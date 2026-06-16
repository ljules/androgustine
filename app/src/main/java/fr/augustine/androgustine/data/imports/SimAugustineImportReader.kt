package fr.augustine.androgustine.data.imports

import android.content.ContentResolver
import android.net.Uri
import fr.augustine.androgustine.data.CircuitPoint
import fr.augustine.androgustine.data.GhostPoint
import fr.augustine.androgustine.data.StrategyIntervalUi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val simAugustineJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun readSimAugustineImport(contentResolver: ContentResolver, uri: Uri): SimAugustineImportSummary {
    val jsonText = contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
        reader?.readText() ?: throw SimAugustineImportException("Impossible d'ouvrir le fichier selectionne.")
    }

    try {
        val root = simAugustineJson.parseToJsonElement(jsonText)
        if (root !is JsonObject) {
            throw SimAugustineImportException("JSON non valide : la racine doit etre un objet.")
        }
    } catch (error: SerializationException) {
        throw SimAugustineImportException(
            "JSON non valide : ${error.message ?: "syntaxe JSON incorrecte"}.",
            error
        )
    }

    val sessionImport = try {
        simAugustineJson.decodeFromString<SimAugustineImport>(jsonText)
    } catch (error: SerializationException) {
        throw SimAugustineImportException(
            "Erreur de deserialisation Sim-Augustine : ${error.message ?: "type inattendu ou structure incompatible"}.",
            error
        )
    } catch (error: IllegalArgumentException) {
        throw SimAugustineImportException(
            "Erreur de format Sim-Augustine : ${error.message ?: "structure incompatible"}.",
            error
        )
    }

    val importedCircuit = sessionImport.toImportedCircuit()
    val summary = sessionImport.toSummary(importedCircuit)
    SimAugustineImportRepository.save(sessionImport, importedCircuit)
    return summary
}

private fun SimAugustineImport.toSummary(
    importedCircuit: SimAugustineImportedCircuit
): SimAugustineImportSummary {
    val schemaVersion = requireText(schemaVersion, "schemaVersion")
    val circuit = requireBlock(circuit, "circuit")
    val session = requireBlock(session, "session")
    val simulation = requireBlock(simulation, "simulation")
    val ghostBlock = ghost
    val circuitName = requireText(circuit.name, "circuit.name")
    val circuitDistanceM = requireValue(circuit.distanceM, "circuit.distanceM")
    val circuitPoints = requireList(circuit.points, "circuit.points")
    val totalLaps = session.totalLaps
    val remainingRaceLaps = requireValue(session.remainingRaceLaps, "session.remainingRaceLaps")
    val startLapResult = requireBlock(simulation.startLapResult, "simulation.startLapResult")
    val raceLapResult = requireBlock(simulation.raceLapResult, "simulation.raceLapResult")
    val sessionResult = requireBlock(simulation.sessionResult, "simulation.sessionResult")
    val startGhost = ghostBlock?.startLap.orEmpty()
    val raceGhost = ghostBlock?.raceLap.orEmpty()
    val canvasDistances = importedCircuit.points.map { it.distance }

    if (circuitPoints.isEmpty()) {
        throw SimAugustineImportException("Import incomplet : circuit.points est vide.")
    }
    return SimAugustineImportSummary(
        schemaVersion = schemaVersion,
        circuitName = circuitName,
        circuitDistanceM = circuitDistanceM,
        circuitPointCount = circuitPoints.size,
        circuitSource = importedCircuit.sourceLabel,
        totalLaps = totalLaps,
        remainingRaceLaps = remainingRaceLaps,
        startLapTimeS = requireValue(startLapResult.timeS, "simulation.startLapResult.totalTimeS"),
        raceLapTimeS = requireValue(raceLapResult.timeS, "simulation.raceLapResult.totalTimeS"),
        sessionEnergyJ = requireValue(sessionResult.energyJ, "simulation.sessionResult.totalEnergyJ"),
        startGhostPointCount = startGhost.size,
        raceGhostPointCount = raceGhost.size,
        startStrategyIntervalCount = session.startLapStrategy?.intervals?.size ?: 0,
        raceStrategyIntervalCount = session.raceLapStrategy?.intervals?.size ?: 0,
        displayedStrategyName = "tour depart",
        displayedStrategyIntervalCount = importedCircuit.startStrategyIntervals.size,
        firstCanvasPointDistanceM = importedCircuit.points.first().distance,
        lastCanvasPointDistanceM = importedCircuit.points.last().distance,
        minCanvasPointDistanceM = canvasDistances.minOrNull() ?: 0f,
        maxCanvasPointDistanceM = canvasDistances.maxOrNull() ?: 0f
    )
}

private fun SimAugustineImport.toImportedCircuit(): SimAugustineImportedCircuit {
    val circuit = requireBlock(circuit, "circuit")
    val session = requireBlock(session, "session")
    val points = requireList(circuit.points, "circuit.points")
    val totalLaps = session.totalLaps
    val trackName = circuit.name?.takeIf { it.isNotBlank() } ?: "Unknown track"
    if (points.isEmpty()) {
        throw SimAugustineImportException("Import incomplet : circuit.points est vide.")
    }

    return SimAugustineImportedCircuit(
        points = points.mapIndexed { index, point ->
            point.toCircuitPoint(index)
        },
        sourceLabel = "JSON Sim-Augustine",
        totalLaps = totalLaps,
        trackName = trackName,
        startStrategyIntervals = session.startLapStrategy?.intervals.orEmpty()
            .mapNotNull { it.toStrategyIntervalUi() },
        raceStrategyIntervals = session.raceLapStrategy?.intervals.orEmpty()
            .mapNotNull { it.toStrategyIntervalUi() },
        startGhostPoints = sessionGhostStartPoints(),
        raceGhostPoints = sessionGhostRacePoints()
    )
}

private fun SimAugustineImport.sessionGhostStartPoints(): List<GhostPoint> =
    ghost?.startLap.orEmpty().mapNotNull { it.toGhostPoint() }.sortedBy { it.timeS }

private fun SimAugustineImport.sessionGhostRacePoints(): List<GhostPoint> =
    ghost?.raceLap.orEmpty().mapNotNull { it.toGhostPoint() }.sortedBy { it.timeS }

private fun SimAugustineCircuitPoint.toCircuitPoint(index: Int): CircuitPoint {
    val lat = requireValue(lat, "circuit.points[$index].lat")
    val lon = requireValue(lon, "circuit.points[$index].lon")

    return CircuitPoint(
        distance = requireValue(distanceM, "circuit.points[$index].sM").toFloat(),
        elevation = 0f,
        utmX = utmX ?: lon,
        utmY = utmY ?: lat,
        lon = lon,
        lat = lat
    )
}

private fun SimAugustineStrategyInterval.toStrategyIntervalUi(): StrategyIntervalUi? {
    val startDistance = startDistanceM ?: return null
    val endDistance = endDistanceM ?: return null
    if (endDistance <= startDistance) {
        return null
    }
    return StrategyIntervalUi(
        startDistanceM = startDistance.toFloat(),
        endDistanceM = endDistance.toFloat(),
        buttonColor = buttonColor
    )
}

private fun SimAugustineGhostPoint.toGhostPoint(): GhostPoint? {
    val time = timeS ?: return null
    val distance = distanceM ?: return null
    val pointLat = lat ?: return null
    val pointLon = lon ?: return null
    return GhostPoint(
        timeS = time,
        distanceM = distance.toFloat(),
        utmX = utmX ?: pointLon,
        utmY = utmY ?: pointLat,
        lon = pointLon,
        lat = pointLat
    )
}

private fun <T> requireBlock(value: T?, path: String): T =
    value ?: throw SimAugustineImportException("Champ manquant : $path.")

private fun <T> requireValue(value: T?, path: String): T =
    value ?: throw SimAugustineImportException("Champ manquant : $path.")

private fun requireText(value: String?, path: String): String =
    value?.takeIf { it.isNotBlank() }
        ?: throw SimAugustineImportException("Champ manquant : $path.")

private fun <T> requireList(value: List<T>?, path: String): List<T> =
    value ?: throw SimAugustineImportException("Champ manquant : $path.")

class SimAugustineImportException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
