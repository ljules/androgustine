package fr.augustine.androgustine.data

import android.content.Context
import android.util.Log
import java.io.File

class CircuitManager(private val context: Context) {

    // Vérifiez bien que ce nom correspond exactement à celui appelé dans le ViewModel
    fun loadCircuitFromInternalStorage(): List<CircuitPoint> {
        val points = mutableListOf<CircuitPoint>()

        // Dossier : /sdcard/Android/data/fr.augustine.androgustine/files/
        val internalFolder = context.getExternalFilesDir(null)
        val file = File(internalFolder, "circuitShell.csv")

        if (!file.exists()) {
            Log.e("CircuitManager", "Fichier introuvable dans : ${file.absolutePath}")
            return emptyList()
        }

        try {
            file.bufferedReader().use { reader ->
                reader.readLine() // Saut de l'en-tête
                reader.forEachLine { line ->
                    if (line.isNotBlank()) {
                        val tokens = line.split(",")
                        if (tokens.size >= 6) {
                            points.add(
                                CircuitPoint(
                                    distance = tokens[0].trim().toFloat(),
                                    elevation = tokens[1].trim().toFloat(),
                                    utmX = tokens[2].trim().toDouble(),
                                    utmY = tokens[3].trim().toDouble(),
                                    lon = tokens[4].trim().toDouble(),
                                    lat = tokens[5].trim().toDouble()
                                )
                            )
                        }
                    }
                }
            }
            Log.d("CircuitManager", "Succès ! ${points.size} points chargés.")
        } catch (e: Exception) {
            Log.e("CircuitManager", "Erreur lecture : ${e.message}")
        }
        return points
    }
}
