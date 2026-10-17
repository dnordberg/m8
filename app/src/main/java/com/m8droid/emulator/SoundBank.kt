package com.m8droid.emulator

import java.io.File
import org.json.JSONObject
import com.m8droid.audio.WavDecoder

data class SampleZone(val path: String, val rootMidi: Int)
data class BankSample(val id: String, val name: String, val category: String, val file: String,
    val rootMidi: Int, val loop: Boolean, val source: String, val license: String) {
    val path get() = "/Samples/Factory/v1/$file"
}
data class BankSound(val id: String, val name: String, val category: String)

/** Original app presets plus licensed recordings; not original hardware DSP parity. */
class SoundBank(val samples: List<BankSample>) {
    val sounds: List<BankSound> get() = samples.filter { it.category == "drums" }.map {
        BankSound(it.id, it.name, "Acoustic drums")
    } + (if (samples.any { it.category == "bass" }) listOf(BankSound("acoustic_bass", "Acoustic bass · multisample", "Bass")) else emptyList()) + presets

    fun instrument(id: String): M8Instrument {
        val sample = samples.firstOrNull { it.id == id }
        if (sample != null || id == "acoustic_bass") {
            val zones = if (id == "acoustic_bass") samples.filter { it.category == "bass" }.sortedBy { it.rootMidi }
                .map { SampleZone(it.path, it.rootMidi) } else emptyList()
            require(sample != null || zones.isNotEmpty()) { "Bass samples unavailable" }
            return M8Instrument(sample?.name ?: "ACOUSTIC BASS", InstrumentType.SAMPLER).apply {
                sampler = SamplerParams(samplePath = sample?.path ?: zones.first().path,
                    rootMidi = sample?.rootMidi ?: zones.first().rootMidi, zones = zones,
                    playMode = if (sample?.loop == true) 2 else 0)
                filter.type = FilterType.OFF
                amp.amp = 190
                modulation.env1 = Envelope(attack = 0, decay = 0, sustain = 255, release = 20)
            }
        }
        require(presets.any { it.id == id }) { "Unknown bank sound: $id" }
        return M8Instrument(presets.first { it.id == id }.name).apply {
            amp.amp = 175
            filter = FilterParams(FilterType.LP, 180, 15)
            modulation.env1 = Envelope(attack = 1, decay = 40, sustain = 150, release = 25)
            when (id) {
                "electro_kick", "electro_snare", "electro_hat", "electro_clap" -> {
                    type = InstrumentType.MACROSYNTH
                    macroSynth = MacroSynthParams(model = when(id) { "electro_kick" -> 34; "electro_snare" -> 35; "electro_hat" -> 36; else -> 37 }, timbre = 115, color = 150)
                    filter.type = FilterType.OFF
                    modulation.env1 = Envelope(attack = 0, decay = if (id == "electro_hat") 12 else 42, sustain = 0, release = 8)
                }
                "sub_bass" -> { wavSynth.shape = WavShape.SINE; filter.type = FilterType.OFF }
                "acid_bass" -> {
                    wavSynth.shape = WavShape.SAW; filter = FilterParams(FilterType.LP, 85, 95)
                    modulation.env1.sustain = 55
                    modulation.env2 = Envelope(decay = 45, sustain = 0, dest = ModDestination.CUTOFF, amount = 180)
                }
                "silk_lead" -> { wavSynth.shape = WavShape.PULSE_25; amp.delaySend = 25 }
                "warm_pad" -> {
                    wavSynth.shape = WavShape.TRIANGLE
                    modulation.env1 = Envelope(attack = 65, decay = 60, sustain = 185, release = 150)
                    amp.chorusSend = 65; amp.reverbSend = 40
                }
                "electric_keys" -> {
                    type = InstrumentType.FM_SYNTH
                    fmSynth = FmSynthParams(op1Ratio = 1, op2Ratio = 2, op2Level = 55, op3Level = 0, op4Level = 0)
                    modulation.env1 = Envelope(attack = 1, decay = 100, sustain = 25, release = 60)
                }
                "bright_pluck" -> {
                    wavSynth.shape = WavShape.SAW
                    modulation.env1 = Envelope(attack = 0, decay = 45, sustain = 0, release = 20)
                    modulation.env2 = Envelope(decay = 35, sustain = 0, dest = ModDestination.CUTOFF, amount = 175)
                    amp.delaySend = 20
                }
            }
        }
    }

    fun assign(id: String, slot: Int, instruments: Array<M8Instrument>) {
        require(slot in instruments.indices)
        instruments[slot] = instrument(id)
    }

    companion object {
        val presets = listOf(
            BankSound("electro_kick", "Electronic kick", "Electronic drums"),
            BankSound("electro_snare", "Electronic snare", "Electronic drums"),
            BankSound("electro_hat", "Electronic hat", "Electronic drums"),
            BankSound("electro_clap", "Electronic clap", "Electronic drums"),
            BankSound("sub_bass", "Round sub bass", "Bass"), BankSound("acid_bass", "Acid bass", "Bass"),
            BankSound("silk_lead", "Silk lead", "Lead"), BankSound("warm_pad", "Warm pad", "Pad"),
            BankSound("electric_keys", "Electric keys", "Keys"), BankSound("bright_pluck", "Bright pluck", "Pluck"))

        fun parse(json: String): SoundBank {
            val root = JSONObject(json)
            require(root.getInt("version") == 1) { "Unsupported bank version" }
            val rows = root.getJSONArray("samples")
            require(rows.length() <= 256)
            val samples = (0 until rows.length()).map { i ->
                val s = rows.getJSONObject(i)
                val file = s.getString("file")
                require(file.isNotBlank() && !file.startsWith('/') && !file.contains('\\') && file.split('/').none { it == ".." || it.isBlank() })
                val pitch = s.getInt("rootMidi"); require(pitch in 0..127)
                val category = s.getString("category"); require(category in listOf("drums", "bass"))
                BankSample(s.getString("id"), s.getString("name"), category, file, pitch,
                    s.getBoolean("loop"), s.getString("source"), s.getString("license"))
            }
            require(samples.map { it.id }.distinct().size == samples.size) { "Duplicate bank IDs" }
            return SoundBank(samples)
        }

        /** Content-checked install repairs interrupted copies and preserves stable project paths. */
        fun install(sdRoot: File, readAsset: (String) -> ByteArray): SoundBank {
            val bank = parse(readAsset("soundbank/manifest.json").toString(Charsets.UTF_8))
            for (s in bank.samples) {
                val bytes = readAsset("soundbank/${s.file}")
                WavDecoder.decode(bytes)
                val target = File(sdRoot, s.path.removePrefix("/")).canonicalFile
                require(target.path.startsWith(sdRoot.canonicalPath + File.separator))
                target.parentFile!!.mkdirs()
                if (!target.isFile || !target.readBytes().contentEquals(bytes)) {
                    val temp = File(target.parentFile, target.name + ".install")
                    temp.writeBytes(bytes)
                    java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
            return bank
        }
    }
}
