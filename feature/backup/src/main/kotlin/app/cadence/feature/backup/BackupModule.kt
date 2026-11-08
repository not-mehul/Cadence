package app.cadence.feature.backup

import android.content.Context
import android.net.Uri
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.InputStream
import java.io.OutputStream

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {
    @Provides
    fun provideFileAccess(
        @ApplicationContext context: Context,
    ): FileAccess =
        object : FileAccess {
            // "wt" truncates, so overwriting an existing file leaves no old bytes behind.
            override fun openOutput(uri: Uri): OutputStream? = context.contentResolver.openOutputStream(uri, "wt")

            override fun openInput(uri: Uri): InputStream? = context.contentResolver.openInputStream(uri)
        }
}
