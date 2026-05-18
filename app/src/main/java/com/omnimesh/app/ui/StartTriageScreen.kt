package omnimesh.command1.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import omnimesh.command1.companion.StartCategory
import omnimesh.command1.companion.StartTriageResult

@Composable
fun StartTriageScreen(
    onComplete: (StartTriageResult) -> Unit,
    onSkip: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf(StartTriageResult()) }

    val steps = listOf(
        TriageQuestion(
            question = "Can you walk to the green marker?",
            subtitle = "If you can stand and walk without help, tap YES",
            yesLabel = "YES — I can walk",
            noLabel = "NO — I cannot walk"
        ),
        TriageQuestion(
            question = "Are you breathing normally?",
            subtitle = "Is your breathing fast, difficult, or painful?",
            yesLabel = "YES — breathing OK",
            noLabel = "NO — difficulty breathing"
        ),
        TriageQuestion(
            question = "Can you feel your pulse at your wrist?",
            subtitle = "Place two fingers on the inside of your wrist",
            yesLabel = "YES — I feel a pulse",
            noLabel = "NO — cannot feel pulse"
        ),
        TriageQuestion(
            question = "Do you know where you are?",
            subtitle = "Can you follow simple instructions?",
            yesLabel = "YES — I understand",
            noLabel = "NO — confused or dizzy"
        ),
    )

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) togetherWith
                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300))
        },
        label = "triage_step"
    ) { currentStep ->
        if (currentStep >= steps.size) {
            val category = computeCategory(result)
            StartResultScreen(
                category = category,
                result = result,
                onContinue = { onComplete(result.copy(derivedCategory = category)) }
            )
        } else {
            val q = steps[currentStep]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A0A))
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${currentStep + 1} of ${steps.size}", color = Color(0xFF9AA0A6), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (currentStep + 1f) / steps.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color(0xFF4285F4),
                    trackColor = Color(0xFF2C2C2E)
                )
                Spacer(Modifier.height(40.dp))

                Text(
                    q.question,
                    color = Color(0xFFE8EAED),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(q.subtitle, color = Color(0xFF9AA0A6), fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(48.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Color(0xFF0D652D), RoundedCornerShape(12.dp))
                        .clickable {
                            result = updateResult(result, currentStep, true)
                            step++
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(q.yesLabel, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .border(2.dp, Color(0xFFEA4335), RoundedCornerShape(12.dp))
                        .clickable {
                            result = updateResult(result, currentStep, false)
                            step++
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(q.noLabel, color = Color(0xFFEA4335), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
                Text("Skip this step", color = Color(0xFF5F6368), fontSize = 13.sp, modifier = Modifier.clickable { onSkip() })
            }
        }
    }
}

@Composable
private fun StartResultScreen(
    category: StartCategory,
    result: StartTriageResult,
    onContinue: () -> Unit,
) {
    val color = when (category) {
        StartCategory.GREEN -> Color(0xFF34A853)
        StartCategory.YELLOW -> Color(0xFFFBBC04)
        StartCategory.RED -> Color(0xFFEA4335)
        StartCategory.BLACK -> Color(0xFF9AA0A6)
        StartCategory.UNKNOWN -> Color(0xFF4285F4)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(60.dp))
                .border(3.dp, color, RoundedCornerShape(60.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(category.color, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        Text(category.description, color = Color(0xFFE8EAED), fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("This assessment has been sent to rescue teams", color = Color(0xFF9AA0A6), fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(color, RoundedCornerShape(28.dp))
                .clickable(onClick = onContinue),
            contentAlignment = Alignment.Center
        ) {
            Text("CONTINUE TO COMPANION", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

private data class TriageQuestion(
    val question: String,
    val subtitle: String,
    val yesLabel: String,
    val noLabel: String,
)

private fun updateResult(
    result: StartTriageResult,
    step: Int,
    answer: Boolean
): StartTriageResult = when (step) {
    0 -> result.copy(canWalk = answer)
    1 -> result.copy(respirationsPerMinute = if (answer) 16 else 30)
    2 -> result.copy(radialPulsePresent = answer)
    3 -> result.copy(canFollowCommands = answer)
    else -> result
}

private fun computeCategory(result: StartTriageResult): StartCategory = when {
    result.canWalk == true -> StartCategory.GREEN
    result.respirationsPerMinute == null ||
        (result.respirationsPerMinute > 30 || result.respirationsPerMinute < 10) -> StartCategory.RED
    result.radialPulsePresent == false -> StartCategory.RED
    result.canFollowCommands == false -> StartCategory.RED
    else -> StartCategory.YELLOW
}
