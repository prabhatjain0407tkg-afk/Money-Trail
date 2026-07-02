package com.expensetracker

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DriveBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        if (!DriveBackupManager.isConnected(applicationContext)) return Result.failure()
        return runCatching {
            val json = UserPrefsStore.exportAllToJson(applicationContext)
            if (DriveBackupManager.upload(applicationContext, json)) {
                UserPrefsStore.saveLastBackupTime(applicationContext, System.currentTimeMillis())
                Result.success()
            } else {
                Result.retry()
            }
        }.getOrDefault(Result.retry())
    }

    companion object {
        private const val WORK_NAME = "drive_daily_backup"

        fun schedule(context: Context) {
            val now    = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
            }
            val delayMs = target.timeInMillis - now.timeInMillis

            val req = PeriodicWorkRequestBuilder<DriveBackupWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<DriveBackupWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("drive_manual_backup")
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}
