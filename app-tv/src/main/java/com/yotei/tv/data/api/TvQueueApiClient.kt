package com.yotei.tv.data.api

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * API client for queue-related endpoints.
 * Responsible for all queue data fetching from backend.
 */
class TvQueueApiClient(
    private val baseUrl: String,
    private val httpClient: HttpClient = createDefaultHttpClient()
) {
    companion object {
        private const val TAG = "TvQueueApiClient"

        private fun createDefaultHttpClient(): HttpClient {
            return HttpClient {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    })
                }
            }
        }
    }

    /**
     * API response data class for queue ETAs.
     * Maps directly to backend response structure.
     */
    @Serializable
    data class QueueEtasResponse(
        val display_id: String = "",
        val barbershop_id: String = "",
        val barbershop_name: String = "",
        val current_queue_size: Int = 0,
        val active_barbers: Int = 0,
        val average_eta_minutes: Int = 0,
        val avg_service_minutes: Int = 0,
        val etas: List<TicketEta> = emptyList()
    )

    @Serializable
    data class TicketEta(
        val ticket_id: String = "",
        val ticket_number: Int = 0,
        val eta_minutes: Int = 0
    )

    /**
     * Fetch queue ETAs for a display.
     * This is the main API call for real-time queue updates.
     *
     * @param displayId The display ID to fetch queue for
     * @param barbershopId The barbershop ID (for logging/filtering)
     * @return QueueEtasResponse with current queue data
     * @throws Exception if API call fails
     */
    suspend fun getQueueEtas(
        displayId: String,
        barbershopId: String
    ): QueueEtasResponse {
        val url = "$baseUrl/api/display/$displayId/queue-etas"
        Log.d(TAG, "Fetching queue ETAs from: $url")

        return try {
            val response = httpClient.get(url)

            if (response.status.isSuccess()) {
                val bodyText = response.bodyAsText()
                Log.d(TAG, "Queue fetch successful (${bodyText.length} bytes)")

                val jsonParser = Json { ignoreUnknownKeys = true }
                val queueData = jsonParser.decodeFromString<QueueEtasResponse>(bodyText)
                Log.d(
                    TAG,
                    "Parsed queue: ${queueData.barbershop_name}, size=${queueData.current_queue_size}, etas=${queueData.etas.size}"
                )

                queueData
            } else {
                Log.e(TAG, "Queue fetch failed with status: ${response.status}")
                throw Exception("Queue API returned ${response.status}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching queue ETAs: ${e.message}", e)
            throw e
        }
    }

    /**
     * Fetch display configuration (optional - for future use).
     * Can include display-specific settings like refresh rate, colors, etc.
     */
    suspend fun getDisplayConfig(displayId: String): Map<String, Any> {
        return mapOf(
            "displayId" to displayId,
            "refreshInterval" to 5000 // ms
        )
    }
}
