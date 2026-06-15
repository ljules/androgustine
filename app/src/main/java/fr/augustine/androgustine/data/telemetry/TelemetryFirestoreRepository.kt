package fr.augustine.androgustine.data.telemetry

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
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

    fun startSession(startTimeMs: Long): String {
        sessionId = "androgustine-session-${sessionTimestamp.format(Date(startTimeMs))}"
        lastWriteAtMs = 0L
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
                Log.w(TAG, "Telemetry Firestore write failed: ${error.message}", error)
            }
            .addOnSuccessListener {
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
                Log.w(TAG, "Firebase is not initialized. Add app/google-services.json to enable Firestore telemetry.")
                null
            } else {
                FirebaseFirestore.getInstance().also {
                    firestore = it
                    Log.i(TAG, "Telemetry Firestore initialized.")
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "Telemetry Firestore initialization failed: ${error.message}", error)
        }.getOrNull()
    }

    companion object {
        private const val TAG = "TelemetryFirestore"
        private val sessionTimestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}
