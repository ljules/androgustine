package fr.augustine.androgustine.viewmodel

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.augustine.androgustine.data.CircuitManager
import fr.augustine.androgustine.data.CircuitPoint
import fr.augustine.androgustine.data.GhostPoint
import fr.augustine.androgustine.data.gps.GpsService
import fr.augustine.androgustine.data.imports.SimAugustineImportRepository
import fr.augustine.androgustine.data.timer.TimeService
import fr.augustine.androgustine.model.PilotUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RaceViewModel(application: Application) : AndroidViewModel(application) {

    private val gpsService = GpsService(application)
    // On passe 'application' (qui est un Context) au manager
    private val circuitManager = CircuitManager(application)
    private val timeService = TimeService()

    private val _uiState = MutableStateFlow(PilotUiState())
    val uiState: StateFlow<PilotUiState> = _uiState.asStateFlow()

    private var isTimerRunning = false
    private var lastLapTimestamp = 0L
    private var currentLapStartedAtMs = 0L

    // CONFIGURATION DE LA COURSE
    companion object {
        private const val START_SPEED_THRESHOLD = 8.0f // km/h pour lancer le chrono
        private const val LAP_DISTANCE_THRESHOLD = 20f // mètres autour de la ligne
        private const val LAP_THRESHOLD_MS = 30000L    // 30s min entre deux tours (anti-rebond)

        // Coordonnées de la ligne (À MODIFIER avec vos coordonnées réelles)
        private const val FINISH_LINE_LAT = 48.564144
        private const val FINISH_LINE_LON = 2.782492
    }

    init {
        // Chargement du circuit au démarrage (Dossier Interne)
        loadCircuitData()
        // Démarrage du flux GPS
        startGpsTracking()
    }

    private fun loadCircuitData() {
        viewModelScope.launch {
            try {
                val points = circuitManager.loadCircuitFromInternalStorage()
                _uiState.value = _uiState.value.copy(
                    circuitPoints = points,
                    circuitSource = "CSV local",
                    activeStrategyName = "Départ",
                    activeStrategyIntervals = emptyList(),
                    startStrategyIntervals = emptyList(),
                    raceStrategyIntervals = emptyList(),
                    startGhostPoints = emptyList(),
                    raceGhostPoints = emptyList(),
                    ghostPoint = null,
                    ghostDistanceM = null,
                    ghostDeltaDistanceM = null
                )
            } catch (e: Exception) {
                Log.e("RaceViewModel", "CRASH lors du chargement : ${e.message}")
            }
        }
    }

    fun useImportedCircuitIfAvailable() {
        val importedCircuit = SimAugustineImportRepository.getCurrentCircuit() ?: return
        _uiState.value = _uiState.value.copy(
            circuitPoints = importedCircuit.points,
            circuitSource = importedCircuit.sourceLabel,
            activeStrategyName = "Départ",
            activeStrategyIntervals = importedCircuit.startStrategyIntervals,
            startStrategyIntervals = importedCircuit.startStrategyIntervals,
            raceStrategyIntervals = importedCircuit.raceStrategyIntervals,
            startGhostPoints = importedCircuit.startGhostPoints,
            raceGhostPoints = importedCircuit.raceGhostPoints,
            ghostPoint = null,
            ghostDistanceM = null,
            ghostDeltaDistanceM = null
        )
        if (isTimerRunning) {
            updateGhostPosition()
        }
    }

    private fun startGpsTracking() {
        viewModelScope.launch {
            gpsService.getGpsUpdates().collect { gpsData ->
                // 1. Mise à jour de la vitesse et de la position actuelle
                _uiState.value = _uiState.value.copy(
                    speed = gpsData.speed,
                    currentLat = gpsData.latitude,
                    currentLon = gpsData.longitude
                )

                // 2. Déclenchement automatique du chrono au départ
                if (!isTimerRunning && gpsData.speed > START_SPEED_THRESHOLD) {
                    startRaceTimer()
                }

                // 3. Vérification du franchissement de ligne
                if (isTimerRunning) {
                    checkLapDetection(gpsData.latitude, gpsData.longitude)
                }
            }
        }
    }

    private fun startRaceTimer() {
        isTimerRunning = true
        val now = System.currentTimeMillis()
        lastLapTimestamp = now // Le chrono commence, le tour 1 aussi
        currentLapStartedAtMs = now
        viewModelScope.launch {
            timeService.timerFlow().collect { seconds ->
                _uiState.value = _uiState.value.copy(
                    timer = timeService.formatSeconds(seconds)
                )
                updateGhostPosition()
            }
        }
    }

    private fun checkLapDetection(currentLat: Double, currentLon: Double) {
        val startFinishPoint = _uiState.value.circuitPoints.firstOrNull()
        val targetLat = startFinishPoint?.lat ?: FINISH_LINE_LAT
        val targetLon = startFinishPoint?.lon ?: FINISH_LINE_LON

        val results = FloatArray(1)
        Location.distanceBetween(
            currentLat, currentLon,
            targetLat, targetLon,
            results
        )

        val distanceToLine = results[0]
        val currentTime = System.currentTimeMillis()

        // Si on est dans la zone et que le délai de sécurité est passé
        if (distanceToLine < LAP_DISTANCE_THRESHOLD && (currentTime - lastLapTimestamp) > LAP_THRESHOLD_MS) {
            lastLapTimestamp = currentTime
            incrementLap()
        }
    }

    private fun incrementLap() {
        val parts = _uiState.value.lapProgress.split("/")
        val currentLapNumber = parts[0].toIntOrNull() ?: 1
        val totalLaps = parts.getOrNull(1)?.toIntOrNull() ?: 11

        if (currentLapNumber < totalLaps) {
            val nextLap = currentLapNumber + 1
            val nextStrategyName = if (nextLap >= 2) "Course" else "Départ"
            val nextStrategyIntervals = if (nextLap >= 2) {
                _uiState.value.raceStrategyIntervals
            } else {
                _uiState.value.startStrategyIntervals
            }

            _uiState.value = _uiState.value.copy(
                lapProgress = "$nextLap/$totalLaps",
                activeStrategyName = nextStrategyName,
                activeStrategyIntervals = nextStrategyIntervals
            )
            currentLapStartedAtMs = System.currentTimeMillis()
            updateGhostPosition()
        }
    }

    private fun updateGhostPosition() {
        if (!isTimerRunning || currentLapStartedAtMs == 0L) {
            clearGhostPosition()
            return
        }

        val state = _uiState.value
        val ghostSamples = if (getCurrentLapNumber(state) >= 2) {
            state.raceGhostPoints
        } else {
            state.startGhostPoints
        }

        val elapsedTimeS = (System.currentTimeMillis() - currentLapStartedAtMs) / 1000.0
        val ghostPoint = interpolateGhostPoint(ghostSamples, elapsedTimeS)
        if (ghostPoint == null) {
            clearGhostPosition()
            return
        }

        val realDistanceM = findNearestCircuitDistance(
            state.circuitPoints,
            state.currentLat,
            state.currentLon
        )

        _uiState.value = state.copy(
            ghostPoint = ghostPoint,
            ghostDistanceM = ghostPoint.distanceM,
            ghostDeltaDistanceM = realDistanceM?.let { it - ghostPoint.distanceM }
        )
    }

    private fun clearGhostPosition() {
        val state = _uiState.value
        if (state.ghostPoint != null || state.ghostDistanceM != null || state.ghostDeltaDistanceM != null) {
            _uiState.value = state.copy(
                ghostPoint = null,
                ghostDistanceM = null,
                ghostDeltaDistanceM = null
            )
        }
    }

    private fun interpolateGhostPoint(samples: List<GhostPoint>, elapsedTimeS: Double): GhostPoint? {
        if (samples.isEmpty()) return null
        if (samples.size == 1 || elapsedTimeS <= samples.first().timeS) return samples.first()
        if (elapsedTimeS >= samples.last().timeS) return samples.last()

        val nextIndex = samples.indexOfFirst { it.timeS >= elapsedTimeS }
        if (nextIndex <= 0) return samples.first()

        val previous = samples[nextIndex - 1]
        val next = samples[nextIndex]
        val duration = next.timeS - previous.timeS
        if (duration <= 0.0) return previous

        val ratio = ((elapsedTimeS - previous.timeS) / duration).coerceIn(0.0, 1.0)
        return GhostPoint(
            timeS = elapsedTimeS,
            distanceM = interpolate(previous.distanceM, next.distanceM, ratio),
            utmX = interpolate(previous.utmX, next.utmX, ratio),
            utmY = interpolate(previous.utmY, next.utmY, ratio),
            lon = interpolate(previous.lon, next.lon, ratio),
            lat = interpolate(previous.lat, next.lat, ratio)
        )
    }

    private fun findNearestCircuitDistance(
        points: List<CircuitPoint>,
        currentLat: Double,
        currentLon: Double
    ): Float? {
        if (points.isEmpty()) return null
        return points.minByOrNull { point ->
            val latDelta = point.lat - currentLat
            val lonDelta = point.lon - currentLon
            latDelta * latDelta + lonDelta * lonDelta
        }?.distance
    }

    private fun getCurrentLapNumber(state: PilotUiState): Int =
        state.lapProgress.substringBefore("/").toIntOrNull() ?: 1

    private fun interpolate(start: Float, end: Float, ratio: Double): Float =
        (start + ((end - start) * ratio)).toFloat()

    private fun interpolate(start: Double, end: Double, ratio: Double): Double =
        start + ((end - start) * ratio)
}
