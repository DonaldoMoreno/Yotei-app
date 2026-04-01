package com.yotei.tv.data

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP client for communicating with TurnoExpress backend.
 * Handles device registration and pairing APIs.
 */
class PairingApiClient(
    private val baseUrl: String,
    private val anonKey: String
) {
    companion object {
        private const val TAG = "PairingApiClient"
        private val json = Json { ignoreUnknownKeys = true }
    }

    // ──────────────────────────────────────────────────────────────────────────────

    @Serializable
    data class RegisterDeviceRequest(
        val device_id: String,
        val device_name: String,
        val device_model: String,
        val barbershop_id: String
    )

    @Serializable
    data class RegisterDeviceProvisionalRequest(
        val device_id: String,
        val device_name: String,
        val device_model: String
    )

    @Serializable
    data class RegisterDeviceResponse(
        val device_id: String,
        val device_status: String,
        val registered_at: String
    )

    @Serializable
    data class GetBindingResponse(
        val binding_id: String,
        val device_id: String,
        val display_id: String,
        val barbershop_id: String,
        val binding_status: String,
        val display_config: JsonElement? = null,  // ✅ Use JsonElement for flexible nested structures
        val device_secret: String,
        val created_at: String,
        val message: String? = null
    )

    @Serializable
    data class PairingCodeStatusResponse(
        val code_status: String,  // "pending", "used", or "expired"
        val expires_in_seconds: Int? = null,  // Remaining time if pending (202)
        val binding_id: String? = null,  // Binding ID if redeemed (200)
        val display_id: String? = null,  // Display ID if redeemed (200)
        val barbershop_id: String? = null,  // Barbershop ID if redeemed (200)
        val display_config: Map<String, String>? = null,  // Display config if redeemed (200)
        val message: String? = null
    )

    @Serializable
    data class ErrorResponse(
        val error: String
    )

    // ──────────────────────────────────────────────────────────────────────────────
    // API CALLS
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Register device as PROVISIONAL on first launch (Option 3 flow).
     * NEW ENDPOINT: POST /api/devices/register-provisional
     *
     * Device is registered WITHOUT barbershop_id (registration_stage='provisional')
     * Allows device to generate pairing code before staff selects barbershop.
     *
     * @return Device registration response, or error
     */
    suspend fun registerDeviceProvisional(
        deviceId: String,
        deviceName: String,
        deviceModel: String
    ): Result<RegisterDeviceResponse> {
        return try {
            val request = RegisterDeviceProvisionalRequest(
                device_id = deviceId,
                device_name = deviceName,
                device_model = deviceModel
            )

            val response = makeRequest(
                method = "POST",
                path = "/api/devices/register-provisional",
                body = json.encodeToString(request)
            )

            if (response.statusCode == 201 || response.statusCode == 200) {
                val data = json.decodeFromString<RegisterDeviceResponse>(response.body)
                Result.success(data)
            } else {
                val error = json.decodeFromString<ErrorResponse>(response.body)
                Result.failure(Exception(error.error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerDeviceProvisional failed", e)
            Result.failure(e)
        }
    }

    /**
     * Register device on first launch (OLD flow - still supported).
     * POST /api/devices
     *
     * @return Device registration response, or null on error
     */
    suspend fun registerDevice(
        deviceId: String,
        deviceName: String,
        deviceModel: String,
        barbershopId: String
    ): Result<RegisterDeviceResponse> {
        return try {
            val request = RegisterDeviceRequest(
                device_id = deviceId,
                device_name = deviceName,
                device_model = deviceModel,
                barbershop_id = barbershopId
            )

            val response = makeRequest(
                method = "POST",
                path = "/api/devices",
                body = json.encodeToString(request)
            )

            if (response.statusCode == 201) {
                val data = json.decodeFromString<RegisterDeviceResponse>(response.body)
                Result.success(data)
            } else {
                val error = json.decodeFromString<ErrorResponse>(response.body)
                Result.failure(Exception(error.error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerDevice failed", e)
            Result.failure(e)
        }
    }

    /**
     * Get active binding for this device (includes display config).
     * GET /api/devices/{device-id}/binding
     *
     * Requires: deviceSecret in Authorization header
     */
    suspend fun getDeviceBinding(
        deviceId: String,
        deviceSecret: String
    ): Result<GetBindingResponse> {
        return try {
            val response = makeRequest(
                method = "GET",
                path = "/api/devices/$deviceId/binding",
                headers = mapOf(
                    "Authorization" to "Bearer $deviceSecret",
                    "X-Device-Id" to deviceId
                )
            )

            when (response.statusCode) {
                200 -> {
                    val data = json.decodeFromString<GetBindingResponse>(response.body)
                    Result.success(data)
                }
                404 -> Result.failure(Exception("No active binding found"))
                401 -> Result.failure(Exception("Invalid device secret"))
                403 -> Result.failure(Exception("Device not active"))
                else -> {
                    val error = json.decodeFromString<ErrorResponse>(response.body)
                    Result.failure(Exception(error.error))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getDeviceBinding failed", e)
            Result.failure(e)
        }
    }

    /**
     * Poll pairing code status while waiting for staff to redeem.
     * GET /api/devices/{device-id}/pairing
     *
     * Requires: deviceSecret in Authorization header
     *
     * Returns:
     * - 202 Accepted: Code pending (expires_in_seconds)
     * - 200 OK: Code redeemed (binding details)
     * - 410 Gone: Code expired
     */
    suspend fun getPairingCodeStatus(
        deviceId: String,
        deviceSecret: String
    ): Result<PairingCodeStatusResponse> {
        return try {
            val response = makeRequest(
                method = "GET",
                path = "/api/devices/$deviceId/pairing",
                headers = mapOf(
                    "Authorization" to "Bearer $deviceSecret",
                    "X-Device-Id" to deviceId
                )
            )

            when (response.statusCode) {
                200, 202, 410 -> {
                    val data = json.decodeFromString<PairingCodeStatusResponse>(response.body)
                    Result.success(data)
                }
                401 -> Result.failure(Exception("Invalid device secret"))
                403 -> Result.failure(Exception("Device not active"))
                404 -> Result.failure(Exception("Device not found"))
                else -> {
                    val error = json.decodeFromString<ErrorResponse>(response.body)
                    Result.failure(Exception(error.error))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPairingCodeStatus failed", e)
            Result.failure(e)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // HTTP HELPER
    // ──────────────────────────────────────────────────────────────────────────────

    private data class HttpResponse(
        val statusCode: Int,
        val body: String,
        val headers: Map<String, String>
    )

    private fun makeRequest(
        method: String,
        path: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): HttpResponse {
        val url = URL("$baseUrl$path")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = method
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            // Set default headers
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $anonKey")

            // Override with custom headers
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            // Send body if present
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { os ->
                    os.write(body.toByteArray())
                }
            }

            // Read response
            val statusCode = connection.responseCode
            val responseBody = if (statusCode < 400) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            Log.d(TAG, "$method $path → $statusCode")

            return HttpResponse(statusCode, responseBody, emptyMap())
        } finally {
            connection.disconnect()
        }
    }
}
