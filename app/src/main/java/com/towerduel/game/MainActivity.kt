package com.towerduel.game

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.towerduel.game.ui.AppNavHost
import com.towerduel.game.ui.GameViewModel
import com.towerduel.game.ui.components.LocalSfx
import com.towerduel.game.ui.theme.NightDeep
import com.towerduel.game.ui.theme.TowerDuelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so keep light system-bar icons even when the phone is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            TowerDuelApp()
        }
    }
}

@Composable
private fun TowerDuelApp() {
    TowerDuelTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = NightDeep) {
            val viewModel: GameViewModel = viewModel()
            CompositionLocalProvider(LocalSfx provides viewModel::playUi) {
                AppNavHost(viewModel)
            }
        }
    }
}
