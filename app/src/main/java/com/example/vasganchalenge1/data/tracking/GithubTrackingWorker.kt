package com.example.vasganchalenge1.data.tracking

import android.util.Log
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class GithubTrackingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val trackingId = inputData.getString(KEY_TRACKING_ID)?.trim().orEmpty()
        if (trackingId.isBlank()) {
            Log.e(TAG, "Worker started without trackingId")
            return Result.failure()
        }

        Log.i(TAG, "Worker run started. trackingId=$trackingId")
        val service = GithubTrackingService.getInstance(applicationContext)
        val result = when (service.collectSnapshotForWorker(trackingId)) {
            WorkerCollectionResult.SUCCESS -> Result.success()
            WorkerCollectionResult.RETRY -> Result.retry()
            WorkerCollectionResult.FAILURE -> Result.failure()
        }
        Log.i(TAG, "Worker run finished. trackingId=$trackingId result=$result")
        return result
    }

    companion object {
        private const val TAG = "GithubTrackingWorker"
        const val KEY_TRACKING_ID = "tracking_id"
    }
}
