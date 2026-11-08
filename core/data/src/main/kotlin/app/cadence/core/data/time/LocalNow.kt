package app.cadence.core.data.time

import java.time.LocalDateTime

/** The current wall-clock time in the device's timezone (read on every call). */
fun interface LocalNow {
    fun now(): LocalDateTime
}
