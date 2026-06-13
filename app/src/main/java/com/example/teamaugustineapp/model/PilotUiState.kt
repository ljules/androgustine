package com.example.teamaugustineapp.model

import androidx.compose.ui.graphics.Color
import com.example.teamaugustineapp.data.CircuitPoint

data class PilotUiState(
    val speed: Float = 0f,
    val timer: String = "00:00",
    val lapProgress: String = "1/11",
    val circuitPoints: List<CircuitPoint> = emptyList(), // Ajouté
    val currentLat: Double = 0.0, // Ajouté pour le futur point pilote
    val currentLon: Double = 0.0,  // Ajouté pour le futur point pilote
    val instruction: String = "MAINTENIR",
    val flag: String = "COURSE",
    val zoneColor: Color = Color.Transparent
)