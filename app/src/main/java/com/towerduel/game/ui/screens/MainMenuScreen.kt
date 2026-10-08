package com.towerduel.game.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.GameData
import com.towerduel.game.data.LaneSpace
import com.towerduel.game.ui.DemoMatch
import com.towerduel.game.ui.GameViewModel
import com.towerduel.game.ui.components.ChunkyButton
import com.towerduel.game.ui.components.ChunkyTextButton
import com.towerduel.game.ui.components.GameIcon
import com.towerduel.game.ui.components.GameIconKind
import com.towerduel.game.ui.components.GamePanel
import com.towerduel.game.ui.components.OutlinedText
import com.towerduel.game.ui.components.ScreenBackground
import com.towerduel.game.ui.components.TowerPortrait
import com.towerduel.game.ui.components.UnitPortrait
import com.towerduel.game.ui.render.LaneView
import com.towerduel.game.ui.theme.Cream
import com.towerduel.game.ui.theme.Ink
import com.towerduel.game.ui.theme.Leaf
import com.towerduel.game.ui.theme.Lilac
import com.towerduel.game.ui.theme.NightDeep
import com.towerduel.game.ui.theme.PanelLight
import com.towerduel.game.ui.theme.PlayerColor
import com.towerduel.game.ui.theme.Sky
import com.towerduel.game.ui.theme.Sun
import com.towerduel.game.ui.theme.Tomato
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

private enum class MenuTab(val label: String) { BATTLE("BATTLE"), STATS("STATS") }

@Composable
fun MainMenuScreen(viewModel: GameViewModel, onStart: (Difficulty) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(MenuTab.BATTLE) }
    // A first-time player gets the rules before anything else.
    var showHelp by rememberSaveable { mutableStateOf(viewModel.profile.isNewPlayer) }

    BackHandler(enabled = tab != MenuTab.BATTLE && !showHelp) { tab = MenuTab.BATTLE }

    Box(modifier = Modifier.fillMaxSize().background(ScreenBackground)) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
            when (tab) {
                MenuTab.BATTLE -> BattleTab(viewModel, onStart, onHelp = { showHelp = true }, Modifier.weight(1f))
                MenuTab.STATS -> StatsTab(viewModel, onPlay = { tab = MenuTab.BATTLE }, Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (entry in MenuTab.entries) {
                    ChunkyTextButton(
                        entry.label,
                        onClick = { tab = entry },
                        modifier = Modifier.weight(1f).height(50.dp).semantics { selected = entry == tab },
                        color = if (entry == tab) Sun else PanelLight,
                        fontSize = 17.sp
                    )
                }
            }
        }

        if (showHelp) {
            HowToPlay(onClose = {
                showHelp = false
                viewModel.profile.markNotNew()
            })
        }
    }
}

@Composable
private fun BattleTab(viewModel: GameViewModel, onStart: (Difficulty) -> Unit, onHelp: () -> Unit, modifier: Modifier) {
    var selected by rememberSaveable { mutableStateOf(viewModel.selectedDifficulty) }

    // Runs the menu's demo match, and prepares the next one in the background before the
    // current one ends, so the lane never goes blank and the menu never stutters. It lives in
    // this tab, so the match stands still while the player is looking at their stats.
    LaunchedEffect(Unit) {
        while (true) {
            val match = viewModel.demo
            if (match == null) {
                viewModel.demo = withContext(Dispatchers.Default) { DemoMatch.warmedUp() }
                continue
            }
            while (!match.finished) withFrameNanos { match.onFrame(it) }
            val next = async(Dispatchers.Default) { DemoMatch.warmedUp() }
            while (!next.isCompleted) withFrameNanos { match.onFrame(it) }
            viewModel.demo = next.await()
        }
    }

    // The lane is the one part that can give: on a short screen it shrinks, and whatever
    // height is left over on a tall one is shared out between the three groups.
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Logo()

        // The match behind the glass is real: two AIs playing the same game the player is about to.
        Box(modifier = Modifier.weight(1f, fill = false).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            val demo = viewModel.demo
            if (demo != null) {
                Box {
                    LaneView(demo.engine, demo.engine.playerField, PlayerColor, demo::observeFrame)
                    Tag("LIVE · AI VS AI", Modifier.align(Alignment.TopStart).padding(7.dp))
                }
            } else {
                // Holds the lane's place for the moment it takes to prepare the first match.
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier.aspectRatio(LaneSpace.WIDTH / LaneSpace.HEIGHT)
                        .clip(shape).background(NightDeep).border(2.5.dp, Ink, shape)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (difficulty in Difficulty.entries) {
                    ChunkyTextButton(
                        difficulty.label.uppercase(),
                        onClick = { selected = difficulty },
                        modifier = Modifier.weight(1f).height(50.dp),
                        color = if (difficulty == selected) Sky else PanelLight,
                        fontSize = 17.sp
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(selected.blurb, color = Lilac, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)

            Spacer(Modifier.height(10.dp))
            ChunkyTextButton(
                "PLAY", onClick = { onStart(selected) },
                modifier = Modifier.fillMaxWidth().height(76.dp), color = Leaf, fontSize = 38.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChunkyTextButton(
                    "HOW TO PLAY", onClick = onHelp,
                    modifier = Modifier.weight(1f).height(50.dp), color = PanelLight, fontSize = 16.sp
                )
                ChunkyButton(
                    viewModel::toggleSound, Modifier.size(width = 62.dp, height = 50.dp),
                    color = PanelLight, sound = null, contentPadding = PaddingValues(0.dp),
                    description = if (viewModel.profile.soundOn) "Turn sound off" else "Turn sound on"
                ) {
                    GameIcon(
                        if (viewModel.profile.soundOn) GameIconKind.SOUND_ON else GameIconKind.SOUND_OFF,
                        Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Logo() {
    val breathing = rememberInfiniteTransition(label = "logo")
    val scale by breathing.animateFloat(
        initialValue = 1f, targetValue = 1.035f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "logoScale"
    )
    Column(modifier = Modifier.rotate(-4f).scale(scale), horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedText("TOWER", fontSize = 66.sp, color = Sky, letterSpacing = 2.sp)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(y = (-14).dp)) {
            TowerPortrait(GameData.TROOPS.first(), Modifier.size(54.dp), level = GameData.TROOPS.first().maxLevel)
            OutlinedText("DUEL", fontSize = 66.sp, color = Sun, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 6.dp))
            UnitPortrait(GameData.unit("boss"), Modifier.size(54.dp))
        }
    }
}

@Composable
private fun Tag(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier.clip(shape).background(Tomato).border(2.dp, Ink, shape).padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        OutlinedText(text, fontSize = 12.sp, modifier = Modifier.offset(y = 1.dp))
    }
}

@Composable
private fun HowToPlay(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.78f))
            .pointerInput(Unit) { detectTapGestures { } }
            .systemBarsPadding()
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        GamePanel(modifier = Modifier.fillMaxWidth(), corner = 24.dp) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedText("HOW TO PLAY", fontSize = 30.sp, color = Sun, modifier = Modifier.align(Alignment.CenterHorizontally))
                Rule("BUILD", "Pick a tower, then touch your lane to place it. Towers cannot stand on the track.") {
                    TowerPortrait(GameData.TROOPS.first(), Modifier.size(46.dp))
                }
                Rule("UPGRADE", "Tap a tower to upgrade it, change who it shoots first, or sell it.") {
                    TowerPortrait(GameData.TROOPS.first(), Modifier.size(46.dp), level = GameData.TROOPS.first().maxLevel)
                }
                Rule("SEND", "Units you send walk the rival's lane. Most also raise your income for the rest of the match.") {
                    UnitPortrait(GameData.unit("grunt"), Modifier.size(42.dp))
                }
                Rule("SURVIVE", "A wave hits both lanes every round and each one is tougher. Anything that reaches a keep costs lives.") {
                    GameIcon(GameIconKind.HEART, Modifier.size(36.dp))
                }
                Rule("WIN", "The first side out of lives loses. After the last round it is sudden death.") {
                    UnitPortrait(GameData.unit("boss"), Modifier.size(46.dp))
                }
                ChunkyTextButton("GOT IT", onClose, Modifier.fillMaxWidth().height(58.dp), color = Leaf)
            }
        }
    }
}

@Composable
private fun Rule(title: String, body: String, picture: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(52.dp), contentAlignment = Alignment.Center) { picture() }
        Spacer(Modifier.width(10.dp))
        Column {
            OutlinedText(title, fontSize = 16.sp, color = Cream)
            Text(body, color = Lilac, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
