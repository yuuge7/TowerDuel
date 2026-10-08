package com.towerduel.game.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.towerduel.game.data.GameMode
import com.towerduel.game.ui.screens.BattleScreen
import com.towerduel.game.ui.screens.CupScreen
import com.towerduel.game.ui.screens.DraftScreen
import com.towerduel.game.ui.screens.FriendsScreen
import com.towerduel.game.ui.screens.MainMenuScreen
import com.towerduel.game.ui.screens.ResultsScreen

/** True once this screen has finished animating in; filters taps on a screen that is on its way out. */
private fun NavBackStackEntry.isResumed(): Boolean = lifecycle.currentState == Lifecycle.State.RESUMED

@Composable
fun AppNavHost(viewModel: GameViewModel) {
    val navController = rememberNavController()
    val backToMenu: () -> Unit = { navController.popBackStack("menu", inclusive = false) }
    // A cup match starts from the bracket and goes back to it, whatever happened in between.
    val toCup: () -> Unit = {
        navController.navigate("cup") {
            popUpTo("menu")
            launchSingleTop = true
        }
    }

    // Back to the friends screen from wherever a game with friends has taken this phone.
    val toFriends: () -> Unit = {
        if (!navController.popBackStack("friends", inclusive = false)) {
            navController.navigate("friends") {
                popUpTo("menu")
                launchSingleTop = true
            }
        }
    }

    // A game with friends moves every phone from screen to screen together: the host starts the
    // draft, the last pick starts the match, and a lobby that reopens calls everybody back to it.
    val friendsPhase = viewModel.friends.phase
    LaunchedEffect(friendsPhase) {
        val at = navController.currentDestination?.route
        when (friendsPhase) {
            FriendsPhase.DRAFT -> if (at != "draft") navController.navigate("draft") {
                popUpTo("friends")
                launchSingleTop = true
            }
            // Still PLAYING while the result is looked at: only a draft leads into a battle.
            FriendsPhase.PLAYING -> if (at != "battle" && at != "results") navController.navigate("battle") {
                popUpTo("friends")
                launchSingleTop = true
            }
            FriendsPhase.HOSTING, FriendsPhase.JOINED, FriendsPhase.JOINING ->
                if (at == "draft" || at == "battle" || at == "results") {
                    // Back in the lobby from a draft: it was called off (somebody left), and nothing of it is left.
                    if (at == "draft") viewModel.friendsGone()
                    toFriends()
                }
            FriendsPhase.IDLE ->
                // The game with friends is gone (the host closed it, the link went) before a match began.
                if (viewModel.online && at == "draft") {
                    viewModel.friendsGone()
                    toFriends()
                }
            FriendsPhase.READY -> Unit
        }
    }

    NavHost(navController = navController, startDestination = "menu") {
        composable("menu") { entry ->
            MainMenuScreen(
                viewModel = viewModel,
                onStart = { difficulty ->
                    if (entry.isResumed()) {
                        if (viewModel.selectedMode == GameMode.CUP) {
                            viewModel.enterCup(difficulty)
                            toCup()
                        } else {
                            viewModel.rollNewMatchSetup(difficulty)
                            navController.navigate("draft") { launchSingleTop = true }
                        }
                    }
                },
                onFriends = { if (entry.isResumed()) navController.navigate("friends") { launchSingleTop = true } }
            )
        }
        composable("friends") { entry ->
            FriendsScreen(viewModel = viewModel, onMenu = { if (entry.isResumed()) backToMenu() })
        }
        composable("cup") { entry ->
            if (viewModel.profile.cup == null) {
                LaunchedEffect(Unit) { backToMenu() }
                return@composable
            }
            CupScreen(
                viewModel = viewModel,
                onPlay = {
                    if (entry.isResumed() && viewModel.rollCupMatch()) {
                        navController.navigate("draft") { launchSingleTop = true }
                    }
                },
                onMenu = { if (entry.isResumed()) backToMenu() }
            )
        }
        composable("draft") { entry ->
            // After process death the back stack is restored but the rolled match is gone.
            if (!viewModel.hasMatchSetup) {
                LaunchedEffect(Unit) { backToMenu() }
                return@composable
            }
            DraftScreen(
                viewModel = viewModel,
                onLeaveFriends = {
                    if (entry.isResumed()) {
                        viewModel.quitMatch()
                        toFriends()
                    }
                },
                onDeploy = {
                    if (viewModel.online) {
                        // With friends the match begins when everybody has picked, not when this phone has.
                        if (entry.isResumed()) viewModel.readyUp()
                    } else if (entry.isResumed() && viewModel.draftProblem == null) {
                        viewModel.startMatch()
                        navController.navigate("battle") {
                            popUpTo("menu")
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable("battle") { entry ->
            val eng = viewModel.engine
            if (eng == null) {
                LaunchedEffect(Unit) { backToMenu() }
                return@composable
            }
            BattleScreen(
                viewModel = viewModel,
                eng = eng,
                onMatchEnd = {
                    navController.navigate("results") {
                        // With friends the lobby stays underneath: that is where the result leads back to.
                        popUpTo(if (viewModel.online) "friends" else "menu")
                        launchSingleTop = true
                    }
                },
                onQuit = {
                    if (entry.isResumed()) {
                        val cup = viewModel.matchMode == GameMode.CUP && !viewModel.online
                        viewModel.quitMatch()
                        if (cup) toCup() else backToMenu()
                    }
                }
            )
        }
        composable("results") { entry ->
            val eng = viewModel.engine
            if (eng == null) {
                LaunchedEffect(Unit) { backToMenu() }
                return@composable
            }
            ResultsScreen(
                viewModel = viewModel,
                eng = eng,
                onPlayAgain = {
                    if (entry.isResumed()) {
                        if (viewModel.online) {
                            // Back to the lobby, if it is still there; the phase change does the navigating.
                            viewModel.leaveFriendsResult()
                            if (viewModel.friends.phase == FriendsPhase.IDLE) toFriends()
                        } else if (viewModel.matchMode == GameMode.CUP) {
                            toCup()
                        } else {
                            viewModel.rollNewMatchSetup(viewModel.selectedDifficulty, viewModel.matchMode)
                            navController.navigate("draft") {
                                popUpTo("menu")
                                launchSingleTop = true
                            }
                        }
                    }
                },
                onMainMenu = {
                    if (entry.isResumed()) {
                        if (viewModel.online) viewModel.quitMatch()
                        backToMenu()
                    }
                }
            )
        }
    }
}
