package app.cadence.core.data.di

import app.cadence.core.data.repository.DataEraser
import app.cadence.core.data.repository.DayEntryRepository
import app.cadence.core.data.repository.PeriodRepository
import app.cadence.core.data.repository.RoomDataEraser
import app.cadence.core.data.repository.RoomDayEntryRepository
import app.cadence.core.data.repository.RoomPeriodRepository
import app.cadence.core.data.repository.RoomSettingsRepository
import app.cadence.core.data.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindPeriodRepository(impl: RoomPeriodRepository): PeriodRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: RoomSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindDayEntryRepository(impl: RoomDayEntryRepository): DayEntryRepository

    @Binds
    abstract fun bindDataEraser(impl: RoomDataEraser): DataEraser
}
