package fr.augustine.androgustine.data.telemetry

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _status = MutableStateFlow(FirestoreStatus(enabled = enabled))
    val status: StateFlow<FirestoreStatus> = _status.asStateFlow()

    fun startSession(startTimeMs: Long): String {
        if (sessionId == null) {
            sessionId = "androgustine-session-${sessionTimestamp.format(Date(startTimeMs))}"
            lastWriteAtMs = 0L
        }
        _status.value = _status.value.copy(sessionId = sessionId)
        Log.i(TAG, "Telemetry Firestore sessionId=$sessionId")
        initializeFirestoreIfNeeded()
        return sessionId.orEmpty()
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
                _status.value = _status.value.copy(
                    errorCount = _status.value.errorCount + 1,
                    lastError = message
                )
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
                _status.value = _status.value.copy(
                    errorCount = _status.value.errorCount + 1,
                    lastError = message
                )
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
            _status.value = _status.value.copy(
                errorCount = _status.value.errorCount + 1,
                lastError = message
            )
            Log.w(TAG, "Telemetry Firestore initialization failed: $message", error)
        }.getOrNull()
    }

    companion object {
        private const val TAG = "TelemetryFirestore"
        private val sessionTimestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}
