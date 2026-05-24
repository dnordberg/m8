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
    Quest(
        id = "ch2_q5", chapter = 2, title = "Bass Patch",
        briefing = "Build the musical context for a bass patch: return to PHRASE and place a low note into the loop so the synth has a bass job to do.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_PHRASE), QuestCondition.PhraseStepHasNote),
        xpReward = 100,
        hintText = "Go to PHRASE, choose a note row, and enter a note. Low octave notes work best for bass practice.",
    ),
    Quest(
        id = "ch2_q6", chapter = 2, title = "Shape the Filter",
        briefing = "Go back to INSTRUMENT with edit mode on. Filter, cutoff, resonance, and oscillator settings live here — tweak while the phrase plays.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_INSTRUMENT), QuestCondition.EditModeActive(true)),
        xpReward = 100,
        hintText = "Open INSTRUMENT and press EDIT. Use the cursor/hex controls to explore tone-shaping fields.",
    ),
    Quest(
        id = "ch2_q7", chapter = 2, title = "Envelope Feel",
        briefing = "Keep the bass loop playing on INSTRUMENT in edit mode. Now listen for attack/decay/release feel as you adjust the patch.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_INSTRUMENT), QuestCondition.EditModeActive(true), QuestCondition.IsPlaying(true)),
        xpReward = 125,
        hintText = "INSTRUMENT + EDIT + PLAY. Small changes are easiest to hear while the loop runs.",
    ),
)

val chapter3Quests = listOf(
    Quest(
        id = "ch3_q1", chapter = 3, title = "Open the Sample Crate",
        briefing = "Sampling starts in the File hub. Open the PROJECT screen — that's where app-native files, downloads, projects, and samples are managed.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_PROJECT)),
        xpReward = 50,
        hintText = "Use the File/Open control to reach the PROJECT/File hub screen.",
    ),
    Quest(
        id = "ch3_q2", chapter = 3, title = "Load a Sampler Slot",
        briefing = "Visit INSTRUMENT and enter edit mode. Sampler instruments use the same patch slots as synths, but point at audio files on the virtual SD.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_INSTRUMENT), QuestCondition.EditModeActive(true)),
        xpReward = 75,
        hintText = "Go to INSTRUMENT, press EDIT, and inspect the sampler-oriented fields.",
    ),
    Quest(
        id = "ch3_q3", chapter = 3, title = "Trigger a Sample",
        briefing = "Trigger a phrase note so the selected sampler/synth slot fires from the tracker instead of just sitting in the browser.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_PHRASE), QuestCondition.PhraseStepHasNote),
        xpReward = 100,
        hintText = "On PHRASE, enter or keep a note in the selected step. The same tracker trigger path drives sampler instruments.",
    ),
    Quest(
        id = "ch3_q4", chapter = 3, title = "Sampler Loop Check",
        briefing = "Start playback with your phrase note in place. Looping playback is where one-shots, loop points, and pitch changes become obvious.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_PHRASE), QuestCondition.PhraseStepHasNote, QuestCondition.IsPlaying(true)),
        xpReward = 100,
        hintText = "Stay on PHRASE and press PLAY. Listen for the sample or patch repeating in time.",
    ),
    Quest(
        id = "ch3_q5", chapter = 3, title = "Back to the Crate",
        briefing = "Return to PROJECT/File hub after auditioning. Good sample workflow is load, trigger, listen, then manage the source file.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_PROJECT)),
        xpReward = 75,
        hintText = "Open the File hub again so you can swap or manage sample/project files.",
    ),
)

val chapter2Intro = listOf(
    DialogueLine("PROF. KICK", "Welcome to SYNTHS. Drums gave you timing; now we'll shape the sounds themselves.", AcademyTheme.AccentMagenta),
    DialogueLine("PROF. KICK", "The Instrument screen is your lab: oscillators, filters, envelopes, and playback context all meet there.", AcademyTheme.AccentMagenta),
)

val chapter2Complete = listOf(
    DialogueLine("PROF. KICK", "Nice — you built a bass context and practiced shaping it while it played. Next we'll get into sampling and loading your own audio.", AcademyTheme.AccentMagenta),
)

val chapter3Intro = listOf(
    DialogueLine("PROF. KICK", "Welcome to SAMPLING. A tracker gets dangerous when tiny sounds become playable instruments.", AcademyTheme.AccentMagenta),
    DialogueLine("PROF. KICK", "We'll move between the File hub, Instrument page, and Phrase triggers — the real loop for sample-based writing.", AcademyTheme.AccentMagenta),
)

val chapter3Complete = listOf(
    DialogueLine("PROF. KICK", "Good crate-digging. You practiced the sample workflow: find it, load it, trigger it, loop it, and return to manage your files.", AcademyTheme.AccentMagenta),
)

val chapter4Quests = listOf(
    Quest(
        id = "ch4_q1", chapter = 4, title = "Per-Step FX",
        briefing = "Open PHRASE with edit mode on. FX columns are where individual tracker rows bend volume, pan, delay, retrigger, and other behavior.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_PHRASE), QuestCondition.EditModeActive(true)),
        xpReward = 75,
        hintText = "Go to PHRASE and press EDIT. Look to the FX columns beside the note/instrument/volume fields.",
    ),
    Quest(
        id = "ch4_q2", chapter = 4, title = "Table Automation",
        briefing = "Visit TABLE. Tables are tick-level automation lanes: the new runtime path can advance table rows, apply transpose, and lock volume/pan/send motion.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_TABLE)),
        xpReward = 75,
        hintText = "Open TABLE and inspect the rows as tiny automation steps.",
    ),
    Quest(
        id = "ch4_q3", chapter = 4, title = "Slide Into Notes",
        briefing = "Use PSL or PBN on a phrase row. PSL slides into the target note; PBN bends from the current pitch so a static row can move.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_PHRASE), QuestCondition.EditModeActive(true)),
        xpReward = 100,
        hintText = "On a PHRASE FX column, try PSL for slide speed or PBN for pitch bend depth.",
    ),
    Quest(
        id = "ch4_q4", chapter = 4, title = "Retrig Fills",
        briefing = "Add RET to a row for fast repeats. The runtime now honors RET timing and volume ramp so fills can fade down or push forward.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_PHRASE), QuestCondition.EditModeActive(true)),
        xpReward = 100,
        hintText = "RET uses the high nibble for speed and low nibble for volume ramp. Try it on a drum or bass row.",
    ),
    Quest(
        id = "ch4_q5", chapter = 4, title = "Hear the Motion",
        briefing = "Return to PHRASE and start playback. Runtime FX and tables only matter once you can hear the row loop moving.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_PHRASE), QuestCondition.IsPlaying(true)),
        xpReward = 100,
        hintText = "Stay on PHRASE and press PLAY so per-step changes repeat audibly.",
    ),
    Quest(
        id = "ch4_q6", chapter = 4, title = "Back to Arrangement",
        briefing = "Go back to SONG with playback running. FX motion belongs inside phrases, but the Song screen is where you arrange those moving parts.",
        conditions = listOf(QuestCondition.OnScreen(M8Emulator.SCREEN_SONG), QuestCondition.IsPlaying(true)),
        xpReward = 100,
        hintText = "Return to SONG while playback continues.",
    ),
)

val chapter4Intro = listOf(
    DialogueLine("PROF. KICK", "Welcome to FX. This is where tracker rows stop being static notes and start becoming motion.", AcademyTheme.AccentMagenta),
    DialogueLine("PROF. KICK", "We'll connect PHRASE FX columns with TABLE automation so you can hear per-step changes while the loop runs.", AcademyTheme.AccentMagenta),
)

val chapter4Complete = listOf(
    DialogueLine("PROF. KICK", "Now you're thinking like a tracker: notes trigger sounds, FX shape the row, and tables add movement between rows.", AcademyTheme.AccentMagenta),
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
        2 -> chapter3Quests
        3 -> chapter4Quests
        // Chapters 5-6 will be added in later Academy slices.
        else -> emptyList()
    }
}

private fun getChapterIntro(chapter: Int): List<DialogueLine> = when (chapter) {
    1 -> chapter2Intro
    2 -> chapter3Intro
    3 -> chapter4Intro
    else -> chapter1Intro
}

private fun getChapterComplete(chapter: Int): List<DialogueLine> = when (chapter) {
    1 -> chapter2Complete
    2 -> chapter3Complete
    3 -> chapter4Complete
    else -> chapter1Complete
}

internal fun findNextQuest(chapter: Int, completedIds: Set<String>): Int {
    val quests = getChapterQuests(chapter)
    return quests.indexOfFirst { it.id !in completedIds }.takeIf { it >= 0 } ?: quests.size
}
