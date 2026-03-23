package com.yotei.tv.data

import android.content.Context
import android.os.Build
import com.yotei.tv.data.PairingApiClient.*
import kotlinx.serialization.Serializable

/**
 * Repository for device pairing operations.
 * Coordinates between local storage (DeviceIdentityManager) and remote backend (PairingApiClient).
 */
class PairingRepository(
    context: Context,
    baseUrl: String,
    anonKey: String
) {
    private val deviceManager = DeviceIdentityManager(context)
    private val apiClient = PairingApiClient(baseUrl, anonKey)

    companion object {
        private const val TAG = "PairingRepository"
    }

    @Serializable
    data class PairingState(
        val isPaired: Boolean,
        val deviceId: String?,
        val deviceName: String?,
        val deviceModel: String?,
        val barbershopId: String?,
        val displayConfig: Map<String, String>? = null,
        val pairingProgress: PairingProgress = PairingProgress.UNPAIRED
    )

    enum class PairingProgress {
        UNPAIRED,           // Not yet paired
        REGISTERING,        // Calling registerDevice API
        WAITING_CODE,       // Waiting for staff to enter code
        POLLING_BINDING,    // Polling for code redemption
        BINDING_FOUND,      // Code redeemed, fetching binding config
        PAIRED,             // Fully paired and ready to display queue
        ERROR               // Error occurred
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // DEVICE LIFECYCLE
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Initialize device on first launch.
     * Generates device_id and stores it locally.
     *
     * @return true if this is first launch, false otherwise
     */
    fun isFirstLaunch(): Boolean {
        return deviceManager.isFirstLaunch()
    }

    /**
     * Get current device ID, generating one if needed.
     */
    fun ensureDeviceId(): String {
        var deviceId = deviceManager.getDeviceId()
        if (deviceId == null) {
            // Generate friendly device name based on device model
            val deviceName = "TV ${Build.DEVICE.capitalize()}"
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"

            deviceManager.initializeDevice(deviceName, deviceModel)
            deviceId = deviceManager.getDeviceId()!!
        }
        return deviceId
    }

    /**
     * Get current pairing state.
     */
    fun getPairingState(): PairingState {
        val deviceId = deviceManager.getDeviceId()
        val deviceName = deviceManager.getDeviceName()
        val deviceModel = deviceManager.getDeviceModel()
        val barbershopId = deviceManager.getBarbershopId()
        val isPaired = deviceManager.isPaired()

        return PairingState(
            isPaired = isPaired,
            deviceId = deviceId,
            deviceName = deviceName,
            deviceModel = deviceModel,
            barbershopId = barbershopId,
            pairingProgress = if (isPaired) PairingProgress.PAIRED else PairingProgress.UNPAIRED
        )
    }

    /**
     * Get device secret for authentication.
     * Returns null if device is not yet paired.
     */
    fun getDeviceSecret(): String? {
        return deviceManager.getDeviceSecret()
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // REGISTRATION & PAIRING
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Register device with backend on first launch.
     * Called after user selects their barbershop from a dropdown.
     *
     * @param deviceId Device UUID
     * @param barbershopId Barbershop UUID
     * @return Result with registration status
     */
    suspend fun registerDevice(
        deviceId: String,
        barbershopId: String
    ): Result<Unit> {
        return try {
            val deviceName = deviceManager.getDeviceName() ?: "TV"
            val deviceModel = deviceManager.getDeviceModel() ?: "Unknown"

            val result = apiClient.registerDevice(
                deviceId = deviceId,
                deviceName = deviceName,
                deviceModel = deviceModel,
                barbershopId = barbershopId
            )

            result.onSuccess { response ->
                // Store barbershop after successful registration
                deviceManager.setBarbershopId(barbershopId)
            }

            result.map { Unit }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate and display pairing code on TV.
     * In real implementation, backend would generate this.
     * For now, we'll show instructions to get code from staff.
     *
     * @return Generated 6-digit code (placeholder until staff generates)
     */
    fun requestPairingCode(): String {
        // TODO: Implement once backend generates and returns code
        // For now, show UI waiting for staff to provide code
        return "WAITING"
    }

    /**
     * Poll pairing code status while waiting for staff input.
     * Blocking call with internal retry logic.
     *
     * @param maxAttempts Maximum polling attempts (each ~2 seconds)
     * @param onProgress Callback with progress updates
     * @return Result with final binding if successful
     */
    suspend fun waitForPairingCodeRedemption(
        maxAttempts: Int = 450, // 15 minutes at 2s interval
        onProgress: ((attempt: Int, progress: String) -> Unit)? = null
    ): Result<GetBindingResponse> {
        val deviceId = deviceManager.getDeviceId()
            ?: return Result.failure(Exception("Device not initialized"))

        val deviceSecret = deviceManager.getDeviceSecret()
            ?: return Result.failure(Exception("Device secret not set"))

        var attempt = 0
        var lastError: Exception? = null

        while (attempt < maxAttempts) {
            attempt++
            val timeoutSeconds = (maxAttempts - attempt) * 2
            onProgress?.invoke(attempt, "Waiting for code (${timeoutSeconds}s remaining)...")

            try {
                val statusResult = apiClient.getPairingCodeStatus(deviceId, deviceSecret)

                statusResult.onSuccess { status ->
                    when (status.code_status) {
                        "pending" -> {
                            // Code not yet redeemed, continue polling
                            kotlinx.coroutines.delay(2000)
                        }
                        "used" -> {
                            // Code redeemed! Fetch the binding details
                            onProgress?.invoke(attempt, "¡Código redimido!")
                            val bindingResult = apiClient.getDeviceBinding(deviceId, deviceSecret)
                            return bindingResult
                        }
                        "expired" -> {
                            return Result.failure(Exception(status.message ?: "Pairing code expired"))
                        }
                        else -> {
                            return Result.failure(Exception("Unknown code status: ${status.code_status}"))
                        }
                    }
                }

                statusResult.onFailure { error ->
                    lastError = error as? Exception
                    if (error.message?.contains("401") == true || error.message?.contains("403") == true) {
                        // Auth error, don't retry
                        return Result.failure(error)
                    }
                    // Network error, retry
                    kotlinx.coroutines.delay(2000)
                }
            } catch (e: Exception) {
                lastError = e
                kotlinx.coroutines.delay(2000)
            }
        }

        return Result.failure(lastError ?: Exception("Timeout waiting for code redemption"))
    }

    /**
     * Fetch active binding and display configuration for device.
     * Called after successful pairing to get queue display config.
     *
     * @return Binding with display configuration
     */
    suspend fun fetchActiveBinding(): Result<GetBindingResponse> {
        val deviceId = deviceManager.getDeviceId()
            ?: return Result.failure(Exception("Device not initialized"))

        val deviceSecret = deviceManager.getDeviceSecret()
            ?: return Result.failure(Exception("Not yet paired - no device secret"))

        return apiClient.getDeviceBinding(deviceId, deviceSecret)
    }

    /**
     * Store binding data locally after successful pairing.
     */
    fun cacheBinding(binding: GetBindingResponse) {
        deviceManager.setBarbershopId(binding.barbershop_id)
        deviceManager.setFirstPairingAt(binding.created_at)
    }

    /**
     * Check if binding is still valid (24h refresh cycle).
     * Should call fetchActiveBinding periodically to detect revocation.
     */
    suspend fun refreshBinding(): Result<GetBindingResponse> {
        if (!deviceManager.isPaired()) {
            return Result.failure(Exception("Device not paired"))
        }

        return fetchActiveBinding()
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // DEBUG/RESET
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Clear all device data and reset to first-launch state.
     * Use only for testing or user-initiated factory reset.
     */
    fun resetDevice() {
        deviceManager.clearAll()
    }
}
