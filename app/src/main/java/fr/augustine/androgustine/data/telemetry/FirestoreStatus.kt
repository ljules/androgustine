package fr.augustine.androgustine.data.telemetry

data class FirestoreStatus(
    val enabled: Boolean,
    val sessionId: String? = null,
    val lastSuccessEpochMs: Long? = null,
    val lastPublishedTimestampIso: String? = null,
    val writeCount: Int = 0,
    val errorCount: Int = 0,
    val lastError: String? = null
)
