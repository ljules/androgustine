package fr.augustine.androgustine.data.imports

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.decodeFromJsonElement
import fr.augustine.androgustine.data.CircuitPoint
import fr.augustine.androgustine.data.GhostPoint
import fr.augustine.androgustine.data.StrategyIntervalUi

@Serializable
data class SimAugustineImport(
    val schemaVersion: String? = null,
    val appName: String? = null,
    val createdAt: String? = null,
    val units: JsonElement? = null,
    val circuit: SimAugustineCircuit? = null,
    val session: SimAugustineSession? = null,
    val simulation: SimAugustineSimulation? = null,
    val ghost: SimAugustineGhost? = null,
    val vehicle: JsonElement? = null,
    val metadata: JsonElement? = null,
    val compatibility: JsonElement? = null
)

@Serializable
data class SimAugustineCircuit(
    val name: String? = null,
    val distanceM: Double? = null,
    val points: List<SimAugustineCircuitPoint>? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SimAugustineCircuitPoint(
    val lat: Double? = null,
    val lon: Double? = null,
    @JsonNames("sM")
    val distanceM: Double? = null,
    val utmX: Double? = null,
    val utmY: Double? = null
)

@Serializable
data class SimAugustineSession(
    val totalLaps: Int? = null,
    val remainingRaceLaps: Int? = null,
    @Serializable(with = NullableStrategySerializer::class)
    val startLapStrategy: SimAugustineStrategy? = null,
    @Serializable(with = NullableStrategySerializer::class)
    val raceLapStrategy: SimAugustineStrategy? = null
)

@Serializable
data class SimAugustineStrategy(
    val pwmOn: Double? = null,
    val vInitKmh: Double? = null,
    val defaultDtSlopeS: Double? = null,
    val defaultButtonColor: String? = null,
    val intervals: List<SimAugustineStrategyInterval> = emptyList()
)

@Serializable
data class SimAugustineStrategyInterval(
    val startDistanceM: Double? = null,
    val endDistanceM: Double? = null,
    val dtSlopeS: Double? = null,
    val pwmTarget: Double? = null,
    val buttonColor: String? = null
)

object NullableStrategySerializer : KSerializer<SimAugustineStrategy?> {
    override val descriptor: SerialDescriptor = SimAugustineStrategy.serializer().descriptor

    override fun deserialize(decoder: Decoder): SimAugustineStrategy? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("NullableStrategySerializer requires JSON decoding.")
        val element = jsonDecoder.decodeJsonElement()
        if (element !is JsonObject) {
            return null
        }
        return jsonDecoder.json.decodeFromJsonElement(SimAugustineStrategy.serializer(), element)
    }

    override fun serialize(encoder: Encoder, value: SimAugustineStrategy?) {
        throw SerializationException("SimAugustineStrategy import serialization is not supported.")
    }
}

@Serializable
data class SimAugustineSimulation(
    val startLapResult: SimAugustineLapResult? = null,
    val raceLapResult: SimAugustineLapResult? = null,
    val sessionResult: SimAugustineSessionResult? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SimAugustineLapResult(
    @JsonNames("durationS", "lapTimeS", "totalTimeS")
    val timeS: Double? = null,
    @JsonNames("totalEnergyJ")
    val energyJ: Double? = null,
    val totalDistanceM: Double? = null,
    val averageSpeedMps: Double? = null,
    val initialSpeedMps: Double? = null,
    val finalSpeedMps: Double? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SimAugustineSessionResult(
    @JsonNames("totalEnergyJ", "energyJ")
    val energyJ: Double? = null,
    @JsonNames("durationS", "timeS")
    val totalTimeS: Double? = null,
    val totalDistanceM: Double? = null,
    val averageSpeedMps: Double? = null
)

@Serializable
data class SimAugustineGhost(
    val sampling: JsonElement? = null,
    val startLap: List<SimAugustineGhostPoint>? = null,
    val raceLap: List<SimAugustineGhostPoint>? = null
)

@Serializable
data class SimAugustineGhostPoint(
    val timeS: Double? = null,
    val distanceM: Double? = null,
    val speedMps: Double? = null,
    val pwm: Double? = null,
    val currentA: Double? = null,
    val energyJ: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val utmX: Double? = null,
    val utmY: Double? = null
)

data class SimAugustineImportSummary(
    val schemaVersion: String,
    val circuitName: String,
    val circuitDistanceM: Double,
    val circuitPointCount: Int,
    val circuitSource: String,
    val totalLaps: Int,
    val remainingRaceLaps: Int,
    val startLapTimeS: Double,
    val raceLapTimeS: Double,
    val sessionEnergyJ: Double,
    val startGhostPointCount: Int,
    val raceGhostPointCount: Int,
    val startStrategyIntervalCount: Int,
    val raceStrategyIntervalCount: Int,
    val displayedStrategyName: String?,
    val displayedStrategyIntervalCount: Int,
    val firstCanvasPointDistanceM: Float,
    val lastCanvasPointDistanceM: Float,
    val minCanvasPointDistanceM: Float,
    val maxCanvasPointDistanceM: Float
)

data class SimAugustineImportedCircuit(
    val points: List<CircuitPoint>,
    val sourceLabel: String,
    val totalLaps: Int,
    val startStrategyIntervals: List<StrategyIntervalUi>,
    val raceStrategyIntervals: List<StrategyIntervalUi>,
    val startGhostPoints: List<GhostPoint>,
    val raceGhostPoints: List<GhostPoint>
)
