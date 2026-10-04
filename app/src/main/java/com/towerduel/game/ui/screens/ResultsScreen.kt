package com.towerduel.game.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towerduel.game.engine.GameEngine
import com.towerduel.game.engine.MatchOutcome
import com.towerduel.game.ui.GameViewModel
import com.towerduel.game.ui.components.ChunkyTextButton
import com.towerduel.game.ui.components.GamePanel
import com.towerduel.game.ui.components.OutlinedText
import com.towerduel.game.ui.components.ScreenBackground
import com.towerduel.game.ui.components.UnitPortrait
import com.towerduel.game.data.GameData
import com.towerduel.game.ui.theme.AiColor
import com.towerduel.game.ui.theme.Cream
import com.towerduel.game.ui.theme.Leaf
import com.towerduel.game.ui.theme.Lilac
import com.towerduel.game.ui.theme.PanelLight
import com.towerduel.game.ui.theme.PlayerColor
import com.towerduel.game.ui.theme.Sun
import com.towerduel.game.ui.theme.Tomato

@Composable
fun ResultsScreen(
    viewModel: GameViewModel,
    eng: GameEngine,
    onPlayAgain: () -> Unit,
    onMainMenu: () -> Unit
) {
    val outcome = eng.outcome
    val (headline, color) = when (outcome) {
        MatchOutcome.PLAYER_WIN -> "VICTORY!" to Sun
        MatchOutcome.AI_WIN -> "DEFEAT" to Tomato
        else -> "DRAW" to Cream
    }
    val you = eng.playerField
    val rival = eng.aiField
    val record = viewModel.profile.stats

    BackHandler { onMainMenu() }

    Box(modifier = Modifier.fillMaxSize().background(ScreenBackground)) {
        if (outcome == MatchOutcome.PLAYER_WIN) VictoryRays()

        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.8f))

            val pop = remember { Animatable(0.4f) }
            LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 220f)) }
            // The loser of the match is whoever is left standing on the winner's lane.
            val opponent = viewModel.rival
            UnitPortrait(GameData.unit(opponent.unitId), Modifier.size(84.dp).scale(pop.value))
            OutlinedText(headline, fontSize = 62.sp, color = color, modifier = Modifier.scale(pop.value))
            // The rival gets the last word.
            Text(
                "${opponent.name}: “${if (outcome == MatchOutcome.PLAYER_WIN) opponent.lines.lose else opponent.lines.win}”",
                color = Cream, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center
            )
            Text(
                "Round ${eng.round} · ${formatDuration(eng.elapsedMs)} · ${viewModel.selectedDifficulty.label} ${viewModel.aiPersonality.label}",
                color = Lilac, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))
            GamePanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedText("YOU", fontSize = 18.sp, color = PlayerColor, modifier = Modifier.width(74.dp))
                        Spacer(Modifier.weight(1f))
                        OutlinedText("RIVAL", fontSize = 18.sp, color = AiColor, textAlign = TextAlign.End, modifier = Modifier.width(74.dp))
                    }
                    StatRow("Lives left", you.lives, rival.lives)
                    StatRow("Units popped", you.stats.kills, rival.stats.kills)
                    StatRow("Units sent", you.stats.unitsSent, rival.stats.unitsSent)
                    StatRow("Gold earned", you.stats.goldEarned.toInt(), rival.stats.goldEarned.toInt())
                    StatRow("Towers built", you.stats.towersBuilt, rival.stats.towersBuilt)
                    StatRow("Leaks", you.stats.leaks, rival.stats.leaks, lowerIsBetter = true)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    outcome == MatchOutcome.PLAYER_WIN && record.streak > 1 -> "${record.streak} wins in a row! Best streak: ${record.bestStreak}"
                    else -> "Record: ${record.wins} won, ${record.losses} lost"
                },
                color = if (outcome == MatchOutcome.PLAYER_WIN) Sun else Lilac,
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))
            ChunkyTextButton("PLAY AGAIN", onPlayAgain, Modifier.fillMaxWidth().height(68.dp), color = Leaf, fontSize = 28.sp)
            Spacer(Modifier.height(10.dp))
            ChunkyTextButton("MAIN MENU", onMainMenu, Modifier.fillMaxWidth().height(52.dp), color = PanelLight, fontSize = 18.sp)
            Spacer(Modifier.height(14.dp))
        }
    }
}

/** One stat, both sides. The better number is lit; a tie lights neither. */
@Composable
private fun StatRow(label: String, you: Int, rival: Int, lowerIsBetter: Boolean = false) {
    val youBetter = if (lowerIsBetter) you < rival else you > rival
    val rivalBetter = if (lowerIsBetter) rival < you else rival > you
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedText("$you", fontSize = 20.sp, color = if (youBetter) Sun else Cream, modifier = Modifier.width(74.dp))
        Text(
            label, color = Lilac, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        OutlinedText("$rival", fontSize = 20.sp, color = if (rivalBetter) Sun else Cream, textAlign = TextAlign.End, modifier = Modifier.width(74.dp))
    }
}

private fun formatDuration(elapsedMs: Float): String {
    val seconds = (elapsedMs / 1000f).toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** Slowly turning sunburst behind a win. */
@Composable
private fun VictoryRays() {
    val spin = rememberInfiniteTransition(label = "rays")
    val angle by spin.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Restart), label = "raysAngle"
    )
    Canvas(Modifier.fillMaxSize()) {
        val centre = Offset(size.width / 2f, size.height * 0.24f)
        val reach = size.height
        rotate(angle, centre) {
            for (i in 0 until 12) {
                drawArc(
                    Color.White.copy(alpha = 0.055f), startAngle = i * 30f, sweepAngle = 15f, useCenter = true,
                    topLeft = Offset(centre.x - reach, centre.y - reach), size = Size(reach * 2f, reach * 2f)
                )
            }
        }
    }
}
