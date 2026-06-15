package fr.augustine.androgustine.data.telemetry

data class PilotTelemetrySnapshot(
    val timestampIso: String,
    val elapsedSessionS: Double,
    val elapsedLapS: Double,
    val currentLap: Int,
    val activeStrategy: String,
    val gpsLat: Double,
    val gpsLon: Double,
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
        "elapsedSessionS" to elapsedSessionS,
        "elapsedLapS" to elapsedLapS,
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
