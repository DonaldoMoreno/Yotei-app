package com.yotei.tv.data.repository

import android.util.Log
import com.yotei.tv.data.api.TvQueueApiClient
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Repository for queue data operations.
 *
 * Responsibilities:
 * - Fetch queue data from API
 * - Start/stop live polling
 * - Handle network errors gracefully
 *
 * Does NOT contain UI logic.
 * Does NOT format data for display (that's QueueStateManager's job).
 */
class QueueRepository(
    private val queueApiClient: TvQueueApiClient
) {
    companion object {
        private const val TAG = "QueueRepository"
        private const val DEFAULT_POLL_INTERVAL_MS = 5000L // 5 seconds
    }

    /**
     * Fetch queue data once.
     * Used for initial load or manual refresh.
     *
     * @param displayId The display to fetch queue for
     * @param barbershopId The barbershop (for context)
     * @return Result containing QueueEtasResponse or error
     */
    suspend fun fetchQueueDataOnce(
        displayId: String,
        barbershopId: String
    ): Result<TvQueueApiClient.QueueEtasResponse> = runCatching {
        queueApiClient.getQueueEtas(displayId, barbershopId)
    }

    /**
     * Start live polling of queue data.
     * Returns a Flow that emits new queue data every 5 seconds.
     *
     * The Flow will continue emitting until:
     * - The collector is cancelled
     * - The coroutine scope is cancelled
     *
     * Network failures are wrapped in Result.failure() but don't stop the polling.
     * The UI layer should handle failures gracefully (show stale data, connection indicator).
     *
     * @param displayId The display to poll
     * @param barbershopId The barbershop (for context)
     * @param intervalMs Poll interval (default 5000ms = 5 seconds)
     * @return Flow<Result<QueueEtasResponse>> emitting every intervalMs
     */
    fun liveQueuePolling(
        displayId: String,
        barbershopId: String,
        intervalMs: Long = DEFAULT_POLL_INTERVAL_MS
    ): Flow<Result<TvQueueApiClient.QueueEtasResponse>> = flow {
        Log.d(TAG, "╔════════════════════════════════════════════════════════════╗")
        Log.d(TAG, "║        liveQueuePolling() FLOW CREATED                   ║")
        Log.d(TAG, "╚════════════════════════════════════════════════════════════╝")
        Log.d(TAG, "[A] Flow Parameters:")
        Log.d(TAG, "    displayId=$displayId")
        Log.d(TAG, "    barbershopId=$barbershopId")
        Log.d(TAG, "    interval=${intervalMs}ms")
        Log.d(TAG, "[B] Entering polling loop...")

        var pollCount = 0
        var lastEmitTime = System.currentTimeMillis()
        while (currentCoroutineContext().isActive) {
            pollCount++
            Log.d(TAG, "")
            Log.d(TAG, "╔────────────────────────────────────────────────────────────╗")
            Log.d(TAG, "║ POLL #$pollCount${" ".repeat(maxOf(0, 43 - pollCount.toString().length))}║")
            Log.d(TAG, "╚────────────────────────────────────────────────────────────╝")
            
            val pollStartTime = System.currentTimeMillis()
            Log.d(TAG, "[C] Poll #$pollCount Start:")
            Log.d(TAG, "    timestamp=${java.text.SimpleDateFormat("HH:mm:ss.SSS").format(pollStartTime)}")

            try {
                Log.d(TAG, "[D] Calling queueApiClient.getQueueEtas()...")
                val queueData = queueApiClient.getQueueEtas(displayId, barbershopId)
                val pollDuration = System.currentTimeMillis() - pollStartTime
                
                Log.d(TAG, "[E] API Success (${pollDuration}ms):")
                Log.d(TAG, "    barbershop_name: ${queueData.barbershop_name}")
                Log.d(TAG, "    queue_size: ${queueData.current_queue_size}")
                Log.d(TAG, "    etas_count: ${queueData.etas.size}")
                Log.d(TAG, "    active_barbers: ${queueData.active_barbers}")
                Log.d(TAG, "    avg_eta: ${queueData.average_eta_minutes}min")
                
                Log.d(TAG, "[F] Emitting Result.success()...")
                emit(Result.success(queueData))
                Log.d(TAG, "[F] ✓ Emission complete - result delivered to collector")
                
            } catch (e: Exception) {
                val pollDuration = System.currentTimeMillis() - pollStartTime
                Log.e(TAG, "[E] API Failed (${pollDuration}ms):")
                Log.e(TAG, "    Exception: ${e.message}")
                Log.e(TAG, "    Type: ${e.javaClass.simpleName}")
                
                // Check error type
                when {
                    e.message?.contains("401") == true || e.message?.contains("403") == true -> {
                        Log.e(TAG, "    ⚠️  AUTH ERROR (401/403) - Binding likely invalid")
                    }
                    e.message?.contains("404") == true -> {
                        Log.e(TAG, "    ⚠️  NOT FOUND (404) - DisplayId might be wrong")
                    }
                    e.message?.contains("5") == true -> {
                        Log.e(TAG, "    ⚠️  SERVER ERROR (5xx) - Backend issue")
                    }
                    else -> {
                        Log.e(TAG, "    ⚠️  NETWORK/OTHER ERROR")
                    }
                }
                Log.e(TAG, "[F] Emitting Result.failure()...", e)
                emit(Result.failure(e))
                Log.d(TAG, "[F] ✓ Failure emission complete")
            }

            Log.d(TAG, "[G] Poll #$pollCount Sleep:")
            Log.d(TAG, "    Waiting ${intervalMs}ms before next poll...")
            delay(intervalMs)
            Log.d(TAG, "[H] Poll #$pollCount Cycle Complete")
        }

        Log.d(TAG, "")
        Log.d(TAG, "╔════════════════════════════════════════════════════════════╗")
        Log.d(TAG, "║        liveQueuePolling() FLOW ENDED                      ║")
        Log.d(TAG, "╚════════════════════════════════════════════════════════════╝")
        Log.d(TAG, "[Z] Flow Completion:")
        Log.d(TAG, "    Total polls completed: $pollCount")
        Log.d(TAG, "    Coroutine active: ${currentCoroutineContext().isActive}")
        Log.d(TAG, "    Reason: Coroutine cancelled or scope ended")
    }
}
