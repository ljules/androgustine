package com.example.teamaugustineapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.teamaugustineapp.data.CircuitPoint
import com.example.teamaugustineapp.ui.theme.ShellOrange
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

@Composable
fun CircuitView(
    points: List<CircuitPoint>,
    currentLat: Double,
    currentLon: Double,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    Canvas(modifier = modifier.padding(16.dp)) {
        val minX = points.minOf { it.utmX }
        val maxX = points.maxOf { it.utmX }
        val minY = points.minOf { it.utmY }
        val maxY = points.maxOf { it.utmY }

        val deltaX = maxX - minX
        val deltaY = maxY - minY

        // Calcul de l'échelle pour adapter l'UTM aux Pixels de l'écran :
        val scale = min(size.width / deltaX, size.height / deltaY).toFloat()

        // Calcul du décalage pour CENTRER le circuit dans le Canvas :
        val offsetX = (size.width - (deltaX * scale)) / 2f
        val offsetY = (size.height - (deltaY * scale)) / 2f

        // Fonction utilitaire pour transformer UTM -> Coordonnées Canvas
        fun getCanvasCoords(utmX: Double, utmY: Double): Offset {
            val x = ((utmX - minX) * scale).toFloat() + offsetX.toFloat()
            // Inversion de l'axe Y pour Android (0 est en haut)
            val y = (size.height - ((utmY - minY) * scale)).toFloat() - offsetY.toFloat()
            return Offset(x, y)
        }


        // DESSIN DE LA PISTE (Blanc, épaisseur doublée) :
        val path = Path()
        points.forEachIndexed { index, point ->
            val coords = getCanvasCoords(point.utmX, point.utmY)
            if (index == 0) path.moveTo(coords.x, coords.y)
            else path.lineTo(coords.x, coords.y)
        }
        // On ferme le circuit s'il ne l'est pas :
        path.close()

        drawPath(
            path = path,
            color = Color.White,
            style = Stroke(width = 12f) // Piste plus épaisse
        )

        // AIMANTATION ET DESSIN DE LA PILOTE :
        // On cherche le point du CSV le plus proche de la position GPS réelle
        val closestPoint = points.minByOrNull { pt ->
            sqrt((pt.lat - currentLat).pow(2.0) + (pt.lon - currentLon).pow(2.0))
        }

        closestPoint?.let { pt ->
            val carCoords = getCanvasCoords(pt.utmX, pt.utmY)

            // Halo de visibilité
            drawCircle(
                color = Color.White.copy(alpha = 0.5f),
                radius = 18f,
                center = carCoords
            )
            // Point Pilote Orange (ShellOrange)
            drawCircle(
                color = ShellOrange,
                radius = 12f,
                center = carCoords
            )
        }

    }
}