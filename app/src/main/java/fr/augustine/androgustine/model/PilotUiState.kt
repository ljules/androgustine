package fr.augustine.androgustine.model

import androidx.compose.ui.graphics.Color
import fr.augustine.androgustine.data.CircuitPoint

data class PilotUiState(
    val speed: Float = 0f,
    val timer: String = "00:00",
    val lapProgress: String = "1/11",
    val circuitPoints: List<CircuitPoint> = emptyList(),
    val circuitSource: String = "CSV local",
    val currentLat: Double = 0.0,
    val currentLon: Double = 0.0,
    val instruction: String = "MAINTENIR",
    val flag: String = "COURSE",
    val zoneColor: Color = Color.Transparent
)
