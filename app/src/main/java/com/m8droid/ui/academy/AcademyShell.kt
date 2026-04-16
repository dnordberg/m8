package com.m8droid.ui.academy

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.m8droid.academy.AcademyState
import com.m8droid.academy.AcademyViewModel
import com.m8droid.academy.quest.Quest
import com.m8droid.academy.quest.QuestCondition

// Chapter 1 quests (Drums) — used for the MVP playable flow
val chapter1Quests = listOf(
    Quest(
        id = "ch1_q1", chapter = 1, title = "First Sounds",
        briefing = "Let's start simple. Navigate to the Song screen (screen 1) and hit Play to hear what's there.",
        conditions = listOf(QuestCondition.OnScreen(0), QuestCondition.IsPlaying(true)),
        xpReward = 50,
        hintText = "Press Play in the transport — the button at the bottom.",
    ),
    Quest(
        id = "ch1_q2", chapter = 1, title = "Tempo Control",
        briefing = "Every beat needs the right speed. Set the tempo between 90 and 110 BPM and start playback.",
        conditions = listOf(QuestCondition.IsPlaying(true), QuestCondition.BpmInRange(90, 110)),
        xpReward = 75,
        hintText = "Use the tempo controls to dial in 90-110 BPM.",
    ),
    Quest(
        id = "ch1_q3", chapter = 1, title = "Explore the Phrase",
        briefing = "Navigate to the Phrase screen (screen 2) where you can see and edit individual steps.",
        conditions = listOf(QuestCondition.OnScreen(1)),
        xpReward = 50,
        hintText = "Swipe or use the screen selector to go to screen 2.",
    ),
    Quest(
        id = "ch1_q4", chapter = 1, title = "Edit Mode",
        briefing = "Enter edit mode on the Phrase screen — this is where you place notes and shape your patterns.",
        conditions = listOf(QuestCondition.OnScreen(1), QuestCondition.EditModeActive(true)),
        xpReward = 100,
        hintText = "Press EDIT to enter edit mode on the phrase screen.",
    ),
)

val chapter1Intro = listOf(
    DialogueLine("PROF. KICK", "Welcome to M8 Academy! I'm Professor Kick, and I'll be your guide through the world of rhythm.", AcademyTheme.AccentMagenta),
    DialogueLine("PROF. KICK", "The M8 tracker is a powerful sequencer. Everything starts with a PHRASE — 16 steps of notes that play in order.", AcademyTheme.AccentMagenta),
    DialogueLine("PROF. KICK", "Let's start with the basics. I'll give you quests — complete them in the M8 UI and earn XP. Ready?", AcademyTheme.AccentMagenta),
)

val chapter1Complete = listOf(
    DialogueLine("PROF. KICK", "Excellent work! You've got the fundamentals down — navigating screens, controlling tempo, and entering edit mode.", AcademyTheme.AccentMagenta),
    DialogueLine("PROF. KICK", "In the next chapter, we'll dive into SYNTHS and learn how to shape the sounds you trigger.", AcademyTheme.AccentMagenta),
)

enum class AcademyScreen {
    ONBOARDING,
    CHAPTER_MAP,
    NARRATIVE_INTRO,
    QUEST_BRIEFING,
    QUEST_ACTIVE,
    QUEST_COMPLETE,
    NARRATIVE_OUTRO,
}

@Composable
fun AcademyShell(
    viewModel: AcademyViewModel,
    onSwitchToM8: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val activeQuest by viewModel.activeQuest.collectAsState()
    val evaluation by viewModel.lastEvaluation.collectAsState()

    var screen by remember { mutableStateOf(
        if (progress.completedQuestIds.isEmpty()) AcademyScreen.ONBOARDING
        else AcademyScreen.CHAPTER_MAP
    ) }
    var selectedChapter by remember { mutableIntStateOf(0) }
    var currentQuestIndex by remember { mutableIntStateOf(0) }

    // React to quest completion from the engine
    LaunchedEffect(state) {
        if (state == AcademyState.QUEST_COMPLETE) {
            screen = AcademyScreen.QUEST_COMPLETE
        }
    }

    AnimatedContent(targetState = screen, label = "academy_screen") { currentScreen ->
        when (currentScreen) {
            AcademyScreen.ONBOARDING -> {
                OnboardingFlow(onComplete = {
                    screen = AcademyScreen.NARRATIVE_INTRO
                    selectedChapter = 0
                })
            }

            AcademyScreen.CHAPTER_MAP -> {
                ChapterMap(
                    xp = progress.xp,
                    completedQuestIds = progress.completedQuestIds,
                    onSelectChapter = { chapter ->
                        selectedChapter = chapter
                        currentQuestIndex = 0
                        screen = AcademyScreen.NARRATIVE_INTRO
                    },
                    modifier = modifier,
                )
            }

            AcademyScreen.NARRATIVE_INTRO -> {
                NarrativeView(
                    lines = chapter1Intro,
                    onComplete = {
                        currentQuestIndex = findNextQuest(selectedChapter, progress.completedQuestIds)
                        screen = AcademyScreen.QUEST_BRIEFING
                    },
                    modifier = modifier,
                )
            }

            AcademyScreen.QUEST_BRIEFING -> {
                val quests = getChapterQuests(selectedChapter)
                if (currentQuestIndex < quests.size) {
                    QuestBriefing(
                        quest = quests[currentQuestIndex],
                        onAccept = {
                            viewModel.startQuest(quests[currentQuestIndex])
                            screen = AcademyScreen.QUEST_ACTIVE
                            onSwitchToM8()
                        },
                        onBack = { screen = AcademyScreen.CHAPTER_MAP },
                        modifier = modifier,
                    )
                } else {
                    // All quests in chapter done
                    screen = AcademyScreen.NARRATIVE_OUTRO
                }
            }

            AcademyScreen.QUEST_ACTIVE -> {
                // Player is in M8 mode — this screen shouldn't show.
                // If they come back to Academy while quest is active, show status.
                val quest = activeQuest
                if (quest != null) {
                    QuestBriefing(
                        quest = quest,
                        onAccept = { onSwitchToM8() },
                        onBack = {
                            viewModel.returnToIdle()
                            screen = AcademyScreen.CHAPTER_MAP
                        },
                        modifier = modifier,
                    )
                } else {
                    screen = AcademyScreen.CHAPTER_MAP
                }
            }

            AcademyScreen.QUEST_COMPLETE -> {
                val quest = getChapterQuests(selectedChapter).getOrNull(currentQuestIndex)
                if (quest != null) {
                    QuestCompleteOverlay(
                        quest = quest,
                        xpEarned = quest.xpReward,
                        onContinue = {
                            viewModel.returnToIdle()
                            currentQuestIndex = findNextQuest(selectedChapter, progress.completedQuestIds)
                            val quests = getChapterQuests(selectedChapter)
                            screen = if (currentQuestIndex < quests.size) {
                                AcademyScreen.QUEST_BRIEFING
                            } else {
                                AcademyScreen.NARRATIVE_OUTRO
                            }
                        },
                        modifier = modifier,
                    )
                }
            }

            AcademyScreen.NARRATIVE_OUTRO -> {
                NarrativeView(
                    lines = chapter1Complete,
                    onComplete = { screen = AcademyScreen.CHAPTER_MAP },
                    modifier = modifier,
                )
            }
        }
    }
}

private fun getChapterQuests(chapter: Int): List<Quest> {
    return when (chapter) {
        0 -> chapter1Quests
        // Chapters 2-6 will be added in Phase 5
        else -> emptyList()
    }
}

private fun findNextQuest(chapter: Int, completedIds: Set<String>): Int {
    val quests = getChapterQuests(chapter)
    return quests.indexOfFirst { it.id !in completedIds }.takeIf { it >= 0 } ?: quests.size
}
