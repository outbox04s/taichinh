package vn.personalfinance

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import java.util.concurrent.TimeUnit
import vn.personalfinance.data.sync.DebtReminderWorker

@HiltAndroidApp
class FinanceApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
    override fun onCreate(){super.onCreate();WorkManager.getInstance(this).enqueueUniquePeriodicWork("debt-reminders",ExistingPeriodicWorkPolicy.UPDATE,PeriodicWorkRequestBuilder<DebtReminderWorker>(24,TimeUnit.HOURS).build())}
}
