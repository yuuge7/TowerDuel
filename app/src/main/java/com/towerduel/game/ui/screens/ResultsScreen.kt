package com.towerduel.game.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.towerduel.game.engine.GameEngine
import com.towerduel.game.engine.MatchOutcome
import com.towerduel.game.ui.theme.AccentGold
import com.towerduel.game.ui.theme.AccentRed
import com.towerduel.game.ui.theme.AccentTeal
import com.towerduel.game.ui.theme.BgDark
import com.towerduel.game.ui.theme.BgPanel
import com.towerduel.game.ui.theme.BgTop
import com.towerduel.game.ui.theme.PanelEdge
import com.towerduel.game.ui.theme.TextPrimary
import com.towerduel.game.ui.theme.TextSecondary

@Composable
fun ResultsScreen(
    eng: GameEngine,
    onPlayAgain: () -> Unit,
    onMainMenu: () -> Unit
) {
    val (headline, color) = when (eng.outcome) {
        MatchOutcome.PLAYER_WIN -> "VICTORY" to AccentTeal
        MatchOutcome.AI_WIN -> "DEFEAT" to AccentRed
        else -> "DRAW" to AccentGold
    }

    BackHandler { onMainMenu() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgDark)))
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(BgPanel.copy(alpha = 0.9f))
                .border(1.dp, PanelEdge, RoundedCornerShape(28.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(headline, style = MaterialTheme.typography.headlineLarge, color = color, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(BgDark.copy(alpha = 0.8f))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Your lives remaining: ${eng.playerField.lives}", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("Opponent lives remaining: ${eng.aiField.lives}", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onPlayAgain,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal, contentColor = BgDark)
            ) {
                Text("PLAY AGAIN", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onMainMenu,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                border = ButtonDefaults.outlinedButtonBorder
            ) {
                Text("MAIN MENU", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
