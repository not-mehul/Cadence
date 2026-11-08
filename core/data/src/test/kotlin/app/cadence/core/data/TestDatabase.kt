package app.cadence.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cadence.core.data.db.CadenceDatabase
import dagger.Lazy

internal fun inMemoryDatabase(): CadenceDatabase =
    Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CadenceDatabase::class.java)
        .allowMainThreadQueries()
        .build()

internal fun CadenceDatabase.asLazy(): Lazy<CadenceDatabase> = Lazy { this }
