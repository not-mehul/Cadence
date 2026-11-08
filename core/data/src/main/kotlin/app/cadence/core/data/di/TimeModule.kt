package app.cadence.core.data.di

import app.cadence.core.data.time.DateSource
import app.cadence.core.data.time.LocalNow
import app.cadence.core.data.time.SystemDateSource
import app.cadence.core.domain.time.TodayProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.LocalDateTime

/** The clock and "today". Tests replace this module to pin the date. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {
    @Binds
    abstract fun bindDateSource(impl: SystemDateSource): DateSource

    companion object {
        @Provides
        fun provideClock(): Clock = Clock.systemUTC()

        @Provides
        fun provideTodayProvider(): TodayProvider = TodayProvider.System

        @Provides
        fun provideLocalNow(): LocalNow = LocalNow { LocalDateTime.now() }
    }
}
