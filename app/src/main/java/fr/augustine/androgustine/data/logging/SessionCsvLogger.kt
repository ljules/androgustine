package fr.augustine.androgustine.data.logging

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionCsvLogger(private val context: Context) {
    private var writer: BufferedWriter? = null
    private var currentFile: File? = null

    val logFilePath: String?
        get() = currentFile?.absolutePath

    @Synchronized
    fun startSession(startTimeMs: Long) {
        if (writer != null) return

        try {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val logsDir = File(baseDir, LOGS_DIR_NAME)
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }

            val fileName = "androgustine_session_${fileTimestamp.format(Date(startTimeMs))}.csv"
            val file = File(logsDir, fileName)
            writer = BufferedWriter(FileWriter(file, false)).also { bufferedWriter ->
                bufferedWriter.write(CSV_HEADER)
                bufferedWriter.newLine()
                bufferedWriter.flush()
            }
            currentFile = file
            Log.i(TAG, "Session CSV log started: ${file.absolutePath}")
        } catch (error: IOException) {
            writer = null
            currentFile = null
            Log.e(TAG, "Unable to start session CSV log: ${error.message}", error)
        }
    }

    @Synchronized
    fun write(row: SessionCsvLogRow) {
        val activeWriter = writer ?: return

        try {
            activeWriter.write(row.toCsvLine())
            activeWriter.newLine()
            activeWriter.flush()
        } catch (error: IOException) {
            Log.e(TAG, "Unable to write session CSV log: ${error.message}", error)
        }
    }

    @Synchronized
    fun stopSession() {
        try {
            writer?.flush()
            writer?.close()
            currentFile?.let { file ->
                Log.i(TAG, "Session CSV log stopped: ${file.absolutePath}")
            }
        } catch (error: IOException) {
            Log.e(TAG, "Unable to close session CSV log: ${error.message}", error)
        } finally {
            writer = null
            currentFile = null
        }
    }

    private fun SessionCsvLogRow.toCsvLine(): String = listOf(
        timestampIso,
        elapsedSessionS.formatSeconds(),
        elapsedLapS.formatSeconds(),
        currentLap.toString(),
        activeStrategy,
        gpsLat.formatCoordinate(),
        gpsLon.formatCoordinate(),
        gpsSpeedKmh.formatFloat(),
        snappedDistanceM?.formatFloat().orEmpty(),
        ghostDistanceM?.formatFloat().orEmpty(),
        deltaDistanceM?.formatFloat().orEmpty(),
        weatherTemperatureC?.formatFloat().orEmpty(),
        weatherWindKmh?.formatFloat().orEmpty(),
        weatherRainProbability?.toString().orEmpty(),
        "",
        "",
        "",
        "",
        "",
        "",
        ""
    ).joinToString(",") { it.escapeCsv() }

    companion object {
        private const val TAG = "SessionCsvLogger"
        private const val LOGS_DIR_NAME = "AndroGustine_logs"
        const val CSV_HEADER =
            "timestampIso,elapsedSessionS,elapsedLapS,currentLap,activeStrategy,gpsLat,gpsLon,gpsSpeedKmh,snappedDistanceM,ghostDistanceM,deltaDistanceM,weatherTemperatureC,weatherWindKmh,weatherRainProbability,heartRateBpm,joulemeterCurrentA,joulemeterPowerW,joulemeterEnergyJ,pilotPaceInstruction,raceStatusInstruction,pitStopRequest"

        private val fileTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }
}

data class SessionCsvLogRow(
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
    val weatherTemperatureC: Float?,
    val weatherWindKmh: Float?,
    val weatherRainProbability: Int?
)

private fun String.escapeCsv(): String {
    val mustQuote = contains(",") || contains("\"") || contains("\n") || contains("\r")
    if (!mustQuote) return this
    return "\"" + replace("\"", "\"\"") + "\""
}

private fun Double.formatSeconds(): String = String.format(Locale.US, "%.3f", this)

private fun Float.formatFloat(): String = String.format(Locale.US, "%.3f", this)

private fun Double.formatCoordinate(): String = String.format(Locale.US, "%.8f", this)
