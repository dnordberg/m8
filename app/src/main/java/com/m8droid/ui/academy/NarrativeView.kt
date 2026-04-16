package com.m8droid.ui.academy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DialogueLine(
    val speaker: String,
    val text: String,
    val speakerColor: androidx.compose.ui.graphics.Color = AcademyTheme.AccentMagenta,
)

@Composable
fun NarrativeView(
    lines: List<DialogueLine>,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentLine by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AcademyTheme.BgRoot)
            .clickable {
                if (currentLine < lines.lastIndex) {
                    currentLine++
                } else {
                    onComplete()
                }
            }
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            // Speaker name
            if (lines.isNotEmpty() && currentLine in lines.indices) {
                val line = lines[currentLine]
                Text(
                    text = line.speaker,
                    color = line.speakerColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(8.dp))

                // Dialogue box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AcademyTheme.BgCardHi)
                        .padding(16.dp),
                ) {
                    Text(
                        text = line.text,
                        color = AcademyTheme.TextBright,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 22.sp,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${currentLine + 1}/${lines.size}",
                        color = AcademyTheme.TextDim,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = if (currentLine < lines.lastIndex) "TAP TO CONTINUE ▶" else "TAP TO FINISH ▶",
                        color = AcademyTheme.AccentCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
