package com.m8droid.emulator

import java.io.File

/** Pure project-health checks that can run after loading/importing a song. */
object ProjectHealth {
    data class MissingSample(
        val instrumentSlot: Int,
        val instrumentName: String,
        val samplePath: String,
    )

    data class Warnings(
        val missingSamples: List<MissingSample> = emptyList(),
    ) {
        val hasWarnings: Boolean get() = missingSamples.isNotEmpty()

        fun userMessage(maxItems: Int = 4): String {
            if (missingSamples.isEmpty()) return ""
            val listed = missingSamples.take(maxItems).joinToString("\n") { missing ->
                "INST ${missing.instrumentSlot}: ${missing.instrumentName} → ${missing.samplePath}"
            }
            val extra = missingSamples.size - maxItems
            val suffix = if (extra > 0) "\n…and $extra more" else ""
            return "Missing samples:\n$listed$suffix\n\nPlayback can continue, but those sampler slots will be silent until the files are copied into m8sd/Samples."
        }
    }

    fun checkSamples(instruments: Array<M8Instrument>, sdRoot: File): Warnings {
        val root = sdRoot.canonicalFile
        val missing = instruments.flatMapIndexed { index, instrument ->
            if (instrument.type != InstrumentType.SAMPLER) return@flatMapIndexed emptyList()
            (listOf(instrument.sampler.samplePath) + instrument.sampler.zones.map { it.path })
                .map { it.trim() }.filter { it.isNotBlank() }.distinct().mapNotNull { samplePath ->
                    val resolved = resolveSample(root, samplePath)
                    if (resolved?.isFile == true) null else MissingSample(index, instrument.name.ifBlank { "---" }, samplePath)
                }
        }
        return Warnings(missing)
    }

    private fun resolveSample(root: File, samplePath: String): File? {
        val normalized = samplePath.replace('\\', '/').removePrefix("/").takeIf { it.isNotBlank() } ?: return null
        val file = File(root, normalized).canonicalFile
        return if (file.path.startsWith(root.path + File.separator) || file == root) file else null
    }
}
