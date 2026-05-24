package com.m8droid.ui.academy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ChapterInfo(
    val index: Int,
    val title: String,
    val subtitle: String,
    val questCount: Int,
    val completedQuests: Int,
    val icon: String,
)

val chapters = listOf(
    ChapterInfo(0, "DRUMS", "Beats, phrases, chains, and table motion", 11, 0, "🥁"),
    ChapterInfo(1, "SYNTHS", "Bass patches, filters, envelopes, LFOs, and playback", 8, 0, "🎹"),
    ChapterInfo(2, "SAMPLING", "Load, trigger, loop, and manage samples", 5, 0, "🎤"),
    ChapterInfo(3, "FX", "Slides, retrigs, tables, and per-step motion", 6, 0, "⚡"),
    ChapterInfo(4, "SONG STRUCTURE", "Build songs with chains and arrangements", 4, 0, "🏗"),
    ChapterInfo(5, "FINAL JAM", "Put it all together in a full track", 4, 0, "🎯"),
)

@Composable
fun ChapterMap(
    xp: Int,
    completedQuestIds: Set<String>,
    onSelectChapter: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AcademyTheme.BgRoot)
            .padding(12.dp),
    ) {
        // XP bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "♟ M8 ACADEMY",
                color = AcademyTheme.AccentMagenta,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "✦ ${xp} XP",
                color = AcademyTheme.XpGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(chapters) { idx, chapter ->
                val chapterCompleted = countChapterQuests(idx, completedQuestIds)
                ChapterCard(
                    chapter = chapter.copy(completedQuests = chapterCompleted),
                    onClick = { onSelectChapter(idx) },
                )
            }
        }
    }
}

private fun countChapterQuests(chapter: Int, completedIds: Set<String>): Int {
    return completedIds.count { it.startsWith("ch${chapter + 1}_") }
}

@Composable
private fun ChapterCard(chapter: ChapterInfo, onClick: () -> Unit) {
    val progress = if (chapter.questCount > 0) chapter.completedQuests.toFloat() / chapter.questCount else 0f
    val borderColor = when {
        progress >= 1f -> AcademyTheme.AccentGreen
        progress > 0f -> AcademyTheme.AccentCyan
        else -> AcademyTheme.BorderDim
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AcademyTheme.BgCard)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = chapter.icon,
            fontSize = 28.sp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chapter.title,
                color = AcademyTheme.TextBright,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = chapter.subtitle,
                color = AcademyTheme.TextDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${chapter.completedQuests}/${chapter.questCount}",
                color = if (progress >= 1f) AcademyTheme.AccentGreen else AcademyTheme.TextNormal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            if (progress >= 1f) {
                Text(text = "✓", color = AcademyTheme.AccentGreen, fontSize = 14.sp)
            }
        }
    }
}
