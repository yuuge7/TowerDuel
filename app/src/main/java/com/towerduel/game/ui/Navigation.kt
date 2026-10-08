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
                }
            )
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
                onDeploy = {
                    if (entry.isResumed() && viewModel.draftProblem == null) {
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
                        popUpTo("menu")
                        launchSingleTop = true
                    }
                },
                onQuit = {
                    if (entry.isResumed()) {
                        viewModel.quitMatch()
                        if (viewModel.matchMode == GameMode.CUP) toCup() else backToMenu()
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
                        if (viewModel.matchMode == GameMode.CUP) {
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
                onMainMenu = { if (entry.isResumed()) backToMenu() }
            )
        }
    }
}
