package com.towerduel.game.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.GameData
import com.towerduel.game.ui.DifficultyRecord
import com.towerduel.game.ui.LifetimeStats
import com.towerduel.game.ui.components.ChunkyTextButton
import com.towerduel.game.ui.components.GamePanel
import com.towerduel.game.ui.components.OutlinedText
import com.towerduel.game.ui.components.TowerPortrait
import com.towerduel.game.ui.components.UnitPortrait
import com.towerduel.game.ui.theme.Cream
import com.towerduel.game.ui.theme.Dim
import com.towerduel.game.ui.theme.Ink
import com.towerduel.game.ui.theme.Leaf
import com.towerduel.game.ui.theme.Lilac
import com.towerduel.game.ui.theme.NightDeep
import com.towerduel.game.ui.theme.Sky
import com.towerduel.game.ui.theme.Sun
import com.towerduel.game.ui.theme.darken
import java.util.Locale
import kotlin.math.roundToInt

private const val TOP_TOWERS = 5

/** The player's lifetime numbers. [onPlay] is where the empty state sends a player with no matches yet. */
@Composable
fun StatsTab(stats: LifetimeStats, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedText("YOUR STATS", fontSize = 32.sp, color = Sun, modifier = Modifier.padding(top = 12.dp))
        if (stats.matches == 0) {
            NoMatchesYet(onPlay)
        } else {
            RecordPanel(stats)
            DifficultyPanel(stats)
            TotalsPanel(stats)
            BestsPanel(stats)
            if (stats.towerPicks.isNotEmpty()) TowerPicksPanel(stats)
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun NoMatchesYet(onPlay: () -> Unit) {
    GamePanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            UnitPortrait(GameData.unit("runner"), Modifier.size(72.dp))
            OutlinedText("NO MATCHES YET", fontSize = 24.sp)
            Text(
                "Finish a match and your record, totals and favourite towers show up here.",
                color = Lilac, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center
            )
            ChunkyTextButton("PLAY A MATCH", onPlay, Modifier.fillMaxWidth().height(58.dp), color = Leaf)
        }
    }
}

/** The one headline number, and the record it comes from. */
@Composable
private fun RecordPanel(stats: LifetimeStats) {
    val winRate = stats.winRate
    GamePanel(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(128.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedText(if (winRate == null) "–" else "${(winRate * 100f).roundToInt()}%", fontSize = 58.sp)
                Text("Win rate", color = Lilac, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    buildString {
                        append("${stats.wins} won · ${stats.losses} lost")
                        if (stats.draws > 0) append(" · ${stats.draws} drawn")
                    },
                    color = Cream, style = MaterialTheme.typography.titleMedium
                )
                Meter(winRate ?: 0f, Modifier.fillMaxWidth())
                Text(
                    "Streak ${stats.streak} · best ${stats.bestStreak}",
                    color = Lilac, style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun DifficultyPanel(stats: LifetimeStats) {
    Section("By difficulty") {
        for (difficulty in Difficulty.entries) {
            val record = stats.byDifficulty[difficulty] ?: DifficultyRecord()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(difficulty.label, color = Cream, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(74.dp))
                if (record.matches == 0) {
                    Text("Not played yet", color = Dim, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Meter(record.wins / record.matches.toFloat(), Modifier.weight(1f))
                    Text(
                        "${record.wins} won · ${record.losses} lost",
                        color = Lilac, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End,
                        modifier = Modifier.width(112.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalsPanel(stats: LifetimeStats) {
    Section("Totals") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Time played", duration(stats.secondsPlayed), Modifier.weight(1f))
            StatTile("Units popped", compact(stats.pops.toLong()), Modifier.weight(1f))
            StatTile("Units sent", compact(stats.unitsSent.toLong()), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Gold earned", compact(stats.goldEarned), Modifier.weight(1f))
            StatTile("Towers built", compact(stats.towersBuilt.toLong()), Modifier.weight(1f))
            StatTile("Lives lost", compact(stats.livesLost.toLong()), Modifier.weight(1f))
        }
    }
}

@Composable
private fun BestsPanel(stats: LifetimeStats) {
    Section("Bests") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                "Fastest win",
                if (stats.fastestWinSeconds == 0) "–" else clock(stats.fastestWinSeconds),
                Modifier.weight(1f)
            )
            StatTile("Highest round", "${stats.bestRound}", Modifier.weight(1f))
            StatTile("Most pops", compact(stats.mostPops.toLong()), Modifier.weight(1f))
        }
    }
}

/** How often each tower was drafted: a ranked bar per tower, longest first. */
@Composable
private fun TowerPicksPanel(stats: LifetimeStats) {
    val ranked = stats.towerPicks.entries.sortedByDescending { it.value }.take(TOP_TOWERS)
    val most = ranked.first().value
    Section("Most picked towers") {
        for ((towerId, picks) in ranked) {
            val tower = GameData.TROOPS.firstOrNull { it.id == towerId } ?: continue
            Row(verticalAlignment = Alignment.CenterVertically) {
                TowerPortrait(tower, Modifier.size(34.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    tower.name, color = Cream, style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(96.dp)
                )
                Bar(picks / most.toFloat(), Modifier.weight(1f))
                Text(
                    if (picks == 1) "1 match" else "$picks matches",
                    color = Lilac, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End,
                    modifier = Modifier.width(84.dp)
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    GamePanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title.uppercase(), color = Sun, style = MaterialTheme.typography.labelSmall)
            content()
        }
    }
}

/** A number with its name. The number is the whole chart. */
@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier.clip(shape).background(NightDeep.copy(alpha = 0.6f)).border(2.dp, Ink, shape)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedText(value, fontSize = 21.sp)
        Text(label, color = Lilac, style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp, maxLines = 1)
    }
}

/**
 * A share of a whole: wins out of matches. The empty part is a darker step of the same green,
 * so the bar reads as one thing that is partly full, not as two competing colours.
 */
@Composable
private fun Meter(fraction: Float, modifier: Modifier = Modifier, color: Color = Leaf) {
    Canvas(modifier.height(13.dp)) {
        val o = 2.dp.toPx()
        val w = size.width - 2f * o
        val h = size.height - 2f * o
        drawRoundRect(Ink, cornerRadius = CornerRadius(size.height / 2f))
        drawRoundRect(color.darken(0.62f), Offset(o, o), Size(w, h), CornerRadius(h / 2f))
        if (fraction > 0f) {
            drawRoundRect(color, Offset(o, o), Size((w * fraction.coerceIn(0f, 1f)).coerceAtLeast(h), h), CornerRadius(h / 2f))
        }
    }
}

/** One bar of a ranking, anchored at the left. */
@Composable
private fun Bar(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.height(12.dp)) {
        val o = 2.dp.toPx()
        val h = size.height - 2f * o
        val w = ((size.width - 2f * o) * fraction.coerceIn(0f, 1f)).coerceAtLeast(h)
        drawRoundRect(Ink, size = Size(w + 2f * o, size.height), cornerRadius = CornerRadius(size.height / 2f))
        drawRoundRect(Sky, Offset(o, o), Size(w, h), CornerRadius(h / 2f))
    }
}

// 1,284 up to ten thousand, then 12.9K, then 4.2M: a tile has room for about five characters.
private fun compact(n: Long): String = when {
    n < 10_000 -> String.format(Locale.US, "%,d", n)
    n < 999_950 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
    else -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
}

// The display face has no lowercase, so a bare "s" or "m" after a digit reads as a letter O
// or a stray capital. Spell the unit out and keep a space before it.
private fun duration(seconds: Long): String = when {
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
}

private fun clock(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
