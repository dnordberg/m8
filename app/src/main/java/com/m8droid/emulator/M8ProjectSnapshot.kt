package com.m8droid.emulator

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A local Android project snapshot. V1 is app-native, not Dirtywave .m8s export. */
object M8ProjectSnapshot {
    private const val MAGIC = "M8DROID_PROJECT"
    private const val VERSION = 2

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
            val version = data.readInt()
            require(version in 1..VERSION) { "Unsupported M8Droid project version" }
            val song = readSong(data, version)
            val count = data.readInt().coerceIn(0, M8Instrument.SLOT_COUNT)
            val instruments = M8Instrument.createDefaults()
            repeat(count) { i -> instruments[i] = readInstrument(data) }
            return Restored(song, instruments)
        }
    }

    fun restoreInto(restored: Restored, targetSong: M8Song, targetInstruments: Array<M8Instrument>) {
        copySong(restored.song, targetSong)
        val count = minOf(restored.instruments.size, targetInstruments.size)
        for (i in 0 until count) targetInstruments[i] = restored.instruments[i]
    }

    private fun copySong(source: M8Song, target: M8Song) {
        target.name = source.name
        target.tempo = source.tempo
        target.transpose = source.transpose
        target.activeScale = source.activeScale
        target.quantize = source.quantize
        for (row in source.songGrid.indices) {
            for (track in source.songGrid[row].indices) target.songGrid[row][track] = source.songGrid[row][track]
        }
        for (chain in source.chains.indices) for (row in source.chains[chain].rows.indices) {
            target.chains[chain].rows[row].phrase = source.chains[chain].rows[row].phrase
            target.chains[chain].rows[row].transpose = source.chains[chain].rows[row].transpose
        }
        for (phrase in source.phrases.indices) for (step in source.phrases[phrase].steps.indices) {
            val src = source.phrases[phrase].steps[step]
            val dst = target.phrases[phrase].steps[step]
            dst.note = src.note
            dst.instrument = src.instrument
            dst.volume = src.volume
            dst.fx1Cmd = src.fx1Cmd
            dst.fx1Val = src.fx1Val
            dst.fx2Cmd = src.fx2Cmd
            dst.fx2Val = src.fx2Val
            dst.fx3Cmd = src.fx3Cmd
            dst.fx3Val = src.fx3Val
        }
        for (table in source.tables.indices) for (row in source.tables[table].rows.indices) {
            val src = source.tables[table].rows[row]
            val dst = target.tables[table].rows[row]
            dst.transpose = src.transpose
            dst.volume = src.volume
            dst.fx1Cmd = src.fx1Cmd
            dst.fx1Val = src.fx1Val
            dst.fx2Cmd = src.fx2Cmd
            dst.fx2Val = src.fx2Val
            dst.fx3Cmd = src.fx3Cmd
            dst.fx3Val = src.fx3Val
        }
        for (groove in source.grooves.indices) {
            for (tick in source.grooves[groove].ticks.indices) target.grooves[groove].ticks[tick] = source.grooves[groove].ticks[tick]
        }
        for (i in source.instrumentIndices.indices) target.instrumentIndices[i] = source.instrumentIndices[i]
        copyEffects(source, target)
    }

    private fun copyEffects(source: M8Song, target: M8Song) {
        target.chorus.modDepth = source.chorus.modDepth
        target.chorus.modFreq = source.chorus.modFreq
        target.chorus.width = source.chorus.width
        target.chorus.reverbSend = source.chorus.reverbSend
        target.delay.filterHP = source.delay.filterHP
        target.delay.filterLP = source.delay.filterLP
        target.delay.timeL = source.delay.timeL
        target.delay.timeR = source.delay.timeR
        target.delay.feedback = source.delay.feedback
        target.delay.width = source.delay.width
        target.delay.reverbSend = source.delay.reverbSend
        target.reverb.filterHP = source.reverb.filterHP
        target.reverb.filterLP = source.reverb.filterLP
        target.reverb.size = source.reverb.size
        target.reverb.damping = source.reverb.damping
        target.reverb.modDepth = source.reverb.modDepth
        target.reverb.modFreq = source.reverb.modFreq
        target.reverb.width = source.reverb.width
        for (i in source.mixer.trackVolumes.indices) {
            target.mixer.trackVolumes[i] = source.mixer.trackVolumes[i]
            target.mixer.trackPans[i] = source.mixer.trackPans[i]
            target.mixer.trackChorusSend[i] = source.mixer.trackChorusSend[i]
            target.mixer.trackDelaySend[i] = source.mixer.trackDelaySend[i]
            target.mixer.trackReverbSend[i] = source.mixer.trackReverbSend[i]
        }
        target.mixer.masterVolume = source.mixer.masterVolume
        target.mixer.djFilter = source.mixer.djFilter
        target.mixer.chorusVolume = source.mixer.chorusVolume
        target.mixer.delayVolume = source.mixer.delayVolume
        target.mixer.reverbVolume = source.mixer.reverbVolume
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
        writeEffects(data, song)
    }

    private fun readSong(data: DataInputStream, version: Int): M8Song {
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
        if (version >= 2) readEffects(data, song)
        return song
    }

    private fun writeEffects(data: DataOutputStream, song: M8Song) {
        data.writeInt(song.chorus.modDepth)
        data.writeInt(song.chorus.modFreq)
        data.writeInt(song.chorus.width)
        data.writeInt(song.chorus.reverbSend)
        data.writeInt(song.delay.filterHP)
        data.writeInt(song.delay.filterLP)
        data.writeInt(song.delay.timeL)
        data.writeInt(song.delay.timeR)
        data.writeInt(song.delay.feedback)
        data.writeInt(song.delay.width)
        data.writeInt(song.delay.reverbSend)
        data.writeInt(song.reverb.filterHP)
        data.writeInt(song.reverb.filterLP)
        data.writeInt(song.reverb.size)
        data.writeInt(song.reverb.damping)
        data.writeInt(song.reverb.modDepth)
        data.writeInt(song.reverb.modFreq)
        data.writeInt(song.reverb.width)
        song.mixer.trackVolumes.forEach(data::writeInt)
        song.mixer.trackPans.forEach(data::writeInt)
        song.mixer.trackChorusSend.forEach(data::writeInt)
        song.mixer.trackDelaySend.forEach(data::writeInt)
        song.mixer.trackReverbSend.forEach(data::writeInt)
        data.writeInt(song.mixer.masterVolume)
        data.writeInt(song.mixer.djFilter)
        data.writeInt(song.mixer.chorusVolume)
        data.writeInt(song.mixer.delayVolume)
        data.writeInt(song.mixer.reverbVolume)
    }

    private fun readEffects(data: DataInputStream, song: M8Song) {
        song.chorus.modDepth = data.readInt()
        song.chorus.modFreq = data.readInt()
        song.chorus.width = data.readInt()
        song.chorus.reverbSend = data.readInt()
        song.delay.filterHP = data.readInt()
        song.delay.filterLP = data.readInt()
        song.delay.timeL = data.readInt()
        song.delay.timeR = data.readInt()
        song.delay.feedback = data.readInt()
        song.delay.width = data.readInt()
        song.delay.reverbSend = data.readInt()
        song.reverb.filterHP = data.readInt()
        song.reverb.filterLP = data.readInt()
        song.reverb.size = data.readInt()
        song.reverb.damping = data.readInt()
        song.reverb.modDepth = data.readInt()
        song.reverb.modFreq = data.readInt()
        song.reverb.width = data.readInt()
        for (i in song.mixer.trackVolumes.indices) song.mixer.trackVolumes[i] = data.readInt()
        for (i in song.mixer.trackPans.indices) song.mixer.trackPans[i] = data.readInt()
        for (i in song.mixer.trackChorusSend.indices) song.mixer.trackChorusSend[i] = data.readInt()
        for (i in song.mixer.trackDelaySend.indices) song.mixer.trackDelaySend[i] = data.readInt()
        for (i in song.mixer.trackReverbSend.indices) song.mixer.trackReverbSend[i] = data.readInt()
        song.mixer.masterVolume = data.readInt()
        song.mixer.djFilter = data.readInt()
        song.mixer.chorusVolume = data.readInt()
        song.mixer.delayVolume = data.readInt()
        song.mixer.reverbVolume = data.readInt()
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

object M8ProjectLibrary {
    data class SavedProject(
        val fileName: String,
        val path: String,
        val songName: String,
        val tempo: Int,
        val modifiedAt: Long,
        val sizeBytes: Long,
    )

    fun list(projectDir: File): List<SavedProject> {
        val files = projectDir.listFiles { file -> file.isFile && file.extension.equals("m8droid", ignoreCase = true) }
            ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching {
                val restored = load(file)
                SavedProject(
                    fileName = file.name,
                    path = file.absolutePath,
                    songName = restored.song.name.ifBlank { file.nameWithoutExtension },
                    tempo = restored.song.tempo,
                    modifiedAt = file.lastModified(),
                    sizeBytes = file.length(),
                )
            }.getOrNull()
        }.sortedWith(compareByDescending<M8ProjectLibrary.SavedProject> { it.modifiedAt }.thenBy { it.fileName })
    }

    fun load(file: File): M8ProjectSnapshot.Restored = M8ProjectSnapshot.decode(file.readBytes())

    fun saveProject(projectDir: File, song: M8Song, instruments: Array<M8Instrument>): File {
        projectDir.mkdirs()
        val root = projectDir.canonicalFile
        val safeStem = safeProjectStem(song.name.ifBlank { "NEW SONG" })
        val target = File(root, "$safeStem.m8droid")
        val temp = File(root, ".$safeStem.${System.nanoTime()}.tmp")
        val bytes = M8ProjectSnapshot.encode(song, instruments)
        val expectedSignature = M8ProjectSnapshot.signature(song, instruments)
        try {
            temp.writeBytes(bytes)
            val restored = load(temp)
            require(M8ProjectSnapshot.signature(restored.song, restored.instruments) == expectedSignature) {
                "Project verification failed"
            }
            try {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            return target
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun rename(projectDir: File, file: File, requestedName: String): File {
        val source = requireManagedProjectFile(projectDir, file)
        val target = uniqueProjectFile(projectDir.canonicalFile, requestedName)
        if (!source.renameTo(target)) error("Could not rename ${source.name}")
        return target
    }

    fun duplicate(projectDir: File, file: File, requestedName: String): File {
        val source = requireManagedProjectFile(projectDir, file)
        val target = uniqueProjectFile(projectDir.canonicalFile, requestedName)
        source.copyTo(target, overwrite = false)
        return target
    }

    fun delete(projectDir: File, file: File): Boolean {
        val source = requireManagedProjectFile(projectDir, file)
        return source.delete()
    }

    fun exportableProjectFile(projectDir: File, path: String): File {
        return requireManagedProjectFile(projectDir, File(path))
    }

    fun importProject(projectDir: File, bytes: ByteArray, requestedName: String): File {
        // Validate before writing so bogus shared files are rejected clearly and
        // never appear in the managed Projects list.
        runCatching { M8ProjectSnapshot.decode(bytes) }
            .getOrElse { throw IllegalArgumentException("Not an m8droid project", it) }
        projectDir.mkdirs()
        val target = uniqueProjectFile(projectDir.canonicalFile, requestedName.removeSuffix(".m8droid"))
        target.writeBytes(bytes)
        return target
    }

    private fun requireManagedProjectFile(projectDir: File, file: File): File {
        val root = projectDir.canonicalFile
        val candidate = file.canonicalFile
        require(candidate.isFile) { "Project does not exist" }
        require(candidate.extension.equals("m8droid", ignoreCase = true)) { "Not an m8droid project" }
        require(candidate.parentFile?.canonicalFile == root) { "Project is outside managed project folder" }
        return candidate
    }

    private fun uniqueProjectFile(dir: File, requestedName: String): File {
        val safeStem = safeProjectStem(requestedName)
        val base = File(dir, "$safeStem.m8droid")
        if (!base.exists()) return base
        var i = 2
        while (true) {
            val candidate = File(dir, "${safeStem}_$i.m8droid")
            if (!candidate.exists()) return candidate
            i++
        }
    }

    private fun safeProjectStem(requestedName: String): String {
        return requestedName
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_', '.')
            .ifEmpty { "Untitled_Project" }
            .take(100)
    }
}
