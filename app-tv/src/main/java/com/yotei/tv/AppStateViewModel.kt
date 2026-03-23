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
 * 6. PAIRED - Show queue display
 * 7. ERROR - Show error message
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
    // DEVICE REGISTRATION
    // ──────────────────────────────────────────────────────────────────────────────

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
        android.util.Log.d("AppStateViewModel", "waitForPairingCodeRedemption called with binding: $binding")
        
        if (binding != null) {
            // Fast path: We already have the binding from PairingScreen
            android.util.Log.d("AppStateViewModel", "✓ Using binding from PairingScreen")
            android.util.Log.d("AppStateViewModel", "  barbershop_id: ${binding.barbershop_id}")
            
            viewModelScope.launch {
                try {
                    // Cache binding and transition to queue display
                    pairingRepository.cacheBinding(binding)
                    _deviceSecret.value = binding.device_secret
                    _barbershopId.value = binding.barbershop_id
                    _appState.value = AppState.PAIRED
                    android.util.Log.d("AppStateViewModel", "✓ PAIRED state set with barbershop_id: ${binding.barbershop_id}")
                } catch (e: Exception) {
                    android.util.Log.e("AppStateViewModel", "✗ Error setting PAIRED state: ${e.message}", e)
                    _errorMessage.value = e.message ?: "Pairing error"
                    _appState.value = AppState.ERROR
                }
            }
        } else {
            // Slow path: No binding yet, poll for it (legacy path)
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
}

/**
 * Composition local for injecting PairingRepository throughout the app.
 */
val LocalPairingRepository = compositionLocalOf<PairingRepository> {
    error("PairingRepository not provided")
}
