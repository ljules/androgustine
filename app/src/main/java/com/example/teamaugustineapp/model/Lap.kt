package com.example.teamaugustineapp.model

data class Lap(
    val lapNumber: Int = 0,
    val timestamp: String = "", // Temps de passage cumulé
    val duration: String = ""   // Temps du tour courant
) {
}