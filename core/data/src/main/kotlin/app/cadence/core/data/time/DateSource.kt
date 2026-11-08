package app.cadence.core.data.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import app.cadence.core.domain.time.TodayProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate
import javax.inject.Inject

/** Today's date, updated at midnight and when the user changes the time or timezone. */
interface DateSource {
    val today: Flow<LocalDate>
}

class SystemDateSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val todayProvider: TodayProvider,
    ) : DateSource {
        override val today: Flow<LocalDate> =
            callbackFlow {
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            context: Context,
                            intent: Intent,
                        ) {
                            trySend(todayProvider.today())
                        }
                    }
                val filter =
                    IntentFilter().apply {
                        addAction(Intent.ACTION_DATE_CHANGED)
                        addAction(Intent.ACTION_TIME_CHANGED)
                        addAction(Intent.ACTION_TIMEZONE_CHANGED)
                    }
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                trySend(todayProvider.today())
                awaitClose { context.unregisterReceiver(receiver) }
            }.distinctUntilChanged()
    }
