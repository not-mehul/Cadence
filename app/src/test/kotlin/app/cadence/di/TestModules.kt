package app.cadence.di

import android.content.Context
import androidx.room.Room
import app.cadence.core.data.db.CadenceDatabase
import app.cadence.core.data.di.DatabaseModule
import app.cadence.core.data.di.TimeModule
import app.cadence.core.data.time.DateSource
import app.cadence.core.data.time.LocalNow
import app.cadence.core.domain.time.TodayProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Singleton

/** Robolectric has no Keystore or SQLCipher, so tests use a plain in-memory database. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object InMemoryDatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): CadenceDatabase = Room.inMemoryDatabaseBuilder(context, CadenceDatabase::class.java).build()
}

/** Pins "today" so tests are deterministic. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [TimeModule::class])
object FixedTimeModule {
    val TODAY: LocalDate = LocalDate.of(2026, 3, 20)

    @Provides
    fun provideClock(): Clock = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    @Provides
    fun provideTodayProvider(): TodayProvider = TodayProvider { TODAY }

    /** Noon on [TODAY]. */
    @Provides
    fun provideLocalNow(): LocalNow = LocalNow { TODAY.atTime(12, 0) }

    @Provides
    fun provideDateSource(): DateSource =
        object : DateSource {
            override val today = flowOf(TODAY)
        }
}
