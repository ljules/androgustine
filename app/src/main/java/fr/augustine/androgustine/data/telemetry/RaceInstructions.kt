package fr.augustine.androgustine.data.telemetry

data class RaceInstructions(
    val pilotPaceInstruction: String = DEFAULT_PACE_INSTRUCTION,
    val raceStatusInstruction: String = DEFAULT_RACE_STATUS_INSTRUCTION,
    val pitStopRequest: Boolean = false,
    val updatedAtIso: String? = null,
    val updatedBy: String? = null
) {
    companion object {
        const val PACE_ACCELERATE = "ACCELERATE"
        const val PACE_MAINTAIN = "MAINTAIN"
        const val PACE_SLOW_DOWN = "SLOW_DOWN"

        const val STATUS_RACE = "RACE"
        const val STATUS_NO_OVERTAKING = "NO_OVERTAKING"
        const val STATUS_STOP = "STOP"

        const val DEFAULT_PACE_INSTRUCTION = PACE_MAINTAIN
        const val DEFAULT_RACE_STATUS_INSTRUCTION = STATUS_RACE

        val DEFAULT = RaceInstructions()

        fun fromFirestore(data: Map<String, Any?>?): RaceInstructions {
            if (data == null) return DEFAULT

            return RaceInstructions(
                pilotPaceInstruction = data["pilotPaceInstruction"]
                    .asAllowedString(
                        allowed = setOf(PACE_ACCELERATE, PACE_MAINTAIN, PACE_SLOW_DOWN),
                        defaultValue = DEFAULT_PACE_INSTRUCTION
                    ),
                raceStatusInstruction = data["raceStatusInstruction"]
                    .asAllowedString(
                        allowed = setOf(STATUS_RACE, STATUS_NO_OVERTAKING, STATUS_STOP),
                        defaultValue = DEFAULT_RACE_STATUS_INSTRUCTION
                    ),
                pitStopRequest = data["pitStopRequest"] as? Boolean ?: false,
                updatedAtIso = data["updatedAtIso"] as? String,
                updatedBy = data["updatedBy"] as? String
            )
        }

        private fun Any?.asAllowedString(allowed: Set<String>, defaultValue: String): String =
            (this as? String)
                ?.takeIf { value -> value in allowed }
                ?: defaultValue
    }
}
