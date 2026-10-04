package com.towerduel.game.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towerduel.game.data.GameData
import com.towerduel.game.data.LaneSpace
import com.towerduel.game.data.MapDef
import com.towerduel.game.data.EnemySendType
import com.towerduel.game.data.TroopType
import com.towerduel.game.engine.LanePath
import com.towerduel.game.ui.GameViewModel
import com.towerduel.game.ui.components.ChunkyTextButton
import com.towerduel.game.ui.components.GameIcon
import com.towerduel.game.ui.components.GameIconKind
import com.towerduel.game.ui.components.GamePanel
import com.towerduel.game.ui.components.OutlinedText
import com.towerduel.game.ui.components.ScreenBackground
import com.towerduel.game.ui.components.TowerPortrait
import com.towerduel.game.ui.components.UnitPortrait
import com.towerduel.game.ui.render.TerrainCache
import com.towerduel.game.ui.render.drawBase
import com.towerduel.game.ui.theme.AiColor
import com.towerduel.game.ui.theme.Cream
import com.towerduel.game.ui.theme.Ink
import com.towerduel.game.ui.theme.Leaf
import com.towerduel.game.ui.theme.Lilac
import com.towerduel.game.ui.theme.NightDeep
import com.towerduel.game.ui.theme.Panel
import com.towerduel.game.ui.theme.PanelLight
import com.towerduel.game.ui.theme.PlayerColor
import com.towerduel.game.ui.theme.Sun
import kotlin.math.roundToInt

@Composable
fun DraftScreen(viewModel: GameViewModel, onDeploy: () -> Unit) {
    val picked = viewModel.pickedTroops
    val problem = viewModel.draftProblem

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .systemBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedText("PICK ${GameData.DRAFT_PICKS} TOWERS", fontSize = 30.sp, color = Sun)
            Spacer(Modifier.weight(1f))
            OutlinedText("${picked.size}/${GameData.DRAFT_PICKS}", fontSize = 24.sp, color = if (problem == null) Leaf else Cream)
        }
        Spacer(Modifier.height(8.dp))

        // One scrolling list for everything above the button, so short screens can still reach every tower.
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { MatchCard(viewModel) }
            item { RosterCard(viewModel.roster) }
            items(viewModel.offeredTroops, key = { it.id }) { troop ->
                val isPicked = troop in picked
                TowerCard(
                    troop = troop,
                    picked = isPicked,
                    // The hand is full: the rest fade until the player frees a slot.
                    faded = !isPicked && picked.size >= GameData.DRAFT_PICKS,
                    onClick = { viewModel.toggleDraftPick(troop) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        ChunkyTextButton(
            text = problem?.uppercase() ?: "BATTLE!",
            onClick = onDeploy,
            modifier = Modifier.fillMaxWidth().height(62.dp),
            color = Leaf,
            enabled = problem == null,
            fontSize = if (problem == null) 26.sp else 15.sp
        )
    }
}

/** Where the match is played, under what rule, and against whom. */
@Composable
private fun MatchCard(viewModel: GameViewModel) {
    GamePanel(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MapPreview(viewModel.map, Modifier.width(132.dp))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoLine("MAP", viewModel.map.name, null)
                for (rule in viewModel.rules) InfoLine("RULE", rule.name, rule.description)
                InfoLine(
                    "RIVAL", viewModel.rival.name,
                    "${viewModel.selectedDifficulty.label} ${viewModel.aiPersonality.label}. ${viewModel.aiPersonality.blurb}",
                    AiColor
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, detail: String?, valueColor: Color = Cream) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Sun, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(6.dp))
            Text(value, color = valueColor, style = MaterialTheme.typography.titleMedium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (detail != null) {
            Text(detail, color = Lilac, style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp, lineHeight = 15.sp)
        }
    }
}

/** The lane exactly as the battle will draw it, shrunk to a thumbnail. */
@Composable
private fun MapPreview(map: MapDef, modifier: Modifier) {
    val path = remember(map) { LanePath(map.pathPoints) }
    val shape = RoundedCornerShape(10.dp)
    Spacer(
        modifier = modifier
            .aspectRatio(LaneSpace.WIDTH / LaneSpace.HEIGHT)
            .clip(shape)
            .border(2.5.dp, Ink, shape)
            .drawWithCache {
                val terrain = TerrainCache.get(map, path, size.width.toInt(), size.height.toInt(), this)
                val u = size.width / LaneSpace.WIDTH
                val last = path.pointCount - 1
                onDrawBehind {
                    drawImage(terrain)
                    drawBase(path.xs[last] * u, path.ys[last] * u, u, PlayerColor, 0f, 0f)
                }
            }
    )
}

@Composable
private fun TowerCard(troop: TroopType, picked: Boolean, faded: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    GamePanel(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (faded) 0.5f else 1f)
            .clip(shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        color = if (picked) PanelLight else Panel,
        edge = if (picked) Sun else Ink
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(68.dp).clip(RoundedCornerShape(14.dp)).background(NightDeep.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                TowerPortrait(troop, Modifier.size(62.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedText(troop.name.uppercase(), fontSize = 18.sp, modifier = Modifier.offset(y = 1.dp))
                    Spacer(Modifier.width(8.dp))
                    RoleTag(troop.role, troop.color)
                }
                Spacer(Modifier.height(3.dp))
                Text(troop.description, color = Lilac, style = MaterialTheme.typography.bodyMedium, lineHeight = 16.sp)
                Spacer(Modifier.height(5.dp))
                StatBars(troop)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PickMark(picked)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(GameIconKind.COIN, Modifier.size(15.dp))
                    Spacer(Modifier.width(2.dp))
                    OutlinedText("${troop.cost}", fontSize = 17.sp, color = Sun, modifier = Modifier.offset(y = 1.dp))
                }
            }
        }
    }
}

@Composable
private fun RoleTag(role: String, color: Color) {
    val shape = RoundedCornerShape(50)
    Text(
        role.uppercase(), color = Ink, fontWeight = FontWeight.Bold, fontSize = 9.5.sp, maxLines = 1,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.clip(shape).background(color).padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@Composable
private fun PickMark(picked: Boolean) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier.size(30.dp).clip(shape).background(if (picked) Leaf else NightDeep).border(2.5.dp, Ink, shape),
        contentAlignment = Alignment.Center
    ) {
        if (picked) GameIcon(GameIconKind.CHECK, Modifier.size(17.dp))
    }
}

/** The units both sides can send this match, and that its waves are made of. */
@Composable
private fun RosterCard(roster: List<EnemySendType>) {
    GamePanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text("UNITS THIS MATCH", color = Sun, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (unit in roster) UnitPortrait(unit, Modifier.size(38.dp))
            }
        }
    }
}

/** Three bars that let two towers be compared at a glance: hitting power, reach and fire rate. */
@Composable
private fun StatBars(troop: TroopType) {
    if (!troop.isAttacker) {
        val text = if (troop.incomeBonusPerSecond > 0f) "+${troop.incomeBonusPerSecond.roundToInt()} gold per second"
        else "+${troop.auraDamageBonusPct.roundToInt()}% damage to nearby towers"
        Text(text, color = Sun, style = MaterialTheme.typography.labelSmall)
        return
    }
    // Damage per second, counting what the tower does besides the plain hit: crits, a beam's
    // ramp, splash, chains, cuts, burn, and for the pulse towers the effect that is their point.
    var power = troop.baseDps
    if (troop.critChance > 0f) power *= 1f + troop.critChance * (troop.critMultiplier - 1f)
    if (troop.rampMax > 0f) power *= 1f + troop.rampMax * 0.4f
    if (troop.splashRadius > 0f) power *= 1f + troop.splashRadius / 9f
    if (troop.chainTargets > 0) power *= 1f + 0.5f * troop.chainTargets
    if (troop.pierce > 0) power *= 1f + 0.35f * troop.pierce
    power += troop.dotDamagePerSecond * (if (troop.shot.isPulse) 2.5f else 1.5f)
    power += troop.slowFactor * 40f + troop.knockback * 4f + troop.vulnerabilityPct * 0.6f
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatBar("PWR", (power / 55f).coerceIn(0.06f, 1f), Modifier.weight(1f))
        StatBar("RNG", (troop.range / 44f).coerceIn(0.06f, 1f), Modifier.weight(1f))
        StatBar("SPD", (220f / troop.fireRateMs).coerceIn(0.06f, 1f), Modifier.weight(1f))
    }
}

@Composable
private fun StatBar(label: String, fraction: Float, modifier: Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Lilac, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
        Spacer(Modifier.width(4.dp))
        Canvas(Modifier.weight(1f).height(7.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
            drawRoundRect(Ink, cornerRadius = r)
            drawRoundRect(
                Sun, topLeft = Offset(1.5f, 1.5f),
                size = androidx.compose.ui.geometry.Size((size.width - 3f) * fraction, size.height - 3f), cornerRadius = r
            )
        }
    }
}
