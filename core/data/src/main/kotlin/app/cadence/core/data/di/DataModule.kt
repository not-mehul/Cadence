package app.cadence.core.data.di

import android.content.Context
import app.cadence.core.data.db.CadenceDatabase
import app.cadence.core.data.db.CadenceDatabaseFactory
import app.cadence.core.data.db.DayLogDao
import app.cadence.core.data.security.DatabasePassphraseStore
import app.cadence.core.data.security.KeystoreKeyWrapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    private const val KEY_FILE_NAME = "cadence_db.key"

    @Provides
    @Singleton
    fun providePassphraseStore(
        @ApplicationContext context: Context,
    ): DatabasePassphraseStore =
        DatabasePassphraseStore(
            // noBackupFilesDir is excluded from every Android backup mechanism.
            file = File(context.noBackupFilesDir, KEY_FILE_NAME),
            wrapper = KeystoreKeyWrapper(),
        )

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        passphraseStore: DatabasePassphraseStore,
    ): CadenceDatabase {
        val passphrase = passphraseStore.getOrCreate()
        return try {
            CadenceDatabaseFactory.create(context, passphrase)
        } finally {
            passphrase.fill(0)
        }
    }

    @Provides
    fun provideDayLogDao(database: CadenceDatabase): DayLogDao = database.dayLogDao()
}
