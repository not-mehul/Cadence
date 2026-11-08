package app.cadence.core.data.repository

/** "Delete all data" (docs/PLAN.md §2.7). */
fun interface DataEraser {
    suspend fun eraseAll()
}
