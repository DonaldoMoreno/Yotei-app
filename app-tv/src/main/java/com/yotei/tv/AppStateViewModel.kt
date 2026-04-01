package com.yotei.tv

import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yotei.tv.data.PairingRepository
import com.yotei.tv.data.PairingApiClient
import kotlinx.coroutines.launch

/**
 * App-level state machine for device registration and pairing.
 *
 * States:
 * 1. INITIALIZING - Loading device state from storage
 * 2. FIRST_LAUNCH - Show device registration screen
 * 3. REGISTERING - Register device with backend
 * 4. WAITING_PAIRING_CODE - Show code input screen (staff enters code)
 * 5. POLLING_BINDING - Poll for code redemption
 * 6. PAIRED - Binding received, cached, ready to load display config
 * 7. LOADING_DISPLAY_CONFIG - Loading queue data after pairing
 * 8. DISPLAY_READY - Queue data loaded, showing display (implicit - triggers UI render)
 * 9. ERROR - Show error message
 *
 * After pairing (PAIRED state):
 * → TvQueueViewModel starts polling
 * → If no data within 15 seconds → ERROR
 * → If polling fails (auth, network) → ERROR
 * → If polling succeeds → StateFlow updates, UI shows queue
 */
class AppStateViewModel(
    private val pairingRepository: PairingRepository
) : ViewModel() {

    enum class AppState {
        INITIALIZING,
        FIRST_LAUNCH,
        REGISTERING,
        WAITING_PAIRING_CODE,
        POLLING_BINDING,
        PAIRED,
        LOADING_DISPLAY_CONFIG,
        ERROR
    }

    private val _appState = mutableStateOf(AppState.INITIALIZING)
    val appState: State<AppState> = _appState

    private val _deviceId = mutableStateOf<String?>(null)
    val deviceId: State<String?> = _deviceId

    private val _deviceName = mutableStateOf<String?>(null)
    val deviceName: State<String?> = _deviceName

    private val _deviceModel = mutableStateOf<String?>(null)
    val deviceModel: State<String?> = _deviceModel

    private val _deviceSecret = mutableStateOf<String?>(null)
    val deviceSecret: State<String?> = _deviceSecret

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> = _errorMessage

    private val _displayConfig = mutableStateOf<Map<String, Any>?>(null)
    val displayConfig: State<Map<String, Any>?> = _displayConfig

    private val _barbershopId = mutableStateOf<String?>(null)
    val barbershopId: State<String?> = _barbershopId

    private val _displayId = mutableStateOf<String?>(null)
    val displayId: State<String?> = _displayId

    private val _pollingProgress = mutableStateOf<String?>(null)
    val pollingProgress: State<String?> = _pollingProgress

    init {
        initializeApp()
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // APP INITIALIZATION
    // ──────────────────────────────────────────────────────────────────────────────

    private fun initializeApp() {
        viewModelScope.launch {
            try {
                // Check if first launch
                if (pairingRepository.isFirstLaunch()) {
                    // Generate device ID
                    val deviceId = pairingRepository.ensureDeviceId()
                    _deviceId.value = deviceId

                    // Show first launch screen
                    _deviceName.value = "TV ${Build.DEVICE.replaceFirstChar { it.uppercase() }}"
                    _deviceModel.value = "${Build.MANUFACTURER} ${Build.MODEL}"
                    _appState.value = AppState.FIRST_LAUNCH
                } else {
                    // Reload device state from storage
                    val state = pairingRepository.getPairingState()
                    _deviceId.value = state.deviceId
                    _deviceName.value = state.deviceName
                    _deviceModel.value = state.deviceModel
                    _deviceSecret.value = pairingRepository.getDeviceSecret()  // Load device secret

                    if (state.isPaired) {
                        // Device already paired, show queue display
                        _appState.value = AppState.PAIRED
                        // TODO: Fetch and cache binding config
                    } else {
                        // Device registered but not paired, show pairing screen
                        _appState.value = AppState.WAITING_PAIRING_CODE
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Initialization error"
                _appState.value = AppState.ERROR
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // DEVICE REGISTRATION (Option 3 Flow)
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * NEW Option 3 Flow:
     * Register device as PROVISIONAL without barbershop_id.
     * Called from PairingScreen when user presses "Generate Code" button.
     *
     * @return true if registration succeeded (device can now generate pairing code)
     */
    fun registerDeviceProvisional(): Boolean {
        _errorMessage.value = null

        return try {
            val deviceId = _deviceId.value ?: throw Exception("Device ID not initialized")

            // Launch coroutine to register asynchronously
            viewModelScope.launch {
                val result = pairingRepository.registerDeviceProvisional(deviceId)

                result.onSuccess {
                    android.util.Log.d("AppStateViewModel", "✓ Device registered as PROVISIONAL")
                    // Success - device is ready to generate pairing code
                    // (PairingScreen will proceed to generatePairingCode)
                }

                result.onFailure { error ->
                    android.util.Log.e("AppStateViewModel", "✗ Failed to register device: ${error.message}")
                    _errorMessage.value = "Failed to register device: ${error.message}"
                    _appState.value = AppState.ERROR
                }
            }

            true // Indicate that registration is in progress
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Registration error"
            android.util.Log.e("AppStateViewModel", "Exception: ${e.message}")
            false
        }
    }

    /**
     * OLD Flow (deprecated - but kept for backward compatibility).
     * Register device with barbershop_id after user selects from dropdown.
     * 
     * @param barbershopId Barbershop UUID from user selection
     */
    fun registerDevice(barbershopId: String) {
        _appState.value = AppState.REGISTERING
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val deviceId = _deviceId.value ?: throw Exception("Device ID not initialized")

                val result = pairingRepository.registerDevice(deviceId, barbershopId)

                result.onSuccess {
                    // Transition to pairing screen
                    _appState.value = AppState.WAITING_PAIRING_CODE
                }

                result.onFailure { error ->
                    _errorMessage.value = error.message ?: "Registration failed"
                    _appState.value = AppState.ERROR
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Registration error"
                _appState.value = AppState.ERROR
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // PAIRING FLOW
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Handle pairing code redemption with binding data received from PairingScreen.
     * The binding already contains all necessary info (barbershop_id, etc.)
     * so we can transition directly to PAIRED state.
     */
    fun waitForPairingCodeRedemption(binding: PairingApiClient.GetBindingResponse? = null) {
        android.util.Log.d("AppStateViewModel", "════════════════════════════════════════════════════════")
        android.util.Log.d("AppStateViewModel", "waitForPairingCodeRedemption() called")
        android.util.Log.d("AppStateViewModel", "  binding: $binding")
        
        if (binding != null) {
            android.util.Log.d("AppStateViewModel", "✓ Fast path: Using binding from PairingScreen")
            android.util.Log.d("AppStateViewModel", "  binding.barbershop_id: ${binding.barbershop_id}")
            android.util.Log.d("AppStateViewModel", "  binding.device_secret: ${binding.device_secret.take(10)}...")
            
            viewModelScope.launch {
                try {
                    android.util.Log.d("AppStateViewModel", "→ Entering coroutine to cache binding")
                    
                    // Cache binding and transition to queue display
                    pairingRepository.cacheBinding(binding)
                    android.util.Log.d("AppStateViewModel", "✓ Binding cached")
                    
                    _deviceSecret.value = binding.device_secret
                    android.util.Log.d("AppStateViewModel", "✓ Device secret updated")
                    
                    _barbershopId.value = binding.barbershop_id
                    android.util.Log.d("AppStateViewModel", "✓ Barbershop ID updated: ${binding.barbershop_id}")
                    
                    _displayId.value = binding.display_id
                    android.util.Log.d("AppStateViewModel", "✓ Display ID updated: ${binding.display_id}")
                    
                    android.util.Log.d("AppStateViewModel", "→ Setting AppState to LOADING_DISPLAY_CONFIG...")
                    transitionAppState(
                        AppState.LOADING_DISPLAY_CONFIG,
                        reason = "Pairing binding cached, starting display config load"
                    )
                    android.util.Log.d("AppStateViewModel", "✓✓✓ LOADING_DISPLAY_CONFIG state set successfully ✓✓✓")
                    android.util.Log.d("AppStateViewModel", "  Current appState: ${_appState.value}")
                    android.util.Log.d("AppStateViewModel", "  Current barbershopId: ${_barbershopId.value}")
                    
                } catch (e: Exception) {
                    android.util.Log.e("AppStateViewModel", "✗✗ Error setting PAIRED state: ${e.message}", e)
                    _errorMessage.value = e.message ?: "Pairing error"
                    _appState.value = AppState.ERROR
                }
                
                android.util.Log.d("AppStateViewModel", "════════════════════════════════════════════════════════")
            }
        } else {
            android.util.Log.d("AppStateViewModel", "→ Slow path: Polling for binding (legacy)")
            _appState.value = AppState.POLLING_BINDING
            _errorMessage.value = null
            _pollingProgress.value = "Waiting for staff to enter code..."

            viewModelScope.launch {
                try {
                    val result = pairingRepository.waitForPairingCodeRedemption(
                        maxAttempts = 450, // 15 minutes
                        onProgress = { attempt, message ->
                            _pollingProgress.value = message
                        }
                    )

                    result.onSuccess { pollBinding ->
                        // Cache binding and transition to queue display
                        pairingRepository.cacheBinding(pollBinding)
                        _deviceSecret.value = pollBinding.device_secret  // Update device secret after pairing
                        _barbershopId.value = pollBinding.barbershop_id  // Save barbershop_id for fetching queue data
                        // Note: display_config is now JsonElement, not needed in state
                        _appState.value = AppState.PAIRED
                        // Queue data will be fetched by MainActivity when observing PAIRED state
                    }

                    result.onFailure { error ->
                        _errorMessage.value = error.message ?: "Pairing failed"
                        _appState.value = AppState.ERROR
                        _pollingProgress.value = null
                    }
                } catch (e: Exception) {
                    _errorMessage.value = e.message ?: "Pairing error"
                    _appState.value = AppState.ERROR
                    _pollingProgress.value = null
                }
            }
        }
    }

    /**
     * Retry pairing if it failed.
     */
    fun retryPairing() {
        _appState.value = AppState.WAITING_PAIRING_CODE
        _errorMessage.value = null
    }

    /**
     * Refresh binding (24h cycle to detect revocation).
     * Called periodically from queue display screen.
     */
    fun refreshBinding() {
        viewModelScope.launch {
            try {
                val result = pairingRepository.refreshBinding()

                result.onSuccess { binding ->
                    // Successfully refreshed binding, store barbershop_id if needed
                    _barbershopId.value = binding.barbershop_id
                }

                result.onFailure { error ->
                    // Device was revoked or lost binding
                    if (error.message?.contains("404") == true ||
                        error.message?.contains("403") == true) {
                        _appState.value = AppState.WAITING_PAIRING_CODE
                        _errorMessage.value = "Binding was revoked. Please pair again."
                    }
                }
            } catch (e: Exception) {
                // Network error, continue showing display (graceful degradation)
            }
        }
    }

    /**
     * Clear all device data and reset to first-launch state.
     * For testing/debug only.
     */
    fun resetDevice() {
        pairingRepository.resetDevice()
        initializeApp()
    }

    /**
     * Centralized state transition helper with automatic logging.
     * All AppState changes should go through this for consistency.
     */
    fun transitionAppState(newState: AppState, reason: String = "", errorMessage: String? = null) {
        android.util.Log.d("AppStateViewModel", "──────────────────────────────────────────────────────────")
        android.util.Log.d("AppStateViewModel", "STATE TRANSITION: ${_appState.value} → $newState")
        if (reason.isNotEmpty()) {
            android.util.Log.d("AppStateViewModel", "  Reason: $reason")
        }
        
        _appState.value = newState
        
        if (errorMessage != null && errorMessage.isNotEmpty()) {
            _errorMessage.value = errorMessage
            android.util.Log.d("AppStateViewModel", "  Error: $errorMessage")
        }
        
        android.util.Log.d("AppStateViewModel", "──────────────────────────────────────────────────────────")
    }
}

/**
 * Composition local for injecting PairingRepository throughout the app.
 */
val LocalPairingRepository = compositionLocalOf<PairingRepository> {
    error("PairingRepository not provided")
}
