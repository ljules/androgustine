package com.example.teamaugustineapp.viewmodel

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.teamaugustineapp.data.CircuitManager
import com.example.teamaugustineapp.data.gps.GpsService
import com.example.teamaugustineapp.data.timer.TimeService
import com.example.teamaugustineapp.model.PilotUiState
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
                _uiState.value = _uiState.value.copy(circuitPoints = points)
            } catch (e: Exception) {
                Log.e("RaceViewModel", "CRASH lors du chargement : ${e.message}")
            }
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
        lastLapTimestamp = System.currentTimeMillis() // Le chrono commence, le tour 1 aussi
        viewModelScope.launch {
            timeService.timerFlow().collect { seconds ->
                _uiState.value = _uiState.value.copy(
                    timer = timeService.formatSeconds(seconds)
                )
            }
        }
    }

    private fun checkLapDetection(currentLat: Double, currentLon: Double) {
        val results = FloatArray(1)
        Location.distanceBetween(
            currentLat, currentLon,
            FINISH_LINE_LAT, FINISH_LINE_LON,
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
            _uiState.value = _uiState.value.copy(
                lapProgress = "$nextLap/$totalLaps"
            )
        }
    }
}