package fr.augustine.androgustine.data.telemetry

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import fr.augustine.androgustine.data.CircuitPoint
import fr.augustine.androgustine.data.StrategyIntervalUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TelemetryFirestoreRepository(
    private val context: Context,
    private val enabled: Boolean = TelemetryConfig.FIRESTORE_TELEMETRY_ENABLED
) {
    private var firestore: FirebaseFirestore? = null
    private var sessionId: String? = null
    private var lastWriteAtMs = 0L
    private var initializationAttempted = false
    private var instructionsRegistration: ListenerRegistration? = null
    private val _status = MutableStateFlow(FirestoreStatus(enabled = enabled))
    val status: StateFlow<FirestoreStatus> = _status.asStateFlow()

    fun startSession(startTimeMs: Long): String {
        if (sessionId == null) {
            sessionId = "androgustine-session-${sessionTimestamp.format(Date(startTimeMs))}"
            lastWriteAtMs = 0L
        }
        _status.value = _status.value.copy(sessionId = sessionId)
        Log.i(TAG, "Telemetry Firestore sessionId=$sessionId")
        createSessionDocument(startTimeMs)
        return sessionId.orEmpty()
    }

    fun markRaceStarted() {
        if (!enabled) return

        val activeSessionId = sessionId ?: return
        val activeFirestore = initializeFirestoreIfNeeded() ?: return
        val path = "raceSessions/$activeSessionId"

        activeFirestore
            .collection("raceSessions")
            .document(activeSessionId)
            .set(
                mapOf(
                    "status" to "RUNNING",
                    "raceStarted" to true
                ),
                SetOptions.merge()
            )
            .addOnFailureListener { error ->
                recordError("Session document update failed: ${error.message ?: error.javaClass.simpleName}")
                Log.w(TAG, "Session document update failed: $path", error)
            }
            .addOnSuccessListener {
                Log.i(TAG, "Session document updated : $path")
            }
    }

    fun updateSessionRaceContext(totalLaps: Int?, trackName: String?) {
        if (!enabled) return

        val activeSessionId = sessionId ?: return
        val activeFirestore = initializeFirestoreIfNeeded() ?: return
        val path = "raceSessions/$activeSessionId"
        val contextFields = mutableMapOf<String, Any>(
            "trackName" to normalizeTrackName(trackName)
        )
        totalLaps?.let { contextFields["totalLaps"] = it }

        activeFirestore
            .collection("raceSessions")
            .document(activeSessionId)
            .set(contextFields, SetOptions.merge())
            .addOnFailureListener { error ->
                recordError("Session race context update failed: ${error.message ?: error.javaClass.simpleName}")
                Log.w(TAG, "Session race context update failed: $path", error)
            }
            .addOnSuccessListener {
                Log.i(TAG, "Session race context updated : $path")
            }
    }

    fun publishTrackData(
        trackName: String?,
        totalLaps: Int?,
        totalDistanceM: Double,
        points: List<CircuitPoint>
    ) {
        if (!enabled) return

        val activeSessionId = sessionId ?: return
        val activeFirestore = initializeFirestoreIfNeeded() ?: return
        val sampledPoints = points
            .filter { point ->
                point.distance.isFinite() &&
                    point.lat.isFinite() &&
                    point.lon.isFinite()
            }
            .sampleForFirestore()
            .map { point ->
                mapOf(
                    "distanceM" to point.distance.toDouble(),
                    "lat" to point.lat,
                    "lon" to point.lon
                )
            }
        if (sampledPoints.isEmpty()) return

        val normalizedTrackName = normalizeTrackName(trackName)
        val normalizedTotalDistanceM = totalDistanceM
            .takeIf { it.isFinite() && it > 0.0 }
            ?: points.maxOfOrNull { it.distance.toDouble() }
            ?: 0.0
        val parentFields = mutableMapOf<String, Any>(
            "trackName" to normalizedTrackName,
            "trackPublished" to true,
            "trackPointCount" to sampledPoints.size,
            "trackTotalDistanceM" to normalizedTotalDistanceM
        )
        totalLaps?.let { parentFields["totalLaps"] = it }

        val sessionRef = activeFirestore
            .collection("raceSessions")
            .document(activeSessionId)
        val trackRef = sessionRef
            .collection("track")
            .document("current")
        val trackPath = "raceSessions/$activeSessionId/track/current"

        activeFirestore
            .batch()
            .set(sessionRef, parentFields, SetOptions.merge())
            .set(
                trackRef,
                mapOf(
                    "trackName" to normalizedTrackName,
                    "totalDistanceM" to normalizedTotalDistanceM,
                    "pointCount" to sampledPoints.size,
                    "points" to sampledPoints
                ),
                SetOptions.merge()
            )
            .commit()
            .addOnFailureListener { error ->
                recordError("Track data publish failed: ${error.message ?: error.javaClass.simpleName}")
                Log.w(TAG, "Track data publish failed: $trackPath", error)
            }
            .addOnSuccessListener {
                Log.i(
                    TAG,
                    "Track data published: $trackPath trackName=$normalizedTrackName " +
                        "pointCount=${sampledPoints.size} totalDistanceM=$normalizedTotalDistanceM"
                )
            }
    }

    fun publishStrategySegments(
        startSegments: List<StrategyIntervalUi>,
        raceSegments: List<StrategyIntervalUi>
    ) {
        if (!enabled) return

        val activeSessionId = sessionId ?: return
        val activeFirestore = initializeFirestoreIfNeeded() ?: return
        val path = "raceSessions/$activeSessionId/strategy/current"
        val strategyData = mapOf(
            "startSegments" to startSegments.toFirestoreSegments(),
            "raceSegments" to raceSegments.toFirestoreSegments()
        )

        activeFirestore
            .collection("raceSessions")
            .document(activeSessionId)
            .collection("strategy")
            .document("current")
            .set(strategyData, SetOptions.merge())
            .addOnFailureListener { error ->
                recordError("Strategy segments publish failed: ${error.message ?: error.javaClass.simpleName}")
                Log.w(TAG, "Strategy segments publish failed: $path", error)
            }
            .addOnSuccessListener {
                Log.i(
                    TAG,
                    "Strategy segments published: $path " +
                        "startSegments=${startSegments.size} raceSegments=${raceSegments.size}"
                )
            }
    }

    fun publishLatest(snapshot: PilotTelemetrySnapshot, nowMs: Long = System.currentTimeMillis()) {
        if (!enabled) return
        if (nowMs - lastWriteAtMs < TelemetryConfig.FIRESTORE_MIN_WRITE_INTERVAL_MS) return

        val activeSessionId = sessionId ?: return
        val activeFirestore = initializeFirestoreIfNeeded() ?: return
        lastWriteAtMs = nowMs

        activeFirestore
            .collection("raceSessions")
            .document(activeSessionId)
            .collection("telemetry")
            .document("latest")
            .set(snapshot.toFirestoreMap())
            .addOnFailureListener { error ->
                val message = error.message ?: error.javaClass.simpleName
                recordError(message)
                Log.w(TAG, "Telemetry Firestore write failed: $message", error)
            }
            .addOnSuccessListener {
                _status.value = _status.value.copy(
                    lastSuccessEpochMs = nowMs,
                    lastPublishedTimestampIso = snapshot.timestampIso,
                    writeCount = _status.value.writeCount + 1,
                    lastError = null
                )
                Log.d(TAG, "Telemetry Firestore latest updated for $activeSessionId")
            }
    }

    fun listenInstructions(
        sessionId: String,
        onInstructions: (RaceInstructions) -> Unit
    ) {
        if (!enabled) return

        val activeFirestore = initializeFirestoreIfNeeded() ?: return
        val path = "raceSessions/$sessionId/instructions/current"

        instructionsRegistration?.remove()
        instructionsRegistration = activeFirestore
            .collection("raceSessions")
            .document(sessionId)
            .collection("instructions")
            .document("current")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val message = "Instructions listener failed: ${error.message ?: error.javaClass.simpleName}"
                    recordError(message)
                    Log.w(TAG, "$message path=$path", error)
                    return@addSnapshotListener
                }

                val instructions = RaceInstructions.fromFirestore(snapshot?.data)
                onInstructions(instructions)
                Log.d(TAG, "Instructions updated from $path: $instructions")
            }

        Log.i(TAG, "Listening instructions: $path")
    }

    fun stopListeningInstructions() {
        instructionsRegistration?.remove()
        instructionsRegistration = null
    }

    private fun createSessionDocument(createdAtMs: Long) {
        if (!enabled) return

        val activeSessionId = sessionId ?: return
        val activeFirestore = initializeFirestoreIfNeeded() ?: return
        val path = "raceSessions/$activeSessionId"

        activeFirestore
            .collection("raceSessions")
            .document(activeSessionId)
            .set(
                mapOf(
                    "sessionId" to activeSessionId,
                    "createdAtIso" to metadataTimestamp.format(Date(createdAtMs)),
                    "status" to "WAITING",
                    "raceStarted" to false,
                    "appRole" to "PILOT",
                    "trackName" to "Unknown track"
                ),
                SetOptions.merge()
            )
            .addOnFailureListener { error ->
                recordError("Session document creation failed: ${error.message ?: error.javaClass.simpleName}")
                Log.w(TAG, "Session document creation failed: $path", error)
            }
            .addOnSuccessListener {
                Log.i(TAG, "Session document created : $path")
            }
    }

    private fun initializeFirestoreIfNeeded(): FirebaseFirestore? {
        if (!enabled) return null
        firestore?.let { return it }
        if (initializationAttempted) return null

        initializationAttempted = true
        return runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            if (FirebaseApp.getApps(context).isEmpty()) {
                val message = "Firebase is not initialized. Add app/google-services.json to enable Firestore telemetry."
                recordError(message)
                Log.w(TAG, message)
                null
            } else {
                FirebaseFirestore.getInstance().also {
                    firestore = it
                    Log.i(TAG, "Telemetry Firestore initialized.")
                }
            }
        }.onFailure { error ->
            val message = error.message ?: error.javaClass.simpleName
            recordError(message)
            Log.w(TAG, "Telemetry Firestore initialization failed: $message", error)
        }.getOrNull()
    }

    private fun recordError(message: String) {
        _status.value = _status.value.copy(
            errorCount = _status.value.errorCount + 1,
            lastError = message
        )
    }

    private fun normalizeTrackName(trackName: String?): String =
        trackName?.takeIf { it.isNotBlank() } ?: "Unknown track"

    private fun List<StrategyIntervalUi>.toFirestoreSegments(): List<Map<String, Any>> =
        filter { segment ->
            segment.startDistanceM.isFinite() &&
                segment.endDistanceM.isFinite() &&
                segment.endDistanceM > segment.startDistanceM
        }.map { segment ->
            mapOf(
                "startDistanceM" to segment.startDistanceM.toDouble(),
                "endDistanceM" to segment.endDistanceM.toDouble(),
                "color" to segment.buttonColor.orEmpty()
            )
        }

    private fun List<CircuitPoint>.sampleForFirestore(): List<CircuitPoint> {
        if (size <= MAX_TRACK_POINT_COUNT) return this

        val step = ceil(size.toDouble() / MAX_TRACK_POINT_COUNT).toInt().coerceAtLeast(1)
        val sampled = filterIndexed { index, _ -> index % step == 0 }.toMutableList()
        val lastPoint = last()
        if (sampled.lastOrNull() != lastPoint) {
            if (sampled.size >= MAX_TRACK_POINT_COUNT) {
                sampled.removeAt(sampled.lastIndex)
            }
            sampled.add(lastPoint)
        }
        return sampled
    }

    companion object {
        private const val TAG = "TelemetryFirestore"
        private const val MAX_TRACK_POINT_COUNT = 1000
        private val sessionTimestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        private val metadataTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
    }
}
