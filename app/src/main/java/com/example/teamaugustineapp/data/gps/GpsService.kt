package com.example.teamaugustineapp.data.gps

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Structure de données regroupant les informations nécessaires du GPS.
 */
data class GpsData(
    val speed: Float,
    val latitude: Double,
    val longitude: Double
)

class GpsService(context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun getGpsUpdates(): Flow<GpsData> = callbackFlow {

        // Configuration optimisée pour la course (Haute précision)
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000 // Update toutes les secondes
        ).apply {
            setMinUpdateIntervalMillis(500)    // Autorise des mises à jour jusqu'à 0.5s si dispo
            setMinUpdateDistanceMeters(0f)     // Ne pas ignorer les petits déplacements
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    // Conversion m/s vers km/h
                    val speedKmH = if (location.hasSpeed()) location.speed * 3.6f else 0.0f

                    // Envoi des données groupées
                    trySend(
                        GpsData(
                            speed = speedKmH,
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                    )
                }
            }
        }

        // Démarrage de l'écoute
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        // Fermeture propre du flux (nettoyage lors de l'arrêt du ViewModel)
        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}