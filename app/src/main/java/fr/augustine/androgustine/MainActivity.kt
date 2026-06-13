package fr.augustine.androgustine

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import fr.augustine.androgustine.ui.screens.PilotScreen


// Définition des couleurs de votre charte graphique
val NavyBlue = Color(0xFF00334D) // Le fond sombre de votre maquette
val ShellOrange = Color(0xFFFF8C00) // L'orange pour les unités

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Garder l'écran allumé (votre modif précédente)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 2. Dire à Android que l'on gère nous-mêmes le placement (Edge-to-Edge)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 3. Masquer les barres système
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())

        // 4. Faire en sorte que les barres se cachent à nouveau après un swipe (mode immersif collant)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            PilotScreen()
        }
    }
}
