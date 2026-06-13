package fr.augustine.androgustine.data.timer

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TimeService {
    fun timerFlow(): Flow<Long> = flow {
        var seconds = 0L
        while (true) {
            emit(seconds)
            delay(1000)
            seconds++
        }
    }

    fun formatSeconds(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
