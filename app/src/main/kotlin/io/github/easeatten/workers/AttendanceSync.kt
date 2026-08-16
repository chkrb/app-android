package io.github.easeatten.workers

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import io.github.easeatten.data.repos.SettingsRepository
import io.github.easeatten.data.repos.UserRepository
import java.io.IOException
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AttendanceSync(val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    companion object Work {
        private const val LOGGER = "WORKER"
        private const val HOURS = 24L

        fun request(context: Context): OneTimeWorkRequest {
            val now = LocalTime.now()
            var nextRefresh = LocalTime.of(0, 0)

            CoroutineScope(Dispatchers.IO).launch {
                val settings = SettingsRepository(context).settingsFlow.first()
                nextRefresh = settings.attendanceRefreshTime
            }

            val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

            // If today's refresh time has already passed, schedule the next refresh for tomorrow.
            if (!nextRefresh.isAfter(now)) {
                nextRefresh = nextRefresh.plusHours(HOURS)
            }
            // Calculating the delay here which will be added to the next work request.
            // This is done because OneTimeWorkRequest would attempt to run immediately otherwise.
            val timeDiff = nextRefresh.second - now.second

            // OneTimeWorkRequest has been used instead of PeriodicWorkRequest
            // because of the compounding delay cause by the latter.
            // In case of the former we are using a calculated delay.
            return OneTimeWorkRequestBuilder<AttendanceSync>()
                .setInitialDelay(timeDiff.toLong(), TimeUnit.SECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .addTag("AttendanceSync")
                .build()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        return try {
            Log.i(LOGGER, "Refreshing attendance")

            // This is the main work that needs to be done.
            UserRepository(appContext).refreshAttendanceData()

            Log.i(LOGGER, "Attendance refreshed")

            Work.request(appContext)
            Log.i(LOGGER, "Successfully scheduled next refresh")

            Result.success()
        } catch (e: IOException) {
            Log.e(LOGGER, "Retry", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(LOGGER, "Failure", e)
            Result.failure()
        }
    }
}
