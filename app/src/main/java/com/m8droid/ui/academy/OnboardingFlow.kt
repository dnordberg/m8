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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class OnboardingPage(val title: String, val body: String)

private val pages = listOf(
    OnboardingPage("WELCOME TO M8 ACADEMY", "Learn the Dirtywave M8 tracker through guided quests. Complete challenges in the real M8 UI and earn XP as you master each concept."),
    OnboardingPage("HOW IT WORKS", "Each chapter teaches a core M8 skill — drums, synths, sampling, FX, and more. Accept a quest, switch to the M8 screen, and the Academy watches your progress in real time."),
    OnboardingPage("READY?", "Your first quest: build a drum pattern. Tap START to begin Chapter 1."),
)

@Composable
fun OnboardingFlow(onComplete: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AcademyTheme.BgRoot)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = pages[page].title,
                color = AcademyTheme.AccentMagenta,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = pages[page].body,
                color = AcademyTheme.TextNormal,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(40.dp))

            // Page dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in pages.indices) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i == page) AcademyTheme.AccentMagenta else AcademyTheme.BorderDim)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            val isLast = page == pages.lastIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isLast) AcademyTheme.AccentMagenta else AcademyTheme.BgCardHi)
                    .clickable { if (isLast) onComplete() else page++ }
                    .padding(horizontal = 32.dp, vertical = 12.dp),
            ) {
                Text(
                    text = if (isLast) "START" else "NEXT",
                    color = if (isLast) AcademyTheme.BgRoot else AcademyTheme.AccentMagenta,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
