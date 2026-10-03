package com.towerduel.game.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.towerduel.game.data.EnemySendType
import com.towerduel.game.data.GameData
import com.towerduel.game.data.LaneSpace
import com.towerduel.game.data.TroopType
import com.towerduel.game.engine.Battlefield
import com.towerduel.game.engine.GameEngine
import com.towerduel.game.engine.MatchOutcome
import com.towerduel.game.engine.TowerInstance
import com.towerduel.game.ui.GameViewModel
import com.towerduel.game.ui.theme.AccentCyan
import com.towerduel.game.ui.theme.AccentGold
import com.towerduel.game.ui.theme.AccentRed
import com.towerduel.game.ui.theme.AccentTeal
import com.towerduel.game.ui.theme.AiColor
import com.towerduel.game.ui.theme.BgDark
import com.towerduel.game.ui.theme.BgPanel
import com.towerduel.game.ui.theme.BgPanelLight
import com.towerduel.game.ui.theme.BgTop
import com.towerduel.game.ui.theme.PanelEdge
import com.towerduel.game.ui.theme.PathColor
import com.towerduel.game.ui.theme.PlayerColor
import com.towerduel.game.ui.theme.TextPrimary
import com.towerduel.game.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val PoisonColor = Color(0xFF7BC96F)

@Composable
fun BattleScreen(
    viewModel: GameViewModel,
    eng: GameEngine,
    onMatchEnd: () -> Unit,
    onQuit: () -> Unit
) {
    // The engine is plain mutable state, not Compose state: this read is what re-runs the
    // screen (HUD numbers, chip affordability) after every simulation tick.
    viewModel.observeFrame()

    val outcome = eng.outcome
    val paused = viewModel.paused

    val currentOnMatchEnd by rememberUpdatedState(onMatchEnd)
    LaunchedEffect(outcome) {
        if (outcome != MatchOutcome.ONGOING) {
            delay(1400)
            currentOnMatchEnd()
        }
    }

    BackHandler(enabled = outcome == MatchOutcome.ONGOING) {
        if (paused) viewModel.resume() else viewModel.pause()
    }

    // Leaving the app mid-match pauses it; the player resumes by hand when they come back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val notice = viewModel.notice
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(1600)
            viewModel.clearNotice(notice)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgDark)))
    ) {
        // Only the play area is inset; the overlays below dim the whole screen, bars included.
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            TopHud(
                timeRemainingSec = eng.timeRemainingSec(),
                modifierName = eng.modifier.name,
                onPause = { viewModel.pause() }
            )

            LaneHeader(label = "OPPONENT", color = AiColor, lives = eng.aiField.lives, gold = eng.aiField.gold.toInt())
            LaneCanvas(
                field = eng.aiField,
                eng = eng,
                observeFrame = viewModel::observeFrame,
                baseColor = AiColor,
                interactive = false,
                selectedTowerId = null,
                showBuildZones = false,
                onTap = { _, _ -> },
                modifier = Modifier.fillMaxWidth().weight(0.9f)
            )

            LaneHeader(label = "YOU", color = PlayerColor, lives = eng.playerField.lives, gold = eng.playerField.gold.toInt())
            LaneCanvas(
                field = eng.playerField,
                eng = eng,
                observeFrame = viewModel::observeFrame,
                baseColor = PlayerColor,
                interactive = true,
                selectedTowerId = viewModel.selectedTowerId,
                showBuildZones = viewModel.armedTroop != null,
                onTap = { x, y -> viewModel.onPlayerLaneTap(x, y) },
                modifier = Modifier.fillMaxWidth().weight(1.05f)
            )

            ContextBar(viewModel = viewModel, eng = eng)
            ActionBar(viewModel = viewModel, gold = eng.playerField.gold)
        }

        if (outcome != MatchOutcome.ONGOING) {
            OutcomeBanner(outcome)
        } else if (paused) {
            PauseOverlay(onResume = { viewModel.resume() }, onQuit = onQuit)
        }
    }
}

@Composable
private fun TopHud(timeRemainingSec: Int, modifierName: String, onPause: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BgPanel.copy(alpha = 0.9f))
            .border(1.dp, PanelEdge, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "⏱ ${timeRemainingSec / 60}:${(timeRemainingSec % 60).toString().padStart(2, '0')}",
            color = if (timeRemainingSec <= 15) AccentRed else TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            modifierName,
            color = AccentGold,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        SmallActionButton("PAUSE", enabled = true, color = TextSecondary, onClick = onPause)
    }
}

@Composable
private fun LaneHeader(label: String, color: Color, lives: Int, gold: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("♥ $lives", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text("$ $gold", color = AccentGold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LaneCanvas(
    field: Battlefield,
    eng: GameEngine,
    observeFrame: () -> Int,
    baseColor: Color,
    interactive: Boolean,
    selectedTowerId: Long?,
    showBuildZones: Boolean,
    onTap: (Float, Float) -> Unit,
    modifier: Modifier
) {
    val pathPoints = eng.map.pathPoints
    val currentOnTap by rememberUpdatedState(onTap)
    val textPaint = remember {
        Paint().apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }
    val baseModifier = modifier
        .padding(horizontal = 10.dp, vertical = 2.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(BgPanelLight)
        .border(1.dp, PanelEdge, RoundedCornerShape(14.dp))
    val canvasModifier = if (interactive) {
        baseModifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val vx = offset.x / size.width.toFloat() * LaneSpace.WIDTH
                val vy = offset.y / size.height.toFloat() * LaneSpace.HEIGHT
                currentOnTap(vx, vy)
            }
        }
    } else baseModifier

    Canvas(modifier = canvasModifier) {
        // Read in the draw phase so every simulation tick repaints the lane.
        observeFrame()

        // The lane is stretched to fill its slot, so one lane unit differs in px per axis.
        val sx = size.width / LaneSpace.WIDTH
        val sy = size.height / LaneSpace.HEIGHT

        // Path
        val path = Path()
        pathPoints.forEachIndexed { i, (x, y) ->
            val px = x * sx; val py = y * sy
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(
            path, color = PathColor,
            style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // The base this lane's enemies are marching toward
        val (endX, endY) = pathPoints.last()
        drawCircle(baseColor.copy(alpha = 0.25f), radius = 13.dp.toPx(), center = Offset(endX * sx, endY * sy))
        drawCircle(baseColor, radius = 13.dp.toPx(), center = Offset(endX * sx, endY * sy), style = Stroke(width = 2.dp.toPx()))

        // Where a new tower can't go while one is armed
        if (showBuildZones) {
            for (t in field.towers) {
                drawLaneOval(AccentRed.copy(alpha = 0.12f), t.x, t.y, GameData.MIN_TOWER_SPACING, sx, sy)
            }
        }

        // Tracers
        for (tr in field.tracers) {
            val alpha = (1f - tr.ageMs / 160f).coerceIn(0f, 1f)
            drawLine(
                color = AccentGold.copy(alpha = alpha),
                start = Offset(tr.fromX * sx, tr.fromY * sy),
                end = Offset(tr.toX * sx, tr.toY * sy),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Towers
        val towerRadius = 11.dp.toPx()
        for (t in field.towers) {
            val center = Offset(t.x * sx, t.y * sy)
            if (t.instanceId == selectedTowerId) {
                val reach = eng.towerReach(t)
                drawLaneOval(t.type.color.copy(alpha = 0.15f), t.x, t.y, reach, sx, sy)
                drawLaneOval(t.type.color.copy(alpha = 0.5f), t.x, t.y, reach, sx, sy, Stroke(width = 1.dp.toPx()))
            }
            drawCircle(t.type.color, radius = towerRadius, center = center)
            if (t.upgraded) {
                drawCircle(AccentGold, radius = towerRadius, center = center, style = Stroke(width = 2.dp.toPx()))
            }
            drawCenteredText(textPaint, t.type.glyph, center.x, center.y, 14.dp.toPx(), Color.Black.toArgb())
        }

        // Enemies
        for (e in field.incomingEnemies) {
            val center = Offset(e.x * sx, e.y * sy)
            val radius = (6f + sqrt(e.type.maxHp) / 3f).coerceIn(7f, 12f).dp.toPx()
            drawCircle(e.type.color, radius = radius, center = center)
            drawCenteredText(textPaint, e.type.glyph, center.x, center.y, radius * 1.3f, Color.Black.toArgb())

            val statusColor = when {
                eng.elapsedMs < e.stunExpiresAtMs -> Color.White
                eng.elapsedMs < e.slowExpiresAtMs -> AccentCyan
                eng.elapsedMs < e.dotExpiresAtMs -> PoisonColor
                else -> null
            }
            if (statusColor != null) {
                drawCircle(statusColor, radius = radius + 1.5.dp.toPx(), center = center, style = Stroke(width = 1.5.dp.toPx()))
            }

            val hpFrac = (e.hp / e.type.maxHp).coerceIn(0f, 1f)
            val barWidth = radius * 2f + 4.dp.toPx()
            val barTopLeft = Offset(center.x - barWidth / 2f, center.y - radius - 7.dp.toPx())
            drawRect(Color.Black.copy(alpha = 0.4f), topLeft = barTopLeft, size = Size(barWidth, 3.dp.toPx()))
            drawRect(AccentRed, topLeft = barTopLeft, size = Size(barWidth * hpFrac, 3.dp.toPx()))
        }
    }
}

/** A circle of [radius] lane units, drawn as the oval it becomes once the lane is stretched. */
private fun DrawScope.drawLaneOval(
    color: Color, x: Float, y: Float, radius: Float, sx: Float, sy: Float,
    style: DrawStyle = Fill
) {
    drawOval(
        color,
        topLeft = Offset((x - radius) * sx, (y - radius) * sy),
        size = Size(radius * 2f * sx, radius * 2f * sy),
        style = style
    )
}

private fun DrawScope.drawCenteredText(paint: Paint, text: String, x: Float, y: Float, sizePx: Float, colorArgb: Int) {
    paint.color = colorArgb
    paint.textSize = sizePx
    drawContext.canvas.nativeCanvas.drawText(text, x, y + sizePx * 0.35f, paint)
}

/** Fixed-height slot under the lanes, so selecting a tower never resizes the battlefield. */
@Composable
private fun ContextBar(viewModel: GameViewModel, eng: GameEngine) {
    val selected = viewModel.selectedTowerId?.let { id -> eng.playerField.towers.find { it.instanceId == id } }
    val armed = viewModel.armedTroop
    val notice = viewModel.notice

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BgPanel.copy(alpha = 0.9f))
            .border(1.dp, PanelEdge, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            selected != null -> {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (selected.upgraded) "${selected.type.name} ★" else selected.type.name,
                        color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        towerStatLine(selected, eng),
                        color = TextSecondary, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!selected.upgraded) {
                        SmallActionButton(
                            "Upgrade ${selected.type.upgradeCost}g",
                            enabled = eng.playerField.gold >= selected.type.upgradeCost,
                            color = AccentTeal,
                            onClick = { viewModel.upgradeSelectedTower() }
                        )
                    }
                    SmallActionButton(
                        "Sell +${eng.sellRefund(selected).toInt()}g",
                        enabled = true, color = AccentRed,
                        onClick = { viewModel.sellSelectedTower() }
                    )
                    SmallActionButton("✕", enabled = true, color = TextSecondary, onClick = { viewModel.deselect() })
                }
            }
            armed != null -> {
                Text(
                    notice?.text ?: "Tap your lane to place ${armed.name}",
                    color = if (notice != null) AccentRed else armed.color,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                SmallActionButton("Cancel", enabled = true, color = TextSecondary, onClick = { viewModel.deselect() })
            }
            else -> {
                Text(
                    "Pick a tower to build, or send units at your opponent.",
                    color = TextSecondary, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun towerStatLine(tower: TowerInstance, eng: GameEngine): String {
    val type = tower.type
    val range = eng.towerReach(tower).roundToInt()
    return when {
        type.incomeBonusPerSecond > 0f -> "+${oneDecimal(tower.effectiveIncome)} gold/s"
        type.auraDamageBonusPct > 0f -> "+${tower.effectiveAuraBonus.roundToInt()}% damage to towers in range"
        // Their hit damage is a rounding error; the effect is the point.
        type.slowFactor > 0f -> "Slows ${(tower.effectiveSlow * 100f).roundToInt()}% · RNG $range"
        type.dotDamagePerSecond > 0f -> "Poison ${oneDecimal(tower.effectiveDotDps)}/s · RNG $range"
        else -> {
            val damage = (tower.effectiveDamage * eng.modifier.damageMultiplier).roundToInt()
            val shotsPerSec = oneDecimal(1000f / tower.effectiveFireRateMs)
            "DMG $damage · RNG $range · $shotsPerSec/s"
        }
    }
}

private fun oneDecimal(value: Float): String {
    val tenths = (value * 10f).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

@Composable
private fun SmallActionButton(label: String, enabled: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) color.copy(alpha = 0.18f) else BgPanelLight)
            .border(1.dp, color.copy(alpha = if (enabled) 0.85f else 0.2f), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) color else TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionBar(viewModel: GameViewModel, gold: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BgPanel.copy(alpha = 0.92f))
            .border(1.dp, PanelEdge, RoundedCornerShape(18.dp))
            .padding(vertical = 8.dp)
    ) {
        Text("BUILD", color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (troop in viewModel.playerDraft) {
                val armed = viewModel.armedTroop == troop
                BuildChip(
                    troop = troop,
                    affordable = gold >= troop.cost,
                    armed = armed,
                    onClick = { viewModel.armTroop(troop) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text("SEND", color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 10.dp, top = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (send in GameData.ENEMY_SENDS) {
                SendChip(
                    send = send,
                    affordable = gold >= send.cost,
                    onClick = { viewModel.sendUnit(send) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BuildChip(troop: TroopType, affordable: Boolean, armed: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (armed) troop.color.copy(alpha = 0.22f) else BgPanelLight)
            .border(1.dp, if (armed) troop.color else Color.Transparent, RoundedCornerShape(12.dp))
            // An armed chip stays tappable so it can always be un-armed, even after gold drops.
            .clickable(enabled = affordable || armed) { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(troop.glyph, color = if (affordable) troop.color else TextSecondary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        Text(
            troop.name, color = if (affordable) TextPrimary else TextSecondary,
            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text("${troop.cost}g", color = if (affordable) AccentGold else TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SendChip(send: EnemySendType, affordable: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BgPanelLight)
            .border(1.dp, if (affordable) send.color.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(enabled = affordable) { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(send.glyph, color = if (affordable) send.color else TextSecondary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        Text(
            send.name.substringBefore(' '), color = if (affordable) TextPrimary else TextSecondary,
            fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Clip
        )
        Text("${send.cost}g", color = if (affordable) AccentGold else TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit, onQuit: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            // Swallow taps so nothing underneath can be built or sent while paused.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .clip(RoundedCornerShape(24.dp))
                .background(BgPanel)
                .border(1.dp, PanelEdge, RoundedCornerShape(24.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("PAUSED", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onResume,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal, contentColor = BgDark)
            ) {
                Text("RESUME", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onQuit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("QUIT MATCH", color = AccentRed, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OutcomeBanner(outcome: MatchOutcome) {
    val (headline, color) = when (outcome) {
        MatchOutcome.PLAYER_WIN -> "VICTORY" to AccentTeal
        MatchOutcome.AI_WIN -> "DEFEAT" to AccentRed
        else -> "DRAW" to AccentGold
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Text(headline, style = MaterialTheme.typography.headlineLarge, color = color)
    }
}
