package com.towerduel.game.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.towerduel.game.data.EnemySendType
import com.towerduel.game.data.GameData
import com.towerduel.game.data.GameMode
import com.towerduel.game.data.LaneSpace
import com.towerduel.game.data.MatchEventType
import com.towerduel.game.data.TroopType
import com.towerduel.game.engine.Battlefield
import com.towerduel.game.engine.GameEngine
import com.towerduel.game.engine.MatchOutcome
import com.towerduel.game.engine.TowerInstance
import com.towerduel.game.ui.GameViewModel
import com.towerduel.game.ui.components.ChunkyButton
import com.towerduel.game.ui.components.ChunkyTextButton
import com.towerduel.game.ui.components.GameIcon
import com.towerduel.game.ui.components.GameIconKind
import com.towerduel.game.ui.components.GamePanel
import com.towerduel.game.ui.components.HudPill
import com.towerduel.game.ui.components.OutlinedText
import com.towerduel.game.ui.components.ScreenBackground
import com.towerduel.game.ui.components.TowerPortrait
import com.towerduel.game.ui.components.UnitPortrait
import com.towerduel.game.ui.render.LaneGestures
import com.towerduel.game.ui.render.LaneView
import com.towerduel.game.ui.theme.AiColor
import com.towerduel.game.ui.theme.Cream
import com.towerduel.game.ui.theme.Dim
import com.towerduel.game.ui.theme.Ink
import com.towerduel.game.ui.theme.Leaf
import com.towerduel.game.ui.theme.Lilac
import com.towerduel.game.ui.theme.NightDeep
import com.towerduel.game.ui.theme.Panel
import com.towerduel.game.ui.theme.PanelLight
import com.towerduel.game.ui.theme.PlayerColor
import com.towerduel.game.ui.theme.Sky
import com.towerduel.game.ui.theme.Sun
import com.towerduel.game.ui.theme.Tomato
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private const val WARNING_SHOW_MS = 2600f
private const val ROUND_BANNER_MS = 1900f
private const val EVENT_BANNER_MS = 3000f

/**
 * The engine is plain mutable state, not Compose state. Every composable in this file that reads
 * it starts with `viewModel.observeFrame()`, which re-runs it after each simulation tick. The
 * screen itself does not, so the two lane canvases are not recomposed 60 times a second.
 */
@Composable
fun BattleScreen(
    viewModel: GameViewModel,
    eng: GameEngine,
    onMatchEnd: () -> Unit,
    onQuit: () -> Unit
) {
    // The match runs on the display's frame clock for as long as this screen is showing.
    LaunchedEffect(eng) {
        while (true) withFrameNanos { viewModel.onFrame(it) }
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
            delay(1800)
            viewModel.clearNotice(notice)
        }
    }

    val gestures = remember(viewModel) {
        LaneGestures(
            onPress = viewModel::onLaneTouch,
            onDrag = viewModel::onLaneTouch,
            onRelease = viewModel::onLaneRelease,
            onCancel = viewModel::onLaneCancel
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(ScreenBackground)) {
        // Only the play area is inset; the overlays below dim the whole screen, bars included.
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 8.dp)) {
            TopHud(viewModel, eng)

            LaneSlot(Modifier.weight(1f)) {
                LaneView(eng, eng.aiField, AiColor, viewModel::observeFrame)
                LaneOverlay(
                    viewModel, eng, eng.aiField,
                    if (eng.isTeamMatch) "RIVALS" else viewModel.rival.name.uppercase(), AiColor, isPlayer = false
                )
            }
            Spacer(Modifier.height(6.dp))
            LaneSlot(Modifier.weight(1f)) {
                LaneView(
                    eng, eng.playerField, PlayerColor, viewModel::observeFrame,
                    selectedTowerId = viewModel.selectedTowerId,
                    ghost = viewModel.ghost,
                    gestures = gestures
                )
                LaneOverlay(viewModel, eng, eng.playerField, if (eng.isTeamMatch) "YOUR TEAM" else "YOU", PlayerColor, isPlayer = true)
            }
            Spacer(Modifier.height(6.dp))

            BottomPanel(viewModel, eng)
        }

        MatchOverlays(viewModel, eng, onQuit, onMatchEnd)
    }
}

/** Centres a lane in its share of the screen at the lane's own aspect ratio. */
@Composable
private fun LaneSlot(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.aspectRatio(LaneSpace.WIDTH / LaneSpace.HEIGHT), content = content)
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
private fun TopHud(viewModel: GameViewModel, eng: GameEngine) {
    viewModel.observeFrame()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SquareButton(GameIconKind.PAUSE, "Pause", onClick = viewModel::pause)
        RoundPill(
            round = eng.round,
            totalRounds = eng.totalRounds,
            suddenDeath = eng.suddenDeath,
            // Whole tenths only, so the pill is not recomposed for changes nobody can see.
            nextWaveFraction = ((1f - eng.secondsToNextRound() / eng.roundIntervalSec).coerceIn(0f, 1f) * 40f).roundToInt() / 40f,
            modifierName = eng.modifier.name,
            modifier = Modifier.weight(1f)
        )
        if (!eng.suddenDeath) HudPill(GameIconKind.CLOCK, formatClock(eng.timeRemainingSec()))
        SquareButton(
            GameIconKind.FAST,
            if (viewModel.fastForward) "Normal speed" else "Double speed",
            onClick = viewModel::toggleFastForward,
            color = if (viewModel.fastForward) Sun else PanelLight
        )
    }
}

@Composable
private fun RoundPill(
    round: Int, totalRounds: Int, suddenDeath: Boolean, nextWaveFraction: Float,
    modifierName: String, modifier: Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(NightDeep.copy(alpha = 0.82f))
            .border(2.dp, Ink, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedText(
                when {
                    suddenDeath -> "SUDDEN DEATH"
                    round == 0 -> "GET READY"
                    else -> "ROUND $round/$totalRounds"
                },
                fontSize = 16.sp,
                color = if (suddenDeath) Tomato else Cream,
                modifier = Modifier.offset(y = 1.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                modifierName, color = Sun, style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(3.dp))
        // Fills up as the next wave gets closer.
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(Ink)) {
            Box(
                Modifier.fillMaxWidth(nextWaveFraction).fillMaxHeight().clip(RoundedCornerShape(50))
                    .background(if (suddenDeath) Tomato else Sky)
            )
        }
    }
}

@Composable
private fun SquareButton(
    icon: GameIconKind, description: String, onClick: () -> Unit, color: Color = PanelLight, size: Dp = 46.dp
) {
    ChunkyButton(
        onClick, Modifier.size(size), color = color, corner = 14.dp, depth = 4.dp,
        contentPadding = PaddingValues(0.dp), description = description
    ) {
        GameIcon(icon, Modifier.size(size * 0.46f))
    }
}

private fun formatClock(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

// ---------------------------------------------------------------------------
// What floats over a lane: whose it is, its lives, and warnings
// ---------------------------------------------------------------------------

@Composable
private fun BoxScope.LaneOverlay(
    viewModel: GameViewModel, eng: GameEngine, field: Battlefield,
    label: String, team: Color, isPlayer: Boolean
) {
    viewModel.observeFrame()
    // Units walk in from the left edge; the tag takes whichever left corner they do not use.
    val tagCorner = if (eng.path.ys[0] < LaneSpace.HEIGHT / 2f) Alignment.BottomStart else Alignment.TopStart
    Box(Modifier.matchParentSize().padding(6.dp)) {
        Row(
            modifier = Modifier.align(tagCorner),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TeamTag(label, team)
            HudPill(GameIconKind.HEART, "${field.lives}", fontSize = 15.sp, textColor = if (field.lives <= eng.startingLives / 4) Tomato else Cream)
            // The rival's purse is public: a fat one means a push is coming.
            if (!isPlayer) HudPill(GameIconKind.COIN, "${field.gold.toInt()}", fontSize = 15.sp)
            // In a 2 v 2 so is the other seat's on each lane: the second rival's, and your ally's (in green).
            val partner = field.partner
            if (partner != null) {
                HudPill(GameIconKind.COIN, "${partner.gold.toInt()}", fontSize = 15.sp, textColor = if (isPlayer) Leaf else Cream)
            }
        }

        if (isPlayer) {
            val sinceRound = eng.elapsedMs - eng.roundStartedAtMs
            val warning = field.warning
            val sinceWarning = if (warning != null) eng.elapsedMs - warning.atMs else Float.MAX_VALUE
            val event = eng.event
            val sinceEvent = if (event != null) eng.elapsedMs - event.startedAtMs else Float.MAX_VALUE
            // One announcement at a time, the most urgent first.
            when {
                warning != null && sinceWarning < WARNING_SHOW_MS ->
                    WarningBanner(warning.unit, fade(sinceWarning, WARNING_SHOW_MS), Modifier.align(Alignment.Center))
                event != null && sinceEvent < EVENT_BANNER_MS ->
                    EventBanner(event.type, fade(sinceEvent, EVENT_BANNER_MS), Modifier.align(Alignment.Center))
                eng.round > 0 && sinceRound < ROUND_BANNER_MS ->
                    RoundBanner(
                        eng.round, suddenDeath = eng.round > eng.totalRounds, waveTitle = eng.waveTitle,
                        modifier = Modifier.align(Alignment.Center).alpha(fade(sinceRound, ROUND_BANNER_MS))
                    )
            }
            // While a timed event lasts, a chip in the opposite corner counts it down.
            val secondsLeft = eng.eventSecondsLeft()
            if (event != null && secondsLeft > 0) {
                val corner = if (tagCorner == Alignment.TopStart) Alignment.TopEnd else Alignment.BottomEnd
                EventChip(event.type, secondsLeft, Modifier.align(corner))
            }
        } else {
            // The rival talks from next to its own name tag.
            val line = viewModel.rivalLine
            if (line != null) {
                val gap = Modifier.padding(top = if (tagCorner == Alignment.TopStart) 32.dp else 0.dp, bottom = if (tagCorner == Alignment.TopStart) 0.dp else 32.dp)
                SpeechBubble(line, Modifier.align(tagCorner).then(gap))
            }
        }
    }
}

@Composable
private fun RoundBanner(round: Int, suddenDeath: Boolean, waveTitle: String?, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedText(
            if (suddenDeath) "SUDDEN DEATH!" else "ROUND $round",
            fontSize = 34.sp, color = if (suddenDeath) Tomato else Sun
        )
        // A themed wave says what is coming.
        if (waveTitle != null) OutlinedText(waveTitle, fontSize = 20.sp, modifier = Modifier.offset(y = (-6).dp))
    }
}

@Composable
private fun EventBanner(type: MatchEventType, alpha: Float, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .alpha(alpha)
            .clip(shape)
            .background(Sky)
            .border(2.5.dp, Ink, shape)
            .padding(horizontal = 16.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedText("${type.label.uppercase()}!", fontSize = 22.sp, modifier = Modifier.offset(y = 1.5.dp))
        Text(type.blurb, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EventChip(type: MatchEventType, secondsLeft: Int, modifier: Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier.clip(shape).background(Sky).border(2.dp, Ink, shape).padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        OutlinedText("${type.label.uppercase()}  $secondsLeft", fontSize = 13.sp, modifier = Modifier.offset(y = 1.dp))
    }
}

@Composable
private fun SpeechBubble(line: String, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        line, color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, lineHeight = 15.sp,
        style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth(0.62f).wrapContentWidth(Alignment.Start)
            .clip(shape).background(Cream).border(2.dp, Ink, shape).padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

/** 1 for most of [totalMs], dropping to 0 over the last quarter. Rounded so it changes in visible steps only. */
private fun fade(ageMs: Float, totalMs: Float): Float =
    (((totalMs - ageMs) / (totalMs * 0.25f)).coerceIn(0f, 1f) * 20f).roundToInt() / 20f

@Composable
private fun TeamTag(label: String, team: Color) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier.clip(shape).background(team).border(2.dp, Ink, shape).padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        OutlinedText(label, fontSize = 13.sp, modifier = Modifier.offset(y = 1.dp))
    }
}

@Composable
private fun WarningBanner(unit: EnemySendType, alpha: Float, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .alpha(alpha)
            .clip(shape)
            .background(Tomato)
            .border(2.5.dp, Ink, shape)
            .padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UnitPortrait(unit, Modifier.size(38.dp))
        Spacer(Modifier.width(6.dp))
        OutlinedText("${unit.name.uppercase()} INCOMING!", fontSize = 19.sp, modifier = Modifier.offset(y = 1.5.dp))
    }
}

// ---------------------------------------------------------------------------
// Bottom panel: purse, build or tower controls, sends
// ---------------------------------------------------------------------------

@Composable
private fun BottomPanel(viewModel: GameViewModel, eng: GameEngine) {
    viewModel.observeFrame()
    val field = eng.playerField
    val gold = field.gold
    val selected = viewModel.selectedTowerId?.let { id -> field.towers.find { it.instanceId == id } }
    // The match's roster, four to a row, and what each send adds to income under this match's rules.
    val sendRows = remember(eng) { eng.roster.chunked(4) }
    val incomeLabels = remember(eng) {
        eng.roster.associate {
            it.id to if (it.incomeBonus > 0f) String.format(Locale.US, "+%.2f/s", eng.sendIncome(it)) else "no income"
        }
    }

    GamePanel(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusRow(viewModel, eng, selected)

            Box(modifier = Modifier.fillMaxWidth().height(74.dp)) {
                if (selected != null && selected.owner !== field) {
                    AllyTowerInfo(viewModel, selected)
                } else if (selected != null) {
                    TowerControls(viewModel, eng, selected, gold)
                } else {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (troop in viewModel.pickedTroops) {
                            BuildCard(
                                troop = troop,
                                affordable = gold >= troop.cost,
                                armed = viewModel.armedTroop == troop,
                                onClick = { viewModel.armTroop(troop) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            for (row in sendRows) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (unit in row) {
                        SendChip(
                            unit = unit,
                            affordable = gold >= unit.cost,
                            unlocked = eng.isUnlocked(unit),
                            unlockRound = eng.unlockRoundOf(unit),
                            // Coarse steps: enough for a smooth-looking refill without a recomposition per frame.
                            cooldown = (eng.sendCooldownFraction(field, unit) * 12f).roundToInt() / 12f,
                            incomeLabel = incomeLabels[unit.id] ?: "",
                            onClick = { viewModel.sendUnit(unit) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(viewModel: GameViewModel, eng: GameEngine, selected: TowerInstance?) {
    val field = eng.playerField
    val notice = viewModel.notice
    val armed = viewModel.armedTroop
    Row(modifier = Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        HudPill(GameIconKind.COIN, "${field.gold.toInt()}", fontSize = 19.sp)
        Spacer(Modifier.width(6.dp))
        GameIcon(GameIconKind.INCOME, Modifier.size(17.dp))
        Spacer(Modifier.width(2.dp))
        OutlinedText("+${oneDecimal(eng.incomePerSec(field))}/s", fontSize = 13.sp, color = Leaf, modifier = Modifier.offset(y = 1.dp))
        Spacer(Modifier.width(8.dp))

        val (message, color) = when {
            notice != null -> notice.text to Tomato
            selected != null -> towerStatLine(selected, eng) to Cream
            armed != null -> "Touch your lane to place ${armed.name}" to Sun
            field.towers.isEmpty() -> "Pick a tower, then touch your lane" to Lilac
            else -> "Sends raise your income" to Lilac
        }
        Text(
            message, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp, fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun towerStatLine(tower: TowerInstance, eng: GameEngine): String {
    val type = tower.type
    val range = eng.towerReach(tower).roundToInt()
    val head = if (tower.level > 0) "${type.name} Lv ${tower.level + 1}" else type.name
    val stats = when {
        type.incomeBonusPerSecond > 0f -> "+${oneDecimal(tower.income)} gold/s"
        type.livesPerMinute > 0f -> "+1 life every ${(60f / tower.livesPerMinute).roundToInt()} seconds"
        type.auraReloadBonusPct > 0f -> "+${tower.auraReloadPct.roundToInt()}% fire rate to towers in range"
        type.auraRangeBonusPct > 0f -> "+${tower.auraRangePct.roundToInt()}% reach to towers in range"
        type.auraDamageBonusPct > 0f -> "+${tower.auraPct.roundToInt()}% damage to towers in range"
        // Their hit damage is a rounding error; the effect is the point.
        type.slowFactor > 0f -> "Slows ${(tower.slow * 100f).roundToInt()}% · range $range"
        type.dotDamagePerSecond > 0f -> "Poison ${oneDecimal(tower.dotDps)}/s · range $range"
        else -> {
            val damage = eng.shotDamage(tower).roundToInt()
            val shotsPerSec = oneDecimal(1000f / eng.reloadMs(tower))
            "DMG $damage · range $range · $shotsPerSec/s"
        }
    }
    val kills = if (type.isAttacker) " · ${tower.kills} pops" else ""
    return "$head\n$stats$kills"
}

private fun oneDecimal(value: Float): String {
    val tenths = (value * 10f).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

@Composable
private fun BuildCard(troop: TroopType, affordable: Boolean, armed: Boolean, onClick: () -> Unit, modifier: Modifier) {
    // Four cards share the row: the name gets the full width of its card, the picture and the price the line below.
    val narrow = LocalConfiguration.current.screenWidthDp < 390
    ChunkyButton(
        onClick, modifier.fillMaxHeight(),
        color = if (armed) Sun else PanelLight, corner = 14.dp,
        contentPadding = PaddingValues(horizontal = 3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                troop.name, color = if (armed) Ink else if (affordable) Cream else Dim,
                fontWeight = FontWeight.Bold, fontSize = if (narrow) 9.5.sp else 10.5.sp, lineHeight = 12.sp,
                style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TowerPortrait(troop, Modifier.size(if (narrow) 32.dp else 38.dp).alpha(if (affordable || armed) 1f else 0.4f))
                Spacer(Modifier.width(1.dp))
                GameIcon(GameIconKind.COIN, Modifier.size(12.dp))
                Spacer(Modifier.width(1.dp))
                OutlinedText(
                    "${troop.cost}", fontSize = 14.5.sp, modifier = Modifier.offset(y = 1.dp),
                    color = if (armed) Cream else if (affordable) Sun else Dim
                )
            }
        }
    }
}

/** An ally's tower can be looked at, not run: whoever built it upgrades and sells it. */
@Composable
private fun AllyTowerInfo(viewModel: GameViewModel, tower: TowerInstance) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TowerPortrait(tower.type, Modifier.size(54.dp), level = tower.level)
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedText("YOUR ALLY'S TOWER", fontSize = 16.sp, color = Leaf)
            Text(
                "${viewModel.ally?.name ?: "Your ally"} built it, and upgrades it.", color = Lilac,
                style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp, maxLines = 1
            )
        }
        SquareButton(GameIconKind.CLOSE, "Close tower controls", onClick = viewModel::deselect, size = 38.dp)
    }
}

@Composable
private fun TowerControls(viewModel: GameViewModel, eng: GameEngine, tower: TowerInstance, gold: Float) {
    val tier = tower.nextUpgrade
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TowerPortrait(tower.type, Modifier.size(54.dp), level = tower.level)

        if (eng.usesTargeting(tower.type)) {
            ChunkyButton(
                viewModel::cycleSelectedTargeting, Modifier.width(64.dp).fillMaxHeight(),
                color = Sky, corner = 14.dp, sound = null, contentPadding = PaddingValues(0.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GameIcon(GameIconKind.TARGET, Modifier.size(20.dp))
                    OutlinedText(tower.targeting.label.uppercase(), fontSize = 13.sp)
                }
            }
        }

        if (tier != null) {
            val affordable = gold >= tier.cost
            ChunkyButton(
                viewModel::upgradeSelectedTower, Modifier.weight(1f).fillMaxHeight(),
                color = Leaf, enabled = affordable, corner = 14.dp, sound = null,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GameIcon(GameIconKind.UP, Modifier.size(13.dp), tint = if (affordable) Cream else Dim)
                        Spacer(Modifier.width(3.dp))
                        OutlinedText(tier.name.uppercase(), fontSize = 12.5.sp, color = if (affordable) Cream else Dim, modifier = Modifier.offset(y = 1.dp))
                    }
                    Text(
                        tier.blurb, color = if (affordable) Ink else Dim, fontSize = 10.5.sp, lineHeight = 11.sp,
                        fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GameIcon(GameIconKind.COIN, Modifier.size(13.dp))
                        Spacer(Modifier.width(2.dp))
                        OutlinedText("${tier.cost}", fontSize = 14.sp, color = if (affordable) Sun else Dim, modifier = Modifier.offset(y = 1.dp))
                    }
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                OutlinedText(if (tower.type.maxLevel > 0) "MAX LEVEL" else "NO UPGRADES", fontSize = 17.sp, color = Sun)
            }
        }

        ChunkyButton(
            viewModel::sellSelectedTower, Modifier.width(60.dp).fillMaxHeight(),
            color = Tomato, corner = 14.dp, sound = null, contentPadding = PaddingValues(0.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedText("SELL", fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(GameIconKind.COIN, Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                    OutlinedText("${eng.sellRefund(tower)}", fontSize = 13.sp, color = Sun, modifier = Modifier.offset(y = 1.dp))
                }
            }
        }

        SquareButton(GameIconKind.CLOSE, "Close tower controls", onClick = viewModel::deselect, size = 38.dp)
    }
}

@Composable
private fun SendChip(
    unit: EnemySendType, affordable: Boolean, unlocked: Boolean, unlockRound: Int, cooldown: Float,
    incomeLabel: String, onClick: () -> Unit, modifier: Modifier
) {
    val ready = affordable && cooldown <= 0f
    // The chip has room for about eight letters at full size; "Juggernaut" needs the smallest.
    val nameSize = if (unit.name.length > 9) 8.5.sp else if (unit.name.length > 8) 9.sp else 10.5.sp
    ChunkyButton(
        onClick, modifier.height(52.dp),
        color = if (unlocked) PanelLight else Panel, corner = 12.dp, depth = 4.dp, sound = null,
        contentPadding = PaddingValues(horizontal = 3.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!unlocked) {
                GameIcon(GameIconKind.LOCK, Modifier.padding(horizontal = 6.dp).size(20.dp), tint = Dim)
                Column {
                    Text(unit.name, color = Dim, fontSize = nameSize, lineHeight = 11.sp, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Text("Round $unlockRound", color = Dim, fontSize = 10.sp, lineHeight = 11.sp, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            } else {
                // Refills from pale to full as the cooldown runs out.
                UnitPortrait(unit, Modifier.size(34.dp).alpha(if (!affordable) 0.4f else 1f - 0.65f * cooldown))
                Spacer(Modifier.width(2.dp))
                Column {
                    Text(
                        unit.name, color = if (ready) Cream else Dim, fontSize = nameSize, lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Clip
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GameIcon(GameIconKind.COIN, Modifier.size(11.dp))
                        Spacer(Modifier.width(2.dp))
                        OutlinedText("${unit.cost}", fontSize = 13.5.sp, color = if (affordable) Sun else Dim, modifier = Modifier.offset(y = 1.dp))
                    }
                    Text(
                        incomeLabel, color = if (unit.incomeBonus > 0f) Leaf else Dim,
                        fontSize = 9.5.sp, lineHeight = 10.sp, style = MaterialTheme.typography.labelSmall, maxLines = 1
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Full-screen overlays
// ---------------------------------------------------------------------------

@Composable
private fun MatchOverlays(viewModel: GameViewModel, eng: GameEngine, onQuit: () -> Unit, onMatchEnd: () -> Unit) {
    viewModel.observeFrame()
    val outcome = eng.outcome
    val paused = viewModel.paused

    val currentOnMatchEnd by rememberUpdatedState(onMatchEnd)
    LaunchedEffect(outcome) {
        if (outcome != MatchOutcome.ONGOING) {
            delay(1900)
            currentOnMatchEnd()
        }
    }

    BackHandler(enabled = outcome == MatchOutcome.ONGOING) {
        if (paused) viewModel.resume() else viewModel.pause()
    }

    if (outcome != MatchOutcome.ONGOING) {
        OutcomeBanner(outcome)
    } else if (paused) {
        PauseOverlay(viewModel, eng, onQuit)
    }
}

@Composable
private fun PauseOverlay(viewModel: GameViewModel, eng: GameEngine, onQuit: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.72f))
            // Swallow taps so nothing underneath can be built or sent while paused.
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        GamePanel(modifier = Modifier.fillMaxWidth(0.82f), corner = 24.dp) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedText("PAUSED", fontSize = 34.sp, color = Sun)
                Spacer(Modifier.height(10.dp))
                Text(
                    "${eng.modifier.name}: ${eng.modifier.description}",
                    color = Lilac, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center
                )
                if (viewModel.matchMode == GameMode.CUP) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Quitting forfeits this cup match.",
                        color = Tomato, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(18.dp))
                ChunkyTextButton("RESUME", viewModel::resume, Modifier.fillMaxWidth().height(58.dp), color = Leaf)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChunkyTextButton("QUIT", onQuit, Modifier.weight(1f).height(54.dp), color = Tomato, fontSize = 19.sp)
                    ChunkyButton(
                        viewModel::toggleSound, Modifier.size(width = 64.dp, height = 54.dp),
                        color = PanelLight, sound = null, contentPadding = PaddingValues(0.dp),
                        description = if (viewModel.profile.soundOn) "Turn sound off" else "Turn sound on"
                    ) {
                        GameIcon(
                            if (viewModel.profile.soundOn) GameIconKind.SOUND_ON else GameIconKind.SOUND_OFF,
                            Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutcomeBanner(outcome: MatchOutcome) {
    val (headline, color) = when (outcome) {
        MatchOutcome.PLAYER_WIN -> "VICTORY!" to Sun
        MatchOutcome.AI_WIN -> "DEFEAT" to Tomato
        else -> "DRAW" to Cream
    }
    val scale = remember { Animatable(0.2f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f)) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.62f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        OutlinedText(headline, fontSize = 60.sp, color = color, modifier = Modifier.scale(scale.value))
    }
}
