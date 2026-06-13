package com.example.teamaugustineapp.model

data class RaceState(
    // Timing (géré par le Chrono)
    val currentLap: Int = 1,
    val totalLaps: Int = 11,
    val totalTime: String = "00:00",

    // Télémétrie (gérée par le Pilote)
    val speed: Float = 0.0f,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,

    // Statégie (gérée par le Copilote)
    val instruction: String = "MAINTENIR",  // ACCELERER, MAINTENIR, RALENTIR
    val flag: String = "COURSE",            // NE_PAS_DOUBLER, STOP, COURSE
    val targetAction: String = "NONE"       // SORTIR, etc.
) {

}