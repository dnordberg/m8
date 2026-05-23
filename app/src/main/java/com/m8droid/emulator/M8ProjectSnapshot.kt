package com.m8droid.emulator

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** A local Android project snapshot. V1 is app-native, not Dirtywave .m8s export. */
object M8ProjectSnapshot {
    private const val MAGIC = "M8DROID_PROJECT"
    private const val VERSION = 1

    data class Restored(
        val song: M8Song,
        val instruments: Array<M8Instrument>,
    )

    fun signature(song: M8Song, instruments: Array<M8Instrument>): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(encode(song, instruments))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun encode(song: M8Song, instruments: Array<M8Instrument>): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.writeUTF(MAGIC)
            data.writeInt(VERSION)
            writeSong(data, song)
            data.writeInt(instruments.size)
            instruments.forEach { writeInstrument(data, it) }
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): Restored {
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            require(data.readUTF() == MAGIC) { "Not an M8Droid project" }
            require(data.readInt() == VERSION) { "Unsupported M8Droid project version" }
            val song = readSong(data)
            val count = data.readInt().coerceIn(0, M8Instrument.SLOT_COUNT)
            val instruments = M8Instrument.createDefaults()
            repeat(count) { i -> instruments[i] = readInstrument(data) }
            return Restored(song, instruments)
        }
    }

    private fun writeSong(data: DataOutputStream, song: M8Song) {
        data.writeUTF(song.name)
        data.writeInt(song.tempo)
        data.writeInt(song.transpose)
        data.writeInt(song.activeScale)
        data.writeInt(song.quantize)
        for (row in song.songGrid) row.forEach(data::writeInt)
        for (chain in song.chains) for (row in chain.rows) {
            data.writeInt(row.phrase)
            data.writeInt(row.transpose)
        }
        for (phrase in song.phrases) for (step in phrase.steps) {
            data.writeInt(step.note)
            data.writeInt(step.instrument)
            data.writeInt(step.volume)
            data.writeInt(step.fx1Cmd)
            data.writeInt(step.fx1Val)
            data.writeInt(step.fx2Cmd)
            data.writeInt(step.fx2Val)
            data.writeInt(step.fx3Cmd)
            data.writeInt(step.fx3Val)
        }
        for (table in song.tables) for (row in table.rows) {
            data.writeInt(row.transpose)
            data.writeInt(row.volume)
            data.writeInt(row.fx1Cmd)
            data.writeInt(row.fx1Val)
            data.writeInt(row.fx2Cmd)
            data.writeInt(row.fx2Val)
            data.writeInt(row.fx3Cmd)
            data.writeInt(row.fx3Val)
        }
        for (groove in song.grooves) groove.ticks.forEach(data::writeInt)
        song.instrumentIndices.forEach(data::writeInt)
    }

    private fun readSong(data: DataInputStream): M8Song {
        val song = M8Song()
        song.name = data.readUTF()
        song.tempo = data.readInt()
        song.transpose = data.readInt()
        song.activeScale = data.readInt()
        song.quantize = data.readInt()
        for (row in song.songGrid) for (i in row.indices) row[i] = data.readInt()
        for (chain in song.chains) for (row in chain.rows) {
            row.phrase = data.readInt()
            row.transpose = data.readInt()
        }
        for (phrase in song.phrases) for (step in phrase.steps) {
            step.note = data.readInt()
            step.instrument = data.readInt()
            step.volume = data.readInt()
            step.fx1Cmd = data.readInt()
            step.fx1Val = data.readInt()
            step.fx2Cmd = data.readInt()
            step.fx2Val = data.readInt()
            step.fx3Cmd = data.readInt()
            step.fx3Val = data.readInt()
        }
        for (table in song.tables) for (row in table.rows) {
            row.transpose = data.readInt()
            row.volume = data.readInt()
            row.fx1Cmd = data.readInt()
            row.fx1Val = data.readInt()
            row.fx2Cmd = data.readInt()
            row.fx2Val = data.readInt()
            row.fx3Cmd = data.readInt()
            row.fx3Val = data.readInt()
        }
        for (groove in song.grooves) for (i in groove.ticks.indices) groove.ticks[i] = data.readInt()
        for (i in song.instrumentIndices.indices) song.instrumentIndices[i] = data.readInt()
        return song
    }

    private fun writeInstrument(data: DataOutputStream, inst: M8Instrument) {
        data.writeUTF(inst.name)
        data.writeInt(inst.type.ordinal)
        data.writeBoolean(inst.transpose)
        data.writeInt(inst.table)
        data.writeInt(inst.sampler.playMode)
        data.writeInt(inst.sampler.sliceMode)
        data.writeInt(inst.sampler.start)
        data.writeInt(inst.sampler.loopStart)
        data.writeInt(inst.sampler.length)
        data.writeInt(inst.sampler.degrade)
        data.writeInt(inst.sampler.detune)
        data.writeUTF(inst.sampler.samplePath)
        data.writeInt(inst.amp.amp)
        data.writeInt(inst.amp.pan)
        data.writeInt(inst.amp.dry)
        data.writeInt(inst.amp.chorusSend)
        data.writeInt(inst.amp.delaySend)
        data.writeInt(inst.amp.reverbSend)
    }

    private fun readInstrument(data: DataInputStream): M8Instrument {
        val name = data.readUTF()
        val type = InstrumentType.entries.getOrElse(data.readInt()) { InstrumentType.WAVSYNTH }
        val inst = M8Instrument(name, type)
        inst.transpose = data.readBoolean()
        inst.table = data.readInt()
        inst.sampler.playMode = data.readInt()
        inst.sampler.sliceMode = data.readInt()
        inst.sampler.start = data.readInt()
        inst.sampler.loopStart = data.readInt()
        inst.sampler.length = data.readInt()
        inst.sampler.degrade = data.readInt()
        inst.sampler.detune = data.readInt()
        inst.sampler.samplePath = data.readUTF()
        inst.amp.amp = data.readInt()
        inst.amp.pan = data.readInt()
        inst.amp.dry = data.readInt()
        inst.amp.chorusSend = data.readInt()
        inst.amp.delaySend = data.readInt()
        inst.amp.reverbSend = data.readInt()
        return inst
    }
}

class SongDirtyGuard(initialCleanSignature: String) {
    private var cleanSignature: String = initialCleanSignature

    fun isDirty(currentSignature: String): Boolean = currentSignature != cleanSignature

    fun shouldConfirmBeforeReplace(currentSignature: String): Boolean = isDirty(currentSignature)

    fun markClean(currentSignature: String) {
        cleanSignature = currentSignature
    }
}
