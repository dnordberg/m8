package com.m8droid.ui.academy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m8droid.academy.quest.Quest
import com.m8droid.academy.quest.QuestEvaluation

@Composable
fun QuestOverlay(
    quest: Quest,
    evaluation: QuestEvaluation?,
    failedAttempts: Int,
    maxAttemptsForHint: Int = 3,
    onAbandon: () -> Unit,
    onHint: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AcademyTheme.BgRoot.copy(alpha = 0.92f))
                .border(1.dp, AcademyTheme.AccentMagenta.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "♟ ${quest.title}",
                    color = AcademyTheme.AccentMagenta,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "✕",
                    color = AcademyTheme.TextDim,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable(onClick = onAbandon).padding(4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))

            // Live condition status
            if (evaluation != null) {
                for (result in evaluation.conditionResults) {
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (result.met) "✓" else "○",
                            color = if (result.met) AcademyTheme.AccentGreen else AcademyTheme.AccentYellow,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = result.condition.describe(),
                            color = if (result.met) AcademyTheme.AccentGreen else AcademyTheme.TextNormal,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            // Hint after N failed attempts
            if (failedAttempts >= maxAttemptsForHint && quest.hintText.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "💡 ${quest.hintText}",
                    color = AcademyTheme.AccentYellow,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
fun QuestCompleteOverlay(
    quest: Quest,
    xpEarned: Int,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AcademyTheme.BgRoot.copy(alpha = 0.85f))
            .clickable(onClick = onContinue),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "QUEST COMPLETE!",
                color = AcademyTheme.AccentGreen,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = quest.title,
                color = AcademyTheme.TextBright,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "✦ +${xpEarned} XP",
                color = AcademyTheme.XpGold,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "TAP TO CONTINUE",
                color = AcademyTheme.TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
