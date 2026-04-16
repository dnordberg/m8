package com.m8droid.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Android MIDI bridge for the M8 emulator.
 *
 * Handles USB-OTG and Bluetooth MIDI via the platform [MidiManager]. Opens every
 * enumerated device for both directions: input ports (device -> app) are wired
 * through a [MidiReceiver] that hands raw status/data bytes to [onNoteOn],
 * [onNoteOff], and [onControlChange] callbacks; output ports (app -> device)
 * are cached so [sendNoteOn], [sendNoteOff], and [sendCc] can broadcast to all
 * connected gear.
 *
 * Runs callbacks on a dedicated handler thread — the public send* methods are
 * safe to call from the audio thread, which is where sequencer-driven output
 * originates.
 */
class MidiEngine(private val context: Context) {

    companion object {
        private const val TAG = "MidiEngine"
        private const val STATUS_NOTE_OFF = 0x80
        private const val STATUS_NOTE_ON = 0x90
        private const val STATUS_CC = 0xB0
    }

    var onNoteOn: ((channel: Int, note: Int, velocity: Int) -> Unit)? = null
    var onNoteOff: ((channel: Int, note: Int) -> Unit)? = null
    var onControlChange: ((channel: Int, cc: Int, value: Int) -> Unit)? = null
    var onActivity: ((inbound: Boolean) -> Unit)? = null

    private val midiManager: MidiManager? =
        context.getSystemService(Context.MIDI_SERVICE) as? MidiManager

    private val handlerThread = HandlerThread("M8MidiThread").apply { start() }
    private val handler = Handler(handlerThread.looper)

    // Open device handles keyed by MidiDeviceInfo id so we can close them later.
    private val openDevices = ConcurrentHashMap<Int, MidiDevice>()

    // Output ports we can send to, one per device. We send to ALL of them.
    private val outputPorts = ConcurrentHashMap<Int, MidiInputPort>()

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            Log.i(TAG, "MIDI device added: ${device.properties.getString(MidiDeviceInfo.PROPERTY_NAME)}")
            openDevice(device)
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            Log.i(TAG, "MIDI device removed: ${device.id}")
            closeDevice(device.id)
        }
    }

    fun start() {
        val mm = midiManager ?: run {
            Log.w(TAG, "MIDI service unavailable on this device")
            return
        }
        mm.registerDeviceCallback(deviceCallback, handler)
        mm.devices.forEach { openDevice(it) }
    }

    fun stop() {
        midiManager?.unregisterDeviceCallback(deviceCallback)
        for ((id, _) in openDevices.toMap()) closeDevice(id)
        handlerThread.quitSafely()
    }

    private fun openDevice(info: MidiDeviceInfo) {
        val mm = midiManager ?: return
        mm.openDevice(info, { device ->
            if (device == null) {
                Log.w(TAG, "Failed to open MIDI device ${info.id}")
                return@openDevice
            }
            openDevices[info.id] = device

            // Hook up every output port on the device as a source of events for us.
            // (Device "output" = data flowing from the external device to us.)
            for (portIdx in 0 until info.outputPortCount) {
                val port = device.openOutputPort(portIdx) ?: continue
                port.connect(DispatchReceiver())
            }

            // Grab the first input port so we can send events to this device.
            // (Device "input" = data flowing from us to the external device.)
            if (info.inputPortCount > 0) {
                val inputPort = device.openInputPort(0)
                if (inputPort != null) {
                    outputPorts[info.id] = inputPort
                }
            }
        }, handler)
    }

    private fun closeDevice(id: Int) {
        outputPorts.remove(id)?.close()
        openDevices.remove(id)?.close()
    }

    /**
     * Parses the raw stream from a MIDI device into channel-voice messages.
     * Only handles running status for Note On/Off/CC. Sysex is ignored.
     */
    private inner class DispatchReceiver : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            var i = offset
            val end = offset + count
            while (i < end) {
                val b = msg[i].toInt() and 0xFF
                if (b < 0x80) { i++; continue } // skip data-only runs
                val status = b and 0xF0
                val channel = b and 0x0F
                when (status) {
                    STATUS_NOTE_ON -> if (i + 2 < end) {
                        val note = msg[i + 1].toInt() and 0x7F
                        val vel = msg[i + 2].toInt() and 0x7F
                        onActivity?.invoke(true)
                        if (vel == 0) onNoteOff?.invoke(channel, note)
                        else onNoteOn?.invoke(channel, note, vel)
                        i += 3
                    } else i = end
                    STATUS_NOTE_OFF -> if (i + 2 < end) {
                        val note = msg[i + 1].toInt() and 0x7F
                        onActivity?.invoke(true)
                        onNoteOff?.invoke(channel, note)
                        i += 3
                    } else i = end
                    STATUS_CC -> if (i + 2 < end) {
                        val cc = msg[i + 1].toInt() and 0x7F
                        val v = msg[i + 2].toInt() and 0x7F
                        onActivity?.invoke(true)
                        onControlChange?.invoke(channel, cc, v)
                        i += 3
                    } else i = end
                    else -> i++ // unsupported status byte; skip
                }
            }
        }
    }

    fun sendNoteOn(channel: Int, note: Int, velocity: Int) {
        sendShort(STATUS_NOTE_ON or (channel and 0x0F), note and 0x7F, velocity and 0x7F)
    }

    fun sendNoteOff(channel: Int, note: Int) {
        sendShort(STATUS_NOTE_OFF or (channel and 0x0F), note and 0x7F, 0)
    }

    fun sendCc(channel: Int, cc: Int, value: Int) {
        sendShort(STATUS_CC or (channel and 0x0F), cc and 0x7F, value and 0x7F)
    }

    private fun sendShort(status: Int, d1: Int, d2: Int) {
        if (outputPorts.isEmpty()) return
        val buf = byteArrayOf(status.toByte(), d1.toByte(), d2.toByte())
        val now = System.nanoTime()
        for (port in outputPorts.values) {
            try {
                port.send(buf, 0, buf.size, now)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send MIDI: ${e.message}")
            }
        }
        onActivity?.invoke(false)
    }

    val hasConnectedDevices: Boolean
        get() = openDevices.isNotEmpty()
}
