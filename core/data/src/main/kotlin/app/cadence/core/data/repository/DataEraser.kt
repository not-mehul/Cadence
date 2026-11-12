package app.cadence.core.data.repository

/** "Delete all data". */
fun interface DataEraser {
    suspend fun eraseAll()
}
