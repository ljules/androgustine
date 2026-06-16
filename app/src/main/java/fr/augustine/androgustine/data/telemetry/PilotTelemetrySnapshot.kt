package fr.augustine.androgustine.data.telemetry

data class PilotTelemetrySnapshot(
    val timestampIso: String,
    val raceStarted: Boolean,
    val elapsedSessionS: Double,
    val elapsedLapS: Double,
    val currentLap: Int,
    val activeStrategy: String,
    val gpsLat: Double?,
    val gpsLon: Double?,
    val gpsSpeedKmh: Float,
    val snappedDistanceM: Float?,
    val ghostDistanceM: Float?,
    val deltaDistanceM: Float?,
    val heartRateBpm: Int?,
    val weatherTemperatureC: Float?,
    val weatherWindKmh: Float?,
    val weatherRainProbability: Int?
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "timestampIso" to timestampIso,
        "raceStarted" to raceStarted,
        "elapsedSessionS" to elapsedSessionS,
        "elapsedLapS" to elapsedLapS,
        "elapsedSessionFormatted" to elapsedSessionS.formatDuration(),
        "elapsedLapFormatted" to elapsedLapS.formatDuration(),
        "currentLap" to currentLap,
        "activeStrategy" to activeStrategy,
        "gpsLat" to gpsLat,
        "gpsLon" to gpsLon,
        "gpsSpeedKmh" to gpsSpeedKmh,
        "snappedDistanceM" to snappedDistanceM,
        "ghostDistanceM" to ghostDistanceM,
        "deltaDistanceM" to deltaDistanceM,
        "heartRateBpm" to heartRateBpm,
        "weatherTemperatureC" to weatherTemperatureC,
        "weatherWindKmh" to weatherWindKmh,
        "weatherRainProbability" to weatherRainProbability
    )
}

private fun Double.formatDuration(): String {
    val totalSeconds = if (isFinite()) toInt().coerceAtLeast(0) else 0
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
