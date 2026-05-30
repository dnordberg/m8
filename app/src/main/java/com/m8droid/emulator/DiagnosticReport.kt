package com.m8droid.emulator

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Renders a compact text bug report users can share without exposing private files. */
object DiagnosticReport {
    fun render(
        song: M8Song,
        instruments: Array<M8Instrument>,
        isDirty: Boolean,
        status: String?,
        warnings: ProjectHealth.Warnings,
        recent: List<String>,
        generatedAt: Long = System.currentTimeMillis(),
    ): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(generatedAt))
        val usedInstruments = instruments
            .mapIndexedNotNull { index, inst ->
                if (inst.name == "---" && inst.type == InstrumentType.WAVSYNTH && inst.sampler.samplePath.isBlank()) null
                else "$index: ${inst.name} / ${inst.type.label}${if (inst.sampler.samplePath.isNotBlank()) " / ${inst.sampler.samplePath}" else ""}"
            }
            .take(24)
        return buildString {
            appendLine("M8Droid Diagnostics")
            appendLine("Generated: $date")
            appendLine()
            appendLine("Project")
            appendLine("- Song: ${song.name.ifBlank { "Untitled" }}")
            appendLine("- Tempo: ${song.tempo}")
            appendLine("- Dirty: $isDirty")
            appendLine("- Status: ${status ?: "none"}")
            appendLine()
            appendLine("Warnings")
            if (warnings.hasWarnings) {
                appendLine(warnings.userMessage(maxItems = 16))
            } else {
                appendLine("- none")
            }
            appendLine()
            appendLine("Instruments")
            if (usedInstruments.isEmpty()) appendLine("- defaults only") else usedInstruments.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Recent")
            if (recent.isEmpty()) appendLine("- none") else recent.take(10).forEach { appendLine("- $it") }
        }
    }
}
