package com.yotei.tv.data

import kotlinx.serialization.Serializable
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay

/**
 * Pairing Code Management for TV App
 * Generates and manages 6-digit pairing codes for device-display binding
 */

@Serializable
data class GeneratePairingCodeRequest(
    val device_id: String,
    val device_secret: String
)

@Serializable
data class RegisterDeviceProvisionalRequest(
    val device_id: String,
    val device_name: String,
    val device_model: String
)

@Serializable
data class GeneratePairingCodeResponse(
    val code: String,                    // Código de 6 dígitos (ej: "482917")
    val code_status: String,             // "pending"
    val ttl_seconds: Int,                // Tiempo de expiración (900 = 15 minutos)
    val created_at: String,
    val expires_at: String
)

@Serializable
data class GetPairingCodeStatusResponse(
    val code: String,
    val code_status: String,             // "pending" | "used" | "expired"
    val ttl_seconds: Int,
    val redeemed_at: String?,
    val redeemed_by_display_id: String?,  // Si fue redimido
    // When code_status = "used", includes full binding details for TV app initialization
    val binding: PairingApiClient.GetBindingResponse? = null
)

class PairingCodeManager(
    private val httpClient: HttpClient,
    private val apiBaseUrl: String
) {
    private val TAG = "PairingCodeManager"
    
    /**
     * NEW Option 3: Register device as PROVISIONAL without barbershop_id
     * Called on first pairing attempt BEFORE generating pairing code
     */
    suspend fun registerDeviceProvisional(
        deviceId: String,
        deviceName: String,
        deviceModel: String
    ): Result<Unit> {
        return try {
            val url = "$apiBaseUrl/api/devices/register-provisional"
            android.util.Log.d(TAG, "Registering device as provisional to: $url")
            android.util.Log.d(TAG, "deviceId: $deviceId, deviceName: $deviceName, deviceModel: $deviceModel")
            
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(RegisterDeviceProvisionalRequest(
                    device_id = deviceId,
                    device_name = deviceName,
                    device_model = deviceModel
                ))
            }
            
            android.util.Log.d(TAG, "Response received: ${response.status}")

            if (response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) {
                android.util.Log.d(TAG, "✓ Device registered as PROVISIONAL successfully")
                Result.success(Unit)
            } else {
                val errorMsg = "Failed to register device: ${response.status} (HTTP ${response.status.value})"
                android.util.Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Exception during device registration: ${e.message}", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Generate a new pairing code for this device
     * Called once when device first launches or needs new code
     * PREREQUISITE: Device must be registered via registerDeviceProvisional() first
     */
    suspend fun generatePairingCode(
        deviceId: String,
        deviceSecret: String
    ): Result<GeneratePairingCodeResponse> {
        return try {
            val url = "$apiBaseUrl/api/pairing-codes/generate"
            android.util.Log.d(TAG, "Starting code generation request to: $url")
            android.util.Log.d(TAG, "deviceId: $deviceId, deviceSecret: ${deviceSecret.take(4)}...")
            
            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(GeneratePairingCodeRequest(
                    device_id = deviceId,
                    device_secret = deviceSecret
                ))
            }
            
            android.util.Log.d(TAG, "Response received: ${response.status}")

            if (response.status == HttpStatusCode.Created) {
                val body = response.body<GeneratePairingCodeResponse>()
                android.util.Log.d(TAG, "✓ Code generated successfully: ${body.code}")
                Result.success(body)
            } else {
                val errorMsg = "Failed to generate pairing code: ${response.status} (HTTP ${response.status.value})"
                android.util.Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Exception during code generation: ${e.message}", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Get current pairing code status
     * Called periodically (every 2 seconds) to check if staff has redeemed the code
     */
    suspend fun getPairingCodeStatus(
        deviceId: String,
        code: String
    ): Result<GetPairingCodeStatusResponse> {
        return try {
            val url = "$apiBaseUrl/api/pairing-codes/$code/status"
            android.util.Log.d(TAG, "Polling status for code: $code")
            
            val response = httpClient.get(url) {
                parameter("device_id", deviceId)
            }
            
            android.util.Log.d(TAG, "Status poll response: ${response.status}")

            when (response.status) {
                HttpStatusCode.OK -> {
                    // Code was redeemed
                    try {
                        val body = response.body<GetPairingCodeStatusResponse>()
                        android.util.Log.d(TAG, "Code status: ${body.code_status}")
                        android.util.Log.d(TAG, "✓ Deserialization successful")
                        android.util.Log.d(TAG, "  binding: ${body.binding}")
                        Result.success(body)
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "✗ Failed to deserialize GetPairingCodeStatusResponse: ${e.message}", e)
                        e.printStackTrace()
                        Result.failure(e)
                    }
                }
                HttpStatusCode.Accepted -> {
                    // Code still pending (staff hasn't entered it yet)
                    try {
                        val body = response.body<GetPairingCodeStatusResponse>()
                        Result.success(body)
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "✗ Failed to deserialize pending status: ${e.message}", e)
                        Result.failure(e)
                    }
                }
                HttpStatusCode.Gone -> {
                    // Code expired (15 min TTL)
                    android.util.Log.w(TAG, "Code expired (410)")
                    Result.failure(Exception("Pairing code expired"))
                }
                else -> {
                    val errorMsg = "Failed to get pairing code status: ${response.status} (HTTP ${response.status.value})"
                    android.util.Log.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Exception during status polling: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Poll code status repeatedly until redeemed or expired
     * Loops every 2 seconds with max attempts
     */
    suspend fun pollCodeStatus(
        code: String,
        deviceId: String,
        maxAttempts: Int = 450
    ): Result<GetPairingCodeStatusResponse> {
        var attempt = 0
        while (attempt < maxAttempts) {
            attempt++
            try {
                val url = "$apiBaseUrl/api/pairing-codes/$code/status"
                val response = httpClient.get(url) {
                    parameter("device_id", deviceId)
                }
                
                if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Accepted) {
                    val body = response.body<GetPairingCodeStatusResponse>()
                    return Result.success(body)
                } else if (response.status == HttpStatusCode.Gone) {
                    return Result.failure(Exception("Code expired"))
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Poll error (attempt $attempt): ${e.message}")
            }
            delay(2000)
        }
        return Result.failure(Exception("Max poll attempts exceeded"))
    }

    /**
     * Fetch binding details after code has been redeemed
     * Called after code_status changes to "used"
     */
    suspend fun getBindingAfterCodeRedeem(
        code: String,
        deviceId: String
    ): Result<PairingApiClient.GetBindingResponse> {
        return try {
            val url = "$apiBaseUrl/api/pairing-codes/$code/binding"
            android.util.Log.d(TAG, "Fetching binding for redeemed code: $code")
            
            val response = httpClient.get(url) {
                parameter("device_id", deviceId)
            }
            
            if (response.status == HttpStatusCode.OK) {
                val body = response.body<PairingApiClient.GetBindingResponse>()
                android.util.Log.d(TAG, "✓ Binding received: ${body.binding_id}")
                Result.success(body)
            } else {
                val errorMsg = "Failed to get binding: ${response.status} (HTTP ${response.status.value})"
                android.util.Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Exception fetching binding: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Clear/revoke current pairing code
     * Called when user dismisses pairing screen or requests new code
     */
    suspend fun revokePairingCode(
        deviceId: String,
        code: String
    ): Result<Unit> {
        return try {
            val url = "$apiBaseUrl/api/pairing-codes/$code"
            android.util.Log.d(TAG, "Revoking code: $code")
            
            val response = httpClient.delete(url) {
                parameter("device_id", deviceId)
            }
            
            android.util.Log.d(TAG, "Revoke response: ${response.status}")

            if (response.status == HttpStatusCode.NoContent) {
                android.util.Log.d(TAG, "✓ Code revoked successfully")
                Result.success(Unit)
            } else {
                val errorMsg = "Failed to revoke pairing code: ${response.status}"
                android.util.Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Exception during code revocation: ${e.message}", e)
            Result.failure(e)
        }
    }
}
