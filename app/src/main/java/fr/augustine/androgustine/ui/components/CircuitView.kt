package fr.augustine.androgustine.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import fr.augustine.androgustine.data.CircuitPoint
import fr.augustine.androgustine.data.GhostPoint
import fr.augustine.androgustine.data.StrategyIntervalUi
import fr.augustine.androgustine.ui.theme.ShellOrange
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

@Composable
fun CircuitView(
    points: List<CircuitPoint>,
    currentLat: Double,
    currentLon: Double,
    strategyIntervals: List<StrategyIntervalUi> = emptyList(),
    ghostPoint: GhostPoint? = null,
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
        val scale = min(size.width / deltaX, size.height / deltaY).toFloat()
        val offsetX = (size.width - (deltaX * scale)) / 2f
        val offsetY = (size.height - (deltaY * scale)) / 2f

        fun getCanvasCoords(utmX: Double, utmY: Double): Offset {
            val x = ((utmX - minX) * scale).toFloat() + offsetX.toFloat()
            val y = (size.height - ((utmY - minY) * scale)).toFloat() - offsetY.toFloat()
            return Offset(x, y)
        }

        val path = Path()
        points.forEachIndexed { index, point ->
            val coords = getCanvasCoords(point.utmX, point.utmY)
            if (index == 0) path.moveTo(coords.x, coords.y) else path.lineTo(coords.x, coords.y)
        }
        path.close()

        drawPath(
            path = path,
            color = Color.White,
            style = Stroke(width = 18f)
        )

        points.zipWithNext().forEach { (startPoint, endPoint) ->
            val segmentDistance = (startPoint.distance + endPoint.distance) / 2f
            val matchingInterval = strategyIntervals.firstOrNull { interval ->
                segmentDistance >= interval.startDistanceM &&
                    segmentDistance <= interval.endDistanceM
            }

            if (matchingInterval != null) {
                drawLine(
                    color = matchingInterval.buttonColor.toStrategyColor(),
                    start = getCanvasCoords(startPoint.utmX, startPoint.utmY),
                    end = getCanvasCoords(endPoint.utmX, endPoint.utmY),
                    strokeWidth = 24f
                )
            }
        }

        ghostPoint?.let { point ->
            val ghostCoords = getCanvasCoords(point.utmX, point.utmY)
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.35f),
                radius = 40f,
                center = ghostCoords
            )
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = 20f,
                center = ghostCoords
            )
        }

        val closestPoint = points.minByOrNull { pt ->
            sqrt((pt.lat - currentLat).pow(2.0) + (pt.lon - currentLon).pow(2.0))
        }

        closestPoint?.let { point ->
            val carCoords = getCanvasCoords(point.utmX, point.utmY)
            drawCircle(
                color = Color.White.copy(alpha = 0.5f),
                radius = 36f,
                center = carCoords
            )
            drawCircle(
                color = ShellOrange,
                radius = 24f,
                center = carCoords
            )
        }
    }
}

private fun String?.toStrategyColor(): Color = when (this?.lowercase()) {
    "green" -> Color(0xFF2ECC71)
    "yellow" -> Color(0xFFFFD43B)
    "blue" -> Color(0xFF4D96FF)
    "orange" -> Color(0xFFFF8C00)
    "red" -> Color(0xFFFF4D4D)
    else -> Color(0xFFB8E986)
}
