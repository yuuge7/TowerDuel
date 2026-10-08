package com.towerduel.game.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towerduel.game.data.GameData
import com.towerduel.game.ui.Cup
import com.towerduel.game.ui.GameViewModel
import com.towerduel.game.ui.components.ChunkyTextButton
import com.towerduel.game.ui.components.GamePanel
import com.towerduel.game.ui.components.OutlinedText
import com.towerduel.game.ui.components.ScreenBackground
import com.towerduel.game.ui.components.TowerPortrait
import com.towerduel.game.ui.components.UnitPortrait
import com.towerduel.game.ui.theme.Cream
import com.towerduel.game.ui.theme.Dim
import com.towerduel.game.ui.theme.Ink
import com.towerduel.game.ui.theme.Leaf
import com.towerduel.game.ui.theme.Lilac
import com.towerduel.game.ui.theme.NightDeep
import com.towerduel.game.ui.theme.PanelLight
import com.towerduel.game.ui.theme.PlayerColor
import com.towerduel.game.ui.theme.Sun
import com.towerduel.game.ui.theme.Tomato

/** The cup's bracket: who meets whom, who went through, and the button for the player's next match. */
@Composable
fun CupScreen(viewModel: GameViewModel, onPlay: () -> Unit, onMenu: () -> Unit) {
    val cup = viewModel.profile.cup
    BackHandler(onBack = onMenu)
    // Abandoning the cup forgets it a moment before this screen is left.
    if (cup != null) Bracket(viewModel, cup, onPlay, onMenu)
}

@Composable
private fun Bracket(viewModel: GameViewModel, cup: Cup, onPlay: () -> Unit, onMenu: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .systemBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedText("THE CUP", fontSize = 30.sp, color = Sun)
            Spacer(Modifier.weight(1f))
            OutlinedText(cup.difficulty.label.uppercase(), fontSize = 20.sp, color = Cream)
        }
        Text(statusLine(cup), color = Lilac, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (round in 0 until Cup.ROUNDS) RoundPanel(cup, round)
        }

        Spacer(Modifier.height(10.dp))
        if (cup.isOver) {
            ChunkyTextButton("NEW CUP", viewModel::newCup, Modifier.fillMaxWidth().height(62.dp), color = Leaf, fontSize = 26.sp)
            Spacer(Modifier.height(8.dp))
            ChunkyTextButton("MAIN MENU", onMenu, Modifier.fillMaxWidth().height(50.dp), color = PanelLight, fontSize = 17.sp)
        } else {
            ChunkyTextButton(
                "PLAY ${Cup.roundName(cup.round).uppercase()}", onPlay,
                Modifier.fillMaxWidth().height(62.dp), color = Leaf, fontSize = 24.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChunkyTextButton("MAIN MENU", onMenu, Modifier.weight(1f).height(50.dp), color = PanelLight, fontSize = 17.sp)
                ChunkyTextButton(
                    "ABANDON", { viewModel.abandonCup(); onMenu() },
                    Modifier.weight(1f).height(50.dp), color = Tomato, fontSize = 17.sp
                )
            }
        }
    }
}

private fun nameOf(id: String): String = if (id == Cup.PLAYER) "You" else GameData.RIVALS.firstOrNull { it.id == id }?.name ?: id

private fun statusLine(cup: Cup): String {
    val out = cup.knockedOutIn
    val opponent = cup.opponentId
    return when {
        cup.playerWon -> "The cup is yours. Three rivals, three wins."
        out != null -> "Knocked out in the ${Cup.roundName(out).lowercase()}. ${nameOf(cup.champion ?: "")} took the cup."
        opponent == null -> ""
        cup.round == Cup.FINAL && cup.matchDifficulty != cup.difficulty ->
            "The final: ${nameOf(opponent)}, playing on ${cup.matchDifficulty.label}."
        else -> "${Cup.roundName(cup.round)}: you meet ${nameOf(opponent)}. Lose once and you are out."
    }
}

/** One round of the bracket. A round nobody has reached yet shows empty places. */
@Composable
private fun RoundPanel(cup: Cup, round: Int) {
    val field = cup.field(round)
    val winners = cup.rounds.getOrNull(round)
    val matches = 4 shr round
    GamePanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (matches > 1) Cup.roundName(round) + "s" else Cup.roundName(round)).uppercase(),
                    color = Sun, style = MaterialTheme.typography.labelSmall
                )
                if (round == cup.round && !cup.isOver) {
                    Spacer(Modifier.width(8.dp))
                    Text("UP NEXT", color = Leaf, style = MaterialTheme.typography.labelSmall)
                }
            }
            for (match in 0 until matches) {
                val a = field?.get(match * 2)
                val b = field?.get(match * 2 + 1)
                val winner = winners?.get(match)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Entrant(a, won = winner != null && winner == a, lost = winner != null && winner != a, Modifier.weight(1f))
                    Text(
                        "VS", color = Dim, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                        modifier = Modifier.width(30.dp)
                    )
                    Entrant(b, won = winner != null && winner == b, lost = winner != null && winner != b, Modifier.weight(1f))
                }
            }
        }
    }
}

/** A place in the bracket: the player, a rival, or nobody yet. */
@Composable
private fun Entrant(id: String?, won: Boolean, lost: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val isPlayer = id == Cup.PLAYER
    Row(
        modifier = modifier
            .alpha(if (lost) 0.4f else 1f)
            .clip(shape)
            .background(NightDeep.copy(alpha = 0.55f))
            .border(2.dp, if (won) Sun else if (isPlayer) PlayerColor else Ink, shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val rival = GameData.RIVALS.firstOrNull { it.id == id }
        when {
            isPlayer -> TowerPortrait(GameData.TROOPS.first(), Modifier.size(34.dp))
            rival != null -> UnitPortrait(GameData.unit(rival.unitId), Modifier.size(34.dp))
            else -> Box(Modifier.size(34.dp))
        }
        Spacer(Modifier.width(5.dp))
        Column {
            when {
                id == null -> Text("To be decided", color = Dim, style = MaterialTheme.typography.bodyMedium)
                isPlayer -> OutlinedText("YOU", fontSize = 15.sp, color = PlayerColor, modifier = Modifier.offset(y = 1.dp))
                else -> Text(
                    nameOf(id), color = Cream, style = MaterialTheme.typography.labelLarge, fontSize = 12.sp, lineHeight = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (rival != null) {
                Text(rival.personality.label, color = Lilac, style = MaterialTheme.typography.bodyMedium, fontSize = 10.5.sp, lineHeight = 12.sp, maxLines = 1)
            }
        }
    }
}
