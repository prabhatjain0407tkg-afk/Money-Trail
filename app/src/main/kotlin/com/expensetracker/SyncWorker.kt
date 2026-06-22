package com.expensetracker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Periodic background worker (every 15 minutes via WorkManager).
 * Sets a "refresh needed" flag in SharedPreferences so that
 * MainActivity reloads SMS data the next time the app comes to foreground.
 */
class SyncWorker(appContext: Context, params: WorkerParameters)
    : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        applicationContext
            .getSharedPreferences("sync_state", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_bg_sync", System.currentTimeMillis())
            .apply()
        return Result.success()
    }
}
