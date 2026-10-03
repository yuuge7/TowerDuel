package com.towerduel.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.towerduel.game.data.Difficulty
import com.towerduel.game.ui.theme.AccentGold
import com.towerduel.game.ui.theme.AccentTeal
import com.towerduel.game.ui.theme.BgDark
import com.towerduel.game.ui.theme.BgPanel
import com.towerduel.game.ui.theme.BgTop
import com.towerduel.game.ui.theme.PanelEdge
import com.towerduel.game.ui.theme.TextPrimary
import com.towerduel.game.ui.theme.TextSecondary

@Composable
fun MainMenuScreen(initialDifficulty: Difficulty, onStart: (Difficulty) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(initialDifficulty) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgTop, BgDark, BgDark)
                )
            )
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(BgPanel.copy(alpha = 0.88f))
                .border(1.dp, PanelEdge, RoundedCornerShape(28.dp))
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("TOWER", style = MaterialTheme.typography.headlineLarge, color = AccentTeal, fontWeight = FontWeight.Black)
            Text("DUEL", style = MaterialTheme.typography.headlineLarge, color = AccentGold, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text(
                "Defend your lane. Break theirs. No two matches play the same.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.82f)
            )

            Spacer(Modifier.height(30.dp))
            Text("DIFFICULTY", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Difficulty.entries.forEach { diff ->
                    val isSelected = diff == selected
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) AccentTeal else BgDark.copy(alpha = 0.8f))
                            .border(1.dp, if (isSelected) AccentTeal else PanelEdge, RoundedCornerShape(14.dp))
                            .clickable { selected = diff }
                            .padding(horizontal = 18.dp, vertical = 11.dp)
                    ) {
                        Text(
                            diff.label,
                            color = if (isSelected) BgDark else TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(34.dp))
            Button(
                onClick = { onStart(selected) },
                modifier = Modifier.fillMaxWidth(0.72f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = BgDark)
            ) {
                Text("START MATCH", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
