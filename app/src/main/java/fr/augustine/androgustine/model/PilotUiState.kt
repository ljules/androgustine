package fr.augustine.androgustine.model

import androidx.compose.ui.graphics.Color
import fr.augustine.androgustine.data.CircuitPoint
import fr.augustine.androgustine.data.GhostPoint
import fr.augustine.androgustine.data.StrategyIntervalUi
import fr.augustine.androgustine.data.telemetry.FirestoreStatus

data class PilotUiState(
    val speed: Float = 0f,
    val timer: String = "00:00",
    val lapProgress: String = "1/--",
    val totalLaps: Int = 0,
    val circuitPoints: List<CircuitPoint> = emptyList(),
    val circuitSource: String = "CSV local",
    val activeStrategyName: String = "Départ",
    val activeStrategyIntervals: List<StrategyIntervalUi> = emptyList(),
    val startStrategyIntervals: List<StrategyIntervalUi> = emptyList(),
    val raceStrategyIntervals: List<StrategyIntervalUi> = emptyList(),
    val startGhostPoints: List<GhostPoint> = emptyList(),
    val raceGhostPoints: List<GhostPoint> = emptyList(),
    val ghostPoint: GhostPoint? = null,
    val ghostDistanceM: Float? = null,
    val ghostDeltaDistanceM: Float? = null,
    val weatherTemperatureC: Float? = null,
    val weatherWindKmh: Float? = null,
    val weatherRainProbability: Int? = null,
    val weatherStatusMessage: String = "Meteo : attente GPS",
    val heartRateBpm: Int? = null,
    val firestoreStatus: FirestoreStatus = FirestoreStatus(enabled = false),
    val currentLat: Double = 0.0,
    val currentLon: Double = 0.0,
    val instruction: String = "MAINTENIR",
    val flag: String = "COURSE",
    val zoneColor: Color = Color.Transparent
)
