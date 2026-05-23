package com.m8droid.ui.academy

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.m8droid.academy.AcademyState
import com.m8droid.academy.AcademyViewModel
import com.m8droid.academy.quest.Quest
import com.m8droid.academy.quest.QuestCondition
import com.m8droid.emulator.M8Emulator

// Chapter 1 quests (Drums) — used for the MVP playable flow
val chapter1Quests = listOf(
    Quest(
        id = "ch1_q1", chapter = 1, title = "First Beat",
        briefing = "Let's start with a beat. Stay on the Song screen and hit Play so you can hear the tracker running.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_SONG), QuestCondition.IsPlaying(true)),
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
        id = "ch1_q3", chapter = 1, title = "Place a Chain",
        briefing = "Start the real tracker loop: on SONG, place chain 00 into the selected track cell.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_SONG), QuestCondition.SongCellFilled),
        xpReward = 75,
        hintText = "Tap a SONG cell, then enter 00 with the hex pad or edit controls.",
    ),
    Quest(
        id = "ch1_q4", chapter = 1, title = "Link a Phrase",
        briefing = "Open CHAIN and put phrase 00 on the selected chain row. Songs play chains; chains play phrases.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_CHAIN), QuestCondition.ChainRowHasPhrase),
        xpReward = 75,
        hintText = "Go to CHAIN, select a phrase slot, then enter 00.",
    ),
    Quest(
        id = "ch1_q5", chapter = 1, title = "Explore the Phrase",
        briefing = "Navigate to the Phrase screen where you can see and edit individual steps.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_PHRASE)),
        xpReward = 50,
        hintText = "Swipe or use the screen selector until PHRASE is visible.",
    ),
    Quest(
        id = "ch1_q6", chapter = 1, title = "Edit Mode",
        briefing = "Enter edit mode on the Phrase screen — this is where you place notes and shape your patterns.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_PHRASE), QuestCondition.EditModeActive(true)),
        xpReward = 100,
        hintText = "Press EDIT to enter edit mode on the phrase screen.",
    ),
    Quest(
        id = "ch1_q7", chapter = 1, title = "Enter a Note",
        briefing = "Put a note into the selected phrase step. Now your chain has something audible to trigger.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_PHRASE), QuestCondition.PhraseStepHasNote),
        xpReward = 100,
        hintText = "On PHRASE, select a note column and enter a note with the note picker or edit controls.",
    ),
    Quest(
        id = "ch1_q8", chapter = 1, title = "Phrase Steps",
        briefing = "Move down into the phrase rows. A phrase is a 16-step pattern: each row is a possible note trigger.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_PHRASE), QuestCondition.CursorYAtLeast(4)),
        xpReward = 75,
        hintText = "On PHRASE, move down to at least step 04.",
    ),
    Quest(
        id = "ch1_q9", chapter = 1, title = "Chain a Pattern",
        briefing = "Open the Chain screen. Chains point to phrases, then the Song screen arranges chains across tracks.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_CHAIN)),
        xpReward = 75,
        hintText = "Go to CHAIN to see phrase references and transpose.",
    ),
    Quest(
        id = "ch1_q10", chapter = 1, title = "Table Motion",
        briefing = "Visit the Table screen. Tables are tiny automation lanes for motion like pitch, volume, and FX changes.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_TABLE)),
        xpReward = 75,
        hintText = "Go to TABLE and look at the automation rows.",
    ),
    Quest(
        id = "ch1_q11", chapter = 1, title = "Back to Song",
        briefing = "Return to the Song screen with playback running. Song arranges chains; chains point to phrases; phrases hold notes.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_SONG), QuestCondition.IsPlaying(true)),
        xpReward = 100,
        hintText = "Return to SONG and press PLAY if needed.",
    ),
)

val chapter1Intro = listOf(
    DialogueLine("PROF. KICK", "Welcome to M8 Academy! I'm Professor Kick, and I'll be your guide through the world of rhythm.", AcademyTheme.AccentMagenta),
    DialogueLine("PROF. KICK", "The M8 tracker is a powerful sequencer. Everything starts with a PHRASE — 16 steps of notes that play in order.", AcademyTheme.AccentMagenta),
    DialogueLine("PROF. KICK", "Let's start with the basics. I'll give you quests — complete them in the M8 UI and earn XP. Ready?", AcademyTheme.AccentMagenta),
)

val chapter1Complete = listOf(
    DialogueLine("PROF. KICK", "Excellent work! You've got the fundamentals down — playback, phrases, chains, and table motion.", AcademyTheme.AccentMagenta),
    DialogueLine("PROF. KICK", "In the next chapter, we'll dive into SYNTHS and learn how to shape the sounds you trigger.", AcademyTheme.AccentMagenta),
)

val chapter2Quests = listOf(
    Quest(
        id = "ch2_q1", chapter = 2, title = "Open the Synth Lab",
        briefing = "Head to the Instrument screen. This is where each track gets its sound.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_INSTRUMENT)),
        xpReward = 50,
        hintText = "Swipe or tap the screen selector until you reach the Instrument page.",
    ),
    Quest(
        id = "ch2_q2", chapter = 2, title = "Inspect a Patch",
        briefing = "Enter edit mode on the Instrument screen so the synth parameters are ready to tweak.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_INSTRUMENT), QuestCondition.EditModeActive(true)),
        xpReward = 75,
        hintText = "Press EDIT while you're on the Instrument screen.",
    ),
    Quest(
        id = "ch2_q3", chapter = 2, title = "Audition the Sound",
        briefing = "Start playback while viewing the Instrument screen so you can hear changes in context.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_INSTRUMENT), QuestCondition.IsPlaying(true)),
        xpReward = 75,
        hintText = "Stay on Instruments and press PLAY.",
    ),
    Quest(
        id = "ch2_q4", chapter = 2, title = "Ready to Shape",
        briefing = "Keep playback running and stay in edit mode on the Instrument screen — now you're ready to shape synths.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_INSTRUMENT), QuestCondition.EditModeActive(true), QuestCondition.IsPlaying(true)),
        xpReward = 100,
        hintText = "Instrument screen + EDIT + PLAY completes the synth basics.",
    ),
)

val chapter2Intro = listOf(
    DialogueLine("PROF. KICK", "Welcome to SYNTHS. Drums gave you timing; now we'll shape the sounds themselves.", AcademyTheme.AccentMagenta),
    DialogueLine("PROF. KICK", "The Instrument screen is your lab: oscillators, envelopes, and playback context all meet there.", AcademyTheme.AccentMagenta),
)

val chapter2Complete = listOf(
    DialogueLine("PROF. KICK", "Nice — the synth lab is open. Next we'll get into sampling and loading your own audio.", AcademyTheme.AccentMagenta),
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
                    lines = getChapterIntro(selectedChapter),
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
                    lines = getChapterComplete(selectedChapter),
                    onComplete = { screen = AcademyScreen.CHAPTER_MAP },
                    modifier = modifier,
                )
            }
        }
    }
}

internal fun getChapterQuests(chapter: Int): List<Quest> {
    return when (chapter) {
        0 -> chapter1Quests
        1 -> chapter2Quests
        // Chapters 3-6 will be added in later Academy slices.
        else -> emptyList()
    }
}

private fun getChapterIntro(chapter: Int): List<DialogueLine> = when (chapter) {
    1 -> chapter2Intro
    else -> chapter1Intro
}

private fun getChapterComplete(chapter: Int): List<DialogueLine> = when (chapter) {
    1 -> chapter2Complete
    else -> chapter1Complete
}

internal fun findNextQuest(chapter: Int, completedIds: Set<String>): Int {
    val quests = getChapterQuests(chapter)
    return quests.indexOfFirst { it.id !in completedIds }.takeIf { it >= 0 } ?: quests.size
}
