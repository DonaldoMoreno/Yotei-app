package com.yotei.tv.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yotei.tv.data.repository.QueueRepository
import com.yotei.tv.domain.state.QueueDisplayState
import com.yotei.tv.domain.state.QueueStateManager
import com.yotei.tv.domain.state.ConnectionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * ViewModel for queue display state.
 *
 * Responsibilities:
 * - Start/stop live queue polling
 * - Expose queue state as StateFlow for UI to collect
 * - Expose connection status
 * - Handle polling lifecycle (tied to ViewModel scope)
 *
 * The ViewModel bridges between Repository (data) and UI (Composables).
 * It does NOT contain business logic - that's in QueueRepository and QueueStateManager.
 */
class TvQueueViewModel(
    private val queueRepository: QueueRepository,
    private val queueStateManager: QueueStateManager
) : ViewModel() {
    companion object {
        private const val TAG = "TvQueueViewModel"
    }

    /**
     * The current queue display state.
     * UI collects this with collectAsState() for automatic recomposition.
     */
    private val _queueState = MutableStateFlow<QueueDisplayState?>(null)
    val queueState: StateFlow<QueueDisplayState?> = _queueState.asStateFlow()

    /**
     * Connection status (CONNECTED, DEGRADED, OFFLINE, BINDING_INVALID).
     * Used to show connection indicator overlay.
     */
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    /**
     * Whether polling is active.
     * Used to prevent multiple concurrent polling jobs.
     */
    private var pollingJob: Job? = null

    /**
     * Tracks when polling started to detect infinite loading.
     */
    private var pollingStartTime = 0L
    private var firstDataReceivedTime = 0L
    private var timeoutWarningIssued = false
    private var timeoutJob: Job? = null

    /**
     * Callback for when polling times out (no data after 15 seconds).
     * Set by MainActivity to trigger AppStateViewModel.transitionAppState(ERROR, ...)
     */
    var onPollingTimeout: (() -> Unit)? = null

    /**
     * Start live queue polling.
     *
     * Call this when:
     * - User has completed pairing (binding received)
     * - App resumes from background
     * - Network connection restored
     *
     * Do NOT call multiple times without stopping first.
     *
     * @param displayId The display ID to poll
     * @param barbershopId The barbershop ID (for context)
     */
    fun startLiveQueueUpdates(
        displayId: String,
        barbershopId: String
    ) {
        Log.d(TAG, "╔═══════════════════════════════════════════════════════════╗")
        Log.d(TAG, "║        startLiveQueueUpdates() CALLED                      ║")
        Log.d(TAG, "╚═══════════════════════════════════════════════════════════╝")
        Log.d(TAG, "[A] Method Entry:")
        Log.d(TAG, "    displayId=$displayId")
        Log.d(TAG, "    barbershopId=$barbershopId")
        Log.d(TAG, "    pollingJob?.isActive=${pollingJob?.isActive}")
        
        // Parameter validation
        if (displayId.isBlank()) {
            Log.e(TAG, "[A] param validation: ✗ displayId is blank!")
            _queueState.value = null
            _connectionStatus.value = ConnectionStatus.OFFLINE
            return
        }
        if (barbershopId.isBlank()) {
            Log.e(TAG, "[A] param validation: ✗ barbershopId is blank!")
            _queueState.value = null
            _connectionStatus.value = ConnectionStatus.OFFLINE
            return
        }
        Log.d(TAG, "[A] param validation: ✓ PASS")
        
        // Prevent multiple concurrent polling jobs
        if (pollingJob?.isActive == true) {
            Log.w(TAG, "[B] duplicate prevention: ⚠️  Already polling, ignoring duplicate")
            return
        }
        Log.d(TAG, "[B] duplicate prevention: ✓ Not already polling")

        Log.d(TAG, "[C] Creating polling job...")
        pollingStartTime = System.currentTimeMillis()
        firstDataReceivedTime = 0L
        timeoutWarningIssued = false
        
        // 15-second timeout check - warn if no data received
        // Use coroutine instead of Timer for cleaner cancellation
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            try {
                delay(15000)  // Wait 15 seconds
                if (firstDataReceivedTime == 0L && !timeoutWarningIssued) {
                    timeoutWarningIssued = true
                    Log.e(TAG, "[!!!TIMEOUT!!!] NO DATA AFTER 15 SEC - UI STUCK ON LOADING!")
                    Log.e(TAG, "    Check logs above for [G] Poll onSuccess")
                    Log.e(TAG, "    If missing: API/network/auth issue")
                    Log.e(TAG, "    Triggering timeout callback to AppStateViewModel...")
                    
                    // Trigger callback to transition AppStateViewModel to ERROR state
                    onPollingTimeout?.invoke()
                }
            } catch (e: Exception) {
                // Cancelled or other error - ignore
            }
        }
                pollingJob = viewModelScope.launch {
            Log.d(TAG, "[D] Polling Coroutine STARTED")
            
            try {
                Log.d(TAG, "[E] Calling queueRepository.liveQueuePolling()...")
                val flow = queueRepository.liveQueuePolling(displayId, barbershopId)
                Log.d(TAG, "[E] ✓ Flow object created, starting collection...")
                
                flow.collect { result ->
                    Log.d(TAG, "[F] Poll result received (checked for success/failure)")
                    
                    result.onSuccess { apiResponse ->
                        if (firstDataReceivedTime == 0L) {
                            firstDataReceivedTime = System.currentTimeMillis()
                            timeoutJob?.cancel()  // Cancel timeout since data arrived
                            val elapsedMs = firstDataReceivedTime - pollingStartTime
                            Log.d(TAG, "[SUCCESS!] First data received after ${elapsedMs}ms")
                        }
                        Log.d(TAG, "[G] Poll onSuccess:")
                        Log.d(TAG, "    ✓ API returned data")
                        Log.d(TAG, "    barbershop=${apiResponse.barbershop_name}")
                        Log.d(TAG, "    queue_size=${apiResponse.current_queue_size}")
                        Log.d(TAG, "    etas_count=${apiResponse.etas.size}")

                        // Transform API response to UI state
                        Log.d(TAG, "[H] Transforming state...")
                        val displayState =
                            queueStateManager.mapApiResponseToDisplayState(apiResponse)

                        Log.d(TAG, "[H] ✓ Transformation complete")
                        Log.d(TAG, "    currentTicket=${displayState.currentTicket?.ticketNumber ?: "none"}")
                        Log.d(TAG, "    nextTickets=${displayState.nextTickets.size}")

                        // Update both state and connection status
                        Log.d(TAG, "[I] Updating StateFlow...")
                        _queueState.value = displayState
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                        
                        Log.d(TAG, "[I] ✓ StateFlow UPDATED - UI recomposition triggered!")
                        Log.d(TAG, "    _queueState.value is now non-null: ${_queueState.value != null}")

                    }.onFailure { exception ->
                        Log.e(TAG, "[G] Poll onFailure:")
                        Log.e(TAG, "    ✗ API ERROR: ${exception.message}")
                        Log.e(TAG, "    Exception type: ${exception.javaClass.simpleName}")
                        Log.d(TAG, "    Full stack trace:", exception)

                        // Check for binding-related errors FIRST (401, 403)
                        val errorMessage = exception.message ?: ""
                        if (errorMessage.contains("401") || errorMessage.contains("403")) {
                            Log.e(TAG, "[H] ✗✗ BINDING INVALID DETECTED (401/403)")
                            Log.e(TAG, "    Calling onPollingTimeout callback to transition to ERROR")
                            _connectionStatus.value = ConnectionStatus.BINDING_INVALID
                            _queueState.value = null  // Clear state on binding error
                            
                            // Trigger error state transition in AppStateViewModel
                            onPollingTimeout?.invoke()
                            return@collect  // Stop polling
                        }

                        // Keep showing last known state but mark as degraded
                        Log.d(TAG, "[H] Fallback handling:")
                        val currentState = _queueState.value
                        if (currentState != null) {
                            Log.d(TAG, "    ✓ Have cached state, marking as DEGRADED")
                            _queueState.value =
                                queueStateManager.markAsDegraded(currentState)
                        } else {
                            Log.e(TAG, "    ✗ NO cached state! queueState will remain NULL")
                            Log.e(TAG, "    UI will STAY on loading screen: triggering error state")
                            
                            // No cached state and API failing = cannot recover
                            // Trigger error state transition
                            onPollingTimeout?.invoke()
                        }
                        _connectionStatus.value = ConnectionStatus.DEGRADED
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[E] ✗✗ FATAL ERROR in polling coroutine:")
                Log.e(TAG, "    Exception: ${e.message}")
                Log.e(TAG, "    Type: ${e.javaClass.simpleName}")
                Log.e(TAG, "    Full stack:", e)
                _connectionStatus.value = ConnectionStatus.OFFLINE
                _queueState.value = null
            }
            
            Log.d(TAG, "[J] Polling coroutine ended (collection stopped or cancelled)")
        }
        
        Log.d(TAG, "[C] ✓ Polling job created and launched")
        Log.d(TAG, "╔═══════════════════════════════════════════════════════════╗")
        Log.d(TAG, "║        startLiveQueueUpdates() COMPLETE                   ║")
        Log.d(TAG, "╚═══════════════════════════════════════════════════════════╝")
    }

    /**
     * Stop live queue polling.
     *
     * Call this when:
     * - Leaving the queue display screen
     * - App enters background
     * - Pairing revoked
     */
    fun stopLiveQueueUpdates() {
        Log.d(TAG, "Stopping live queue polling")
        timeoutJob?.cancel()
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Manually refresh queue data once.
     * Used for "Pull to refresh" or similar UI patterns (if added in future).
     */
    fun refreshQueueDataOnce(
        displayId: String,
        barbershopId: String
    ) {
        Log.d(TAG, "Manual refresh requested")

        viewModelScope.launch {
            queueRepository.fetchQueueDataOnce(displayId, barbershopId)
                .onSuccess { apiResponse ->
                    val displayState =
                        queueStateManager.mapApiResponseToDisplayState(apiResponse)
                    _queueState.value = displayState
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                }
                .onFailure { exception ->
                    Log.e(TAG, "Manual refresh failed: ${exception.message}", exception)
                    // Keep showing stale data
                    val currentState = _queueState.value
                    if (currentState != null) {
                        _queueState.value =
                            queueStateManager.markAsDegraded(currentState)
                    }
                    _connectionStatus.value = ConnectionStatus.DEGRADED
                }
        }
    }

    /**
     * Clear queue state.
     * Used when pairing is revoked or device is reset.
     */
    fun clearQueueState() {
        Log.d(TAG, "Clearing queue state")
        stopLiveQueueUpdates()
        _queueState.value = null
        _connectionStatus.value = ConnectionStatus.CONNECTED
    }

    /**
     * ViewModel cleanup: stop polling when ViewModel is destroyed.
     */
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, stopping polling")
        stopLiveQueueUpdates()
    }
}
