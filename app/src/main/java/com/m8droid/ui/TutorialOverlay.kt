package com.m8droid.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m8droid.tutorial.M8Tutorial

private val cBg = Color(0xE6000000)         // Semi-transparent black
private val cPanel = Color(0xFF0A1428)       // Dark navy panel
private val cBorder = Color(0xFF1A3060)      // Panel border
private val cTitle = Color(0xFF60B0FF)       // Bright blue title
private val cText = Color(0xFFB0D0F0)        // Light blue text
private val cHint = Color(0xFF00DD00)        // Green button hints
private val cDim = Color(0xFF406080)         // Dim text
private val cSection = Color(0xFFFF8800)     // Orange section label
private val cProgress = Color(0xFF1A5090)    // Progress bar bg
private val cProgressFill = Color(0xFF3090E0) // Progress bar fill
private val cButton = Color(0xFF182848)      // Button background
private val cButtonText = Color(0xFF80B8E8)  // Button text

/**
 * Tutorial overlay that appears at the bottom of the screen.
 * Shows the current tutorial step with instructions and button hints.
 * Minimal design that doesn't block the M8 display.
 */
@Composable
fun TutorialOverlay(
    tutorial: M8Tutorial,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSkip: () -> Unit,
    onPrevious: () -> Unit,
) {
    val step = tutorial.currentStep ?: return

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(cPanel)
                .padding(top = 2.dp),
        ) {
            // Top border accent
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(cBorder),
            )

            // Header row: section + progress + controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Section label
                Text(
                    text = step.section.label,
                    color = cSection,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )

                // Progress
                Text(
                    text = tutorial.progressText,
                    color = cDim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )

                // Control buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TutorialButton("PREV") { onPrevious() }
                    TutorialButton("SKIP") { onSkip() }
                    TutorialButton("PAUSE") { onPause() }
                    TutorialButton("EXIT") { onStop() }
                }
            }

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(horizontal = 12.dp)
                    .background(cProgress),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(tutorial.progress)
                        .fillMaxHeight()
                        .background(cProgressFill),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Title
            Text(
                text = step.title,
                color = cTitle,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Instruction text
            Text(
                text = step.instruction,
                color = cText,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            // Button hint
            if (step.buttonHint != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "> ${step.buttonHint}",
                    color = cHint,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Compact contextual tips overlay shown when tutorial is paused.
 * Shows screen-specific tips and a resume button.
 */
@Composable
fun TutorialPausedBanner(
    currentScreen: Int,
    onResume: () -> Unit,
    onResumeHere: () -> Unit,
    onStop: () -> Unit,
) {
    val tips = M8Tutorial.getScreenTips(currentScreen)
    val screenNames = arrayOf("SONG", "CHAIN", "PHRASE", "INSTR", "TABLE", "MIXER", "FX", "CONFIG")
    val screenName = screenNames.getOrElse(currentScreen) { "???" }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(cPanel)
                .padding(top = 2.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(cBorder),
            )

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TUTORIAL PAUSED",
                    color = cSection,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TutorialButton("RESUME") { onResume() }
                    TutorialButton("THIS SCREEN") { onResumeHere() }
                    TutorialButton("EXIT") { onStop() }
                }
            }

            // Screen tips
            Text(
                text = "$screenName TIPS:",
                color = cTitle,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            for (tip in tips) {
                Text(
                    text = "  $tip",
                    color = cText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Small start button shown when tutorial is not active.
 */
@Composable
fun TutorialStartButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "[?]",
        color = cDim,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .clickable { onClick() }
            .padding(4.dp),
    )
}

@Composable
private fun TutorialButton(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = cButtonText,
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(cButton)
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
