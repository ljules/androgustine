package fr.augustine.androgustine.data.imports

import android.content.ContentResolver
import android.net.Uri
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

    val summary = sessionImport.toSummary()
    SimAugustineImportRepository.save(sessionImport)
    return summary
}

private fun SimAugustineImport.toSummary(): SimAugustineImportSummary {
    val schemaVersion = requireText(schemaVersion, "schemaVersion")
    val circuit = requireBlock(circuit, "circuit")
    val session = requireBlock(session, "session")
    val simulation = requireBlock(simulation, "simulation")
    val ghost = requireBlock(ghost, "ghost")
    val circuitName = requireText(circuit.name, "circuit.name")
    val circuitDistanceM = requireValue(circuit.distanceM, "circuit.distanceM")
    val circuitPoints = requireList(circuit.points, "circuit.points")
    val totalLaps = requireValue(session.totalLaps, "session.totalLaps")
    val remainingRaceLaps = requireValue(session.remainingRaceLaps, "session.remainingRaceLaps")
    val startLapResult = requireBlock(simulation.startLapResult, "simulation.startLapResult")
    val raceLapResult = requireBlock(simulation.raceLapResult, "simulation.raceLapResult")
    val sessionResult = requireBlock(simulation.sessionResult, "simulation.sessionResult")
    val startGhost = requireList(ghost.startLap, "ghost.startLap")
    val raceGhost = requireList(ghost.raceLap, "ghost.raceLap")

    if (circuitPoints.isEmpty()) {
        throw SimAugustineImportException("Import incomplet : circuit.points est vide.")
    }
    if (startGhost.isEmpty()) {
        throw SimAugustineImportException("Import incomplet : ghost.startLap est vide.")
    }
    if (raceGhost.isEmpty()) {
        throw SimAugustineImportException("Import incomplet : ghost.raceLap est vide.")
    }

    return SimAugustineImportSummary(
        schemaVersion = schemaVersion,
        circuitName = circuitName,
        circuitDistanceM = circuitDistanceM,
        circuitPointCount = circuitPoints.size,
        totalLaps = totalLaps,
        remainingRaceLaps = remainingRaceLaps,
        startLapTimeS = requireValue(startLapResult.timeS, "simulation.startLapResult.totalTimeS"),
        raceLapTimeS = requireValue(raceLapResult.timeS, "simulation.raceLapResult.totalTimeS"),
        sessionEnergyJ = requireValue(sessionResult.energyJ, "simulation.sessionResult.totalEnergyJ"),
        startGhostPointCount = startGhost.size,
        raceGhostPointCount = raceGhost.size,
        startStrategyIntervalCount = session.startLapStrategy?.intervals?.size ?: 0,
        raceStrategyIntervalCount = session.raceLapStrategy?.intervals?.size ?: 0
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
