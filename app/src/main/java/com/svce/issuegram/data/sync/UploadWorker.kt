package com.svce.issuegram.data.sync

import android.content.Context
import androidx.work.*
import com.svce.issuegram.data.repository.IssuegramRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (IssuegramRepository.get(applicationContext).syncOutbox()) Result.success() else Result.retry()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { Result.retry() }

    companion object {
        fun schedule(context: Context) {
            val work = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("issuegram-outbox", ExistingWorkPolicy.APPEND_OR_REPLACE, work)
        }
    }
}
