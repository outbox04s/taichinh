package vn.personalfinance.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions

@HiltWorker
class BankSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val supabase: SupabaseClient,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        supabase.functions.invoke("sepay-sync")
        Result.success()
    }.getOrElse { Result.retry() }
}
