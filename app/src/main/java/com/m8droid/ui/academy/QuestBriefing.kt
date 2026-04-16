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
import com.m8droid.academy.quest.QuestCondition

@Composable
fun QuestBriefing(
    quest: Quest,
    onAccept: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AcademyTheme.BgRoot)
            .padding(16.dp),
    ) {
        // Back button
        Text(
            text = "← BACK",
            color = AcademyTheme.TextDim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable(onClick = onBack).padding(bottom = 16.dp),
        )

        Text(
            text = quest.title,
            color = AcademyTheme.AccentMagenta,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = quest.briefing,
            color = AcademyTheme.TextNormal,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(20.dp))

        // Conditions preview
        Text(
            text = "OBJECTIVES",
            color = AcademyTheme.AccentCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(8.dp))
        for (condition in quest.conditions) {
            ConditionRow(condition, met = false)
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(16.dp))

        // XP reward
        Text(
            text = "REWARD: ✦ ${quest.xpReward} XP",
            color = AcademyTheme.XpGold,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )

        if (quest.hintText.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "💡 ${quest.hintText}",
                color = AcademyTheme.TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        Spacer(Modifier.weight(1f))

        // Accept button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AcademyTheme.AccentMagenta)
                .clickable(onClick = onAccept)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "ACCEPT QUEST",
                color = AcademyTheme.BgRoot,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
fun ConditionRow(condition: QuestCondition, met: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(AcademyTheme.BgCard)
            .border(1.dp, if (met) AcademyTheme.AccentGreen else AcademyTheme.BorderDim, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (met) "✓" else "○",
            color = if (met) AcademyTheme.AccentGreen else AcademyTheme.TextDim,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = condition.describe(),
            color = if (met) AcademyTheme.AccentGreen else AcademyTheme.TextNormal,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
