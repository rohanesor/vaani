package com.vokie.cloud

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/**
 * Coordinates cloud sync operations.
 * Manages sync state, triggers, and WorkManager scheduling.
 */
class SyncCoordinator(
    private val context: Context,
    private val repository: CloudSyncRepository,
) {
    companion object {
        const val SYNC_WORK_NAME = "vaani_cloud_sync"
        const val PERIODIC_SYNC_WORK_NAME = "vaani_periodic_sync"
        private const val PERIODIC_SYNC_INTERVAL_MINUTES = 15L
        private const val MAX_RETRY_ATTEMPTS = 5
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /**
     * Triggers an immediate sync.
     */
    suspend fun syncNow(): SyncResult {
        if (!repository.isAuthenticated()) {
            _syncState.value = SyncState.AuthRequired
            throw SyncException.AuthRequired("Not authenticated")
        }

        _syncState.value = SyncState.Syncing
        return try {
            val result = repository.sync()
            _syncState.value = if (result.isSuccess) {
                SyncState.Synced(System.currentTimeMillis())
            } else {
                SyncState.PartialError("${result.uploadErrors} items failed")
            }
            result
        } catch (e: SyncException.AuthRequired) {
            _syncState.value = SyncState.AuthRequired
            throw e
        } catch (e: SyncException.RateLimited) {
            _syncState.value = SyncState.RetryPending(
                System.currentTimeMillis() + e.retryAfterSeconds * 1000
            )
            throw e
        } catch (e: SyncException) {
            _syncState.value = if (e.retryable) {
                SyncState.RetryPending(System.currentTimeMillis() + 30_000)
            } else {
                SyncState.Error(e.message ?: "Sync failed")
            }
            throw e
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.message ?: "Sync failed")
            throw e
        }
    }

    /**
     * Schedules a one-time sync via WorkManager.
     */
    fun scheduleSyncOnce() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWork = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setInputData(workDataOf("session_generation" to repository.sessionGeneration()))
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.KEEP, // Don't duplicate if already queued
            syncWork
        )
    }

    /**
     * Starts periodic background sync.
     */
    fun startPeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<CloudSyncWorker>(
            PERIODIC_SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setInputData(workDataOf("session_generation" to repository.sessionGeneration()))
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
    }

    /**
     * Stops periodic background sync.
     */
    fun stopPeriodicSync() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
    }

    /**
     * Cancels all pending sync work (e.g., on logout).
     */
    fun cancelAllSync() {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
        _syncState.value = SyncState.Idle
    }
}

/**
 * Sync state for UI display.
 */
sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Synced(val timestamp: Long) : SyncState()
    object WaitingForConnection : SyncState()
    data class RetryPending(val retryAt: Long) : SyncState()
    object AuthRequired : SyncState()
    data class Error(val message: String) : SyncState()
    data class PartialError(val message: String) : SyncState()
}

/**
 * WorkManager worker for background sync.
 */
class CloudSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Get repository from app-level dependency injection
        // In a real app, use Hilt or manual DI
        val db = com.vokie.data.local.VokieDatabase.get(applicationContext)
        val user = db.cloudUser().get() ?: return Result.success() // Not logged in
        val credentials = SecureCredentialStore(applicationContext)
        val syncPreferences = CloudSyncPreferences(applicationContext)
        if (!syncPreferences.enabled.value) return Result.success()
        if (inputData.getString("session_generation") != syncPreferences.sessionGeneration) return Result.success()
        val token = credentials.token() ?: return Result.failure()

        val baseUrl = com.vokie.BuildConfig.AI_BACKEND_BASE_URL
        if (baseUrl.isBlank()) {
            return Result.success() // Cloud sync not configured
        }

        val apiClient = SyncApiClient(baseUrl) { credentials.token() }
        val repository = CloudSyncRepository(
            cloudUserDao = db.cloudUser(),
            cloudIdMappingDao = db.cloudIdMapping(),
            cloudConversationDao = db.cloudConversations(),
            cloudMemberDao = db.cloudMembers(),
            cloudMessageDao = db.cloudMessages(),
            syncQueueDao = db.syncQueue(),
            localMessageDao = db.messages(),
            apiClient = apiClient,
            credentials = credentials,
            syncPreferences = syncPreferences,
        )

        return try {
            repository.sync()
            Result.success()
        } catch (e: SyncException.AuthRequired) {
            // Don't retry auth errors
            Result.failure()
        } catch (e: SyncException.RateLimited) {
            // Retry after rate limit
            Result.retry()
        } catch (e: SyncException) {
            if (e.retryable && runAttemptCount < 5) {
                Result.retry()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            if (runAttemptCount < 5) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
