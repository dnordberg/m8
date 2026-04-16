package com.m8droid.ui.academy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val glossary = mapOf(
    "phrase" to "A 16-step pattern of notes. The basic building block of M8 music.",
    "chain" to "A sequence of up to 16 phrases played in order on one track.",
    "song row" to "One horizontal row of the song grid, containing one chain reference per track.",
    "instrument" to "A voice preset defining oscillator type, filter, envelope, and FX settings.",
    "step" to "A single row within a phrase — can contain a note, instrument, volume, and FX.",
    "FX command" to "A two-character hex code that modifies playback (e.g., retrigger, arpeggio, delay send).",
    "swing" to "Timing offset applied to alternating steps to create a groove feel.",
    "BPM" to "Beats per minute — controls the speed/tempo of playback.",
    "track" to "One of 8 vertical columns. Each track plays one voice at a time.",
    "table" to "An automation sequence that modifies instrument parameters over time.",
    "groove" to "A per-step timing pattern that overrides the default even spacing.",
    "note off" to "A special command that stops a playing note and triggers its release envelope.",
    "cutoff" to "Filter frequency — controls how bright or muffled a sound is.",
    "resonance" to "Filter emphasis at the cutoff point — adds a peak/whistle at high values.",
    "ADSR" to "Attack-Decay-Sustain-Release — the four stages of a volume envelope.",
    "sample" to "A recorded audio file that can be played back as an instrument.",
)

@Composable
fun GlossaryTooltip(term: String, onDismiss: () -> Unit) {
    val definition = glossary[term.lowercase()] ?: "No definition available."

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AcademyTheme.BgCardHi)
            .border(1.dp, AcademyTheme.AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onDismiss)
            .padding(12.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = AcademyTheme.AccentCyan, fontWeight = FontWeight.Bold)) {
                    append(term.uppercase())
                }
                append("  ")
                withStyle(SpanStyle(color = AcademyTheme.TextNormal)) {
                    append(definition)
                }
            },
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp,
        )
    }
}

@Composable
fun CommandReferencePanel(chapter: Int, modifier: Modifier = Modifier) {
    val commands = when (chapter) {
        0 -> listOf("VOL" to "Set step volume", "RET" to "Retrigger note", "HOP" to "Jump to step")
        1 -> listOf("ARP" to "Arpeggio", "PIT" to "Pitch slide", "FIN" to "Fine pitch")
        2 -> listOf("STA" to "Sample start", "LOP" to "Loop point", "SLI" to "Slice")
        3 -> listOf("DEL" to "Delay send", "REV" to "Reverb send", "CHO" to "Chorus send")
        4 -> listOf("GRV" to "Groove", "TSP" to "Transpose", "TBL" to "Table")
        else -> listOf("ALL" to "All commands available")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AcademyTheme.BgCard)
            .border(1.dp, AcademyTheme.BorderDim, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Text(
            text = "COMMANDS",
            color = AcademyTheme.AccentCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(4.dp))
        for ((cmd, desc) in commands) {
            Row(modifier = Modifier.padding(vertical = 1.dp)) {
                Text(
                    text = cmd,
                    color = AcademyTheme.AccentYellow,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(36.dp),
                )
                Text(
                    text = desc,
                    color = AcademyTheme.TextDim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
