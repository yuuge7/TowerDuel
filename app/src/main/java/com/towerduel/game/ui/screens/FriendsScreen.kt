package com.towerduel.game.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.GameData
import com.towerduel.game.net.Lobby
import com.towerduel.game.net.LobbySeat
import com.towerduel.game.net.SeatKind
import com.towerduel.game.ui.Friends
import com.towerduel.game.ui.FriendsPhase
import com.towerduel.game.ui.GameViewModel
import com.towerduel.game.ui.components.ChunkyTextButton
import com.towerduel.game.ui.components.GamePanel
import com.towerduel.game.ui.components.OutlinedText
import com.towerduel.game.ui.components.ScreenBackground
import com.towerduel.game.ui.components.TowerPortrait
import com.towerduel.game.ui.components.UnitPortrait
import com.towerduel.game.ui.theme.AiColor
import com.towerduel.game.ui.theme.Cream
import com.towerduel.game.ui.theme.Dim
import com.towerduel.game.ui.theme.Ink
import com.towerduel.game.ui.theme.Leaf
import com.towerduel.game.ui.theme.Lilac
import com.towerduel.game.ui.theme.NightDeep
import com.towerduel.game.ui.theme.PanelLight
import com.towerduel.game.ui.theme.PlayerColor
import com.towerduel.game.ui.theme.Sky
import com.towerduel.game.ui.theme.Sun
import com.towerduel.game.ui.theme.Tomato

/**
 * Playing with friends over Bluetooth: host a match or join one, then the lobby where the host
 * decides who sits where. The draft and the match themselves are the ordinary screens.
 */
@Composable
fun FriendsScreen(viewModel: GameViewModel, onMenu: () -> Unit) {
    val friends = viewModel.friends
    val leave = {
        friends.close()
        onMenu()
    }
    BackHandler(onBack = leave)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .systemBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        OutlinedText("WITH FRIENDS", fontSize = 30.sp, color = Sun)
        Text("Over Bluetooth, phone to phone. No internet, no account.", color = Lilac, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val lobby = friends.lobby
            when {
                friends.phase == FriendsPhase.IDLE -> Doorway(viewModel, friends)
                friends.phase == FriendsPhase.JOINING || lobby == null ->
                    Waiting("Reaching ${friends.hostName.ifEmpty { "the host" }}…", "They have to be hosting, with this screen open, a few steps away.")
                else -> Table(friends, lobby)
            }
            val status = friends.status
            if (status != null) Text(status, color = Sun, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(10.dp))
        val lobby = friends.lobby
        if (friends.phase == FriendsPhase.HOSTING && lobby != null) {
            val problem = lobby.problem
            ChunkyTextButton(
                problem?.uppercase() ?: "START", friends::start,
                Modifier.fillMaxWidth().height(62.dp), color = Leaf, enabled = problem == null,
                fontSize = if (problem == null) 26.sp else 14.sp
            )
            Spacer(Modifier.height(8.dp))
        }
        ChunkyTextButton(
            if (friends.phase == FriendsPhase.IDLE) "MAIN MENU" else "LEAVE", leave,
            Modifier.fillMaxWidth().height(50.dp), color = if (friends.phase == FriendsPhase.IDLE) PanelLight else Tomato, fontSize = 17.sp
        )
    }
}

/** Not in a game yet: a name, Bluetooth made ready, then host or join. */
@Composable
private fun Doorway(viewModel: GameViewModel, friends: Friends) {
    // Bumped whenever something outside the app may have changed: a permission, the radio, a pairing.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                friends.refreshPaired()
                refresh++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        friends.refreshPaired()
        refresh++
    }
    val askSystem = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        friends.refreshPaired()
        refresh++
    }

    var name by rememberSaveable { mutableStateOf(viewModel.profile.playerName) }
    val shownName = name.ifBlank { friends.deviceName() }
    val keepName = { viewModel.profile.rememberName(name.trim()) }

    GamePanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("YOUR NAME", color = Sun, style = MaterialTheme.typography.labelSmall)
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier.fillMaxWidth().clip(shape).background(NightDeep.copy(alpha = 0.6f)).border(2.dp, Ink, shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = name, onValueChange = { name = it.take(20) }, singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = Cream),
                    cursorBrush = SolidColor(Sun),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                if (name.isEmpty()) Text(friends.deviceName(), color = Dim, style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    // `refresh` is read so that this part is drawn again after the player comes back from a system screen.
    val ready = refresh >= 0 && friends.bluetoothExists && friends.hasPermission() && friends.bluetoothOn()
    when {
        !friends.bluetoothExists -> Hint("This phone has no Bluetooth, so it cannot play with friends.")
        !friends.hasPermission() -> {
            Hint("The game needs your leave to use Bluetooth to reach your friends' phones. It asks for nothing else.")
            ChunkyTextButton(
                "ALLOW BLUETOOTH",
                { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) askPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                Modifier.fillMaxWidth().height(58.dp), color = Sky
            )
        }
        !friends.bluetoothOn() -> {
            Hint("Bluetooth is off.")
            ChunkyTextButton(
                "TURN BLUETOOTH ON",
                { runCatching { askSystem.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } },
                Modifier.fillMaxWidth().height(58.dp), color = Sky
            )
        }
    }
    if (ready) HostOrJoin(friends, shownName, keepName) { runCatching { askSystem.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } }
}

@Composable
private fun HostOrJoin(friends: Friends, shownName: String, keepName: () -> Unit, onPair: () -> Unit) {
    val difficulty = friends.lobby?.botDifficulty ?: Difficulty.MEDIUM
    ChunkyTextButton(
        "HOST A MATCH",
        {
            keepName()
            friends.host(shownName, difficulty)
        },
        Modifier.fillMaxWidth().height(62.dp), color = Leaf, fontSize = 24.sp
    )
    Hint("One of you hosts. The others join that phone from the list below. Up to four can play: 1 v 1, or 2 v 2 with bots in any seat nobody takes.")

    GamePanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("JOIN A FRIEND'S MATCH", color = Sun, style = MaterialTheme.typography.labelSmall)
            if (friends.paired.isEmpty()) {
                Text(
                    "No paired phones yet. Pair with your friend's phone once in the Bluetooth settings, and it shows up here.",
                    color = Lilac, style = MaterialTheme.typography.bodyMedium
                )
            }
            for (phone in friends.paired) {
                ChunkyTextButton(
                    phone.name.uppercase(),
                    {
                        keepName()
                        friends.join(phone, shownName)
                    },
                    Modifier.fillMaxWidth().height(50.dp), color = Sky, fontSize = 16.sp
                )
            }
            ChunkyTextButton("PAIR A PHONE", onPair, Modifier.fillMaxWidth().height(46.dp), color = PanelLight, fontSize = 15.sp)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Lilac, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Waiting(title: String, detail: String) {
    GamePanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedText(title.uppercase(), fontSize = 20.sp, color = Cream, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(detail, color = Lilac, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

/** The lobby: two teams of seats. The host changes it by tapping; a guest watches. */
@Composable
private fun Table(friends: Friends, lobby: Lobby) {
    val hosting = friends.phase == FriendsPhase.HOSTING
    if (hosting) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChunkyTextButton("1 V 1", { friends.setTeams(false) }, Modifier.weight(1f).height(46.dp), color = if (!lobby.team) Sun else PanelLight, fontSize = 17.sp)
            ChunkyTextButton("2 V 2", { friends.setTeams(true) }, Modifier.weight(1f).height(46.dp), color = if (lobby.team) Sun else PanelLight, fontSize = 17.sp)
        }
    }

    // Seats 0 and 1 share one lane, 2 and 3 the other. Whoever looks at the table sees their own team first.
    val mine = if (friends.mySeat < 2) listOf(0, 1) else listOf(2, 3)
    val theirs = if (friends.mySeat < 2) listOf(2, 3) else listOf(0, 1)
    Team("YOUR TEAM", PlayerColor, mine, lobby, friends, hosting)
    Team("AGAINST", AiColor, theirs, lobby, friends, hosting)

    if (lobby.seats.any { it.kind == SeatKind.BOT }) {
        GamePanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("THE BOTS PLAY ON", color = Sun, style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (difficulty in Difficulty.entries) {
                        ChunkyTextButton(
                            difficulty.label.uppercase(), { friends.setBotDifficulty(difficulty) },
                            Modifier.weight(1f).height(42.dp).alpha(if (hosting || difficulty == lobby.botDifficulty) 1f else 0.4f),
                            color = if (difficulty == lobby.botDifficulty) Sky else PanelLight, enabled = hosting, fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }

    Hint(
        when {
            !hosting -> "Waiting for ${lobby.seats[0].name} to start the match."
            lobby.team -> "Friends join from their own phones. Tap an open seat to give it to a bot, a bot to open its seat again, a friend to move them."
            else -> "Your friend joins from their own phone: Friends, then this phone's name in their list."
        }
    )
}

@Composable
private fun Team(title: String, color: Color, seats: List<Int>, lobby: Lobby, friends: Friends, hosting: Boolean) {
    GamePanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedText(title, fontSize = 15.sp, color = color)
            for (index in seats) {
                val seat = lobby.seats[index]
                if (seat.kind == SeatKind.CLOSED) continue
                SeatRow(seat, index, isMe = index == friends.mySeat, onTap = if (hosting && seat.kind != SeatKind.HOST) ({ friends.tapSeat(index) }) else null)
            }
        }
    }
}

@Composable
private fun SeatRow(seat: LobbySeat, index: Int, isMe: Boolean, onTap: (() -> Unit)?) {
    val shape = RoundedCornerShape(12.dp)
    val open = seat.kind == SeatKind.OPEN
    var row = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(NightDeep.copy(alpha = 0.55f))
        .border(2.dp, if (isMe) Sun else Ink, shape)
    if (onTap != null) row = row.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
    Row(modifier = row.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        when (seat.kind) {
            SeatKind.BOT -> UnitPortrait(GameData.unit("jammer"), Modifier.size(38.dp))
            SeatKind.OPEN, SeatKind.CLOSED -> Box(Modifier.size(38.dp))
            else -> if (isMe) TowerPortrait(GameData.TROOPS.first(), Modifier.size(38.dp)) else UnitPortrait(GameData.unit(FACES[index % FACES.size]), Modifier.size(38.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val title = when (seat.kind) {
                SeatKind.OPEN -> "Open seat"
                SeatKind.BOT -> "Bot"
                else -> seat.name
            }
            Text(title, color = if (open) Dim else Cream, style = MaterialTheme.typography.titleMedium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = when {
                isMe -> "You"
                seat.kind == SeatKind.HOST -> "Hosting"
                seat.kind == SeatKind.FRIEND -> if (onTap != null) "A friend. Tap to move them" else "A friend"
                seat.kind == SeatKind.OPEN -> if (onTap != null) "Waiting for a friend. Tap to seat a bot" else "Waiting for a friend"
                else -> if (onTap != null) "Tap to open the seat for a friend" else "Plays by itself"
            }
            Text(detail, color = Lilac, style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isMe) OutlinedText("YOU", fontSize = 14.sp, color = Sun, modifier = Modifier.offset(y = 1.dp))
    }
}

/** The unit that stands in for a friend's face, by seat: the same ones the match itself uses. */
private val FACES = listOf("grunt", "lancer", "runner", "bubbler")
