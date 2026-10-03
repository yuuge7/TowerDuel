package com.towerduel.game.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.towerduel.game.data.LaneSpace
import com.towerduel.game.data.TroopType
import com.towerduel.game.ui.GameViewModel
import com.towerduel.game.ui.theme.AccentGold
import com.towerduel.game.ui.theme.AccentTeal
import com.towerduel.game.ui.theme.AiColor
import com.towerduel.game.ui.theme.BgDark
import com.towerduel.game.ui.theme.BgPanel
import com.towerduel.game.ui.theme.BgTop
import com.towerduel.game.ui.theme.PanelEdge
import com.towerduel.game.ui.theme.PathColor
import com.towerduel.game.ui.theme.TextPrimary
import com.towerduel.game.ui.theme.TextSecondary

@Composable
fun DraftScreen(viewModel: GameViewModel, onDeploy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgDark)))
            .systemBarsPadding()
            .padding(20.dp)
    ) {
        Text("THIS MATCH", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))

        // One scrolling list for everything above the button, so short screens can still reach the draft.
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                InfoCard(title = "MAP · ${viewModel.map.name}") {
                    MiniPathPreview(pathPoints = viewModel.map.pathPoints)
                }
            }
            item {
                InfoCard(title = "MODIFIER · ${viewModel.modifier.name}") {
                    Text(viewModel.modifier.description, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                InfoCard(title = "OPPONENT · ${viewModel.selectedDifficulty.label}") {
                    Text(
                        "${viewModel.aiPersonality.label} — ${viewModel.aiPersonality.blurb}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            item {
                Text(
                    "YOUR DEFENSE DRAFT",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(viewModel.playerDraft, key = { it.id }) { troop -> TroopDraftCard(troop) }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDeploy,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal, contentColor = BgDark)
        ) {
            Text("DEPLOY", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgPanel.copy(alpha = 0.88f))
            .border(1.dp, PanelEdge, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text(title, color = AccentGold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun TroopDraftCard(troop: TroopType) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgPanel.copy(alpha = 0.88f))
            .border(1.dp, PanelEdge, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            troop.glyph,
            color = troop.color,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(troop.color.copy(alpha = 0.18f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(troop.name, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("${troop.cost}g", color = AccentGold, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(3.dp))
            Text(troop.description, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MiniPathPreview(pathPoints: List<Pair<Float, Float>>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
        val sx = size.width / LaneSpace.WIDTH
        val sy = size.height / LaneSpace.HEIGHT
        val path = Path()
        pathPoints.forEachIndexed { i, (x, y) ->
            val px = x * sx; val py = y * sy
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color = PathColor, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(AccentTeal, radius = 4.dp.toPx(), center = Offset(pathPoints.first().first * sx, pathPoints.first().second * sy))
        drawCircle(AiColor, radius = 4.dp.toPx(), center = Offset(pathPoints.last().first * sx, pathPoints.last().second * sy))
    }
}
