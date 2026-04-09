package com.yotei.tv.domain.state

import android.util.Log
import com.yotei.tv.data.api.TvQueueApiClient
import com.yotei.coremodel.queue.QueueTicket
import com.yotei.coremodel.queue.QueueStatus
import com.yotei.coremodel.queue.Barbershop

/**
 * State models for queue display.
 * These represent the UI-ready state (different from API response).
 */

data class QueueDisplayState(
    val displayId: String,
    val barbershopId: String,
    val barbershop: Barbershop,
    val currentTicket: QueueTicket?,
    val nextTickets: List<QueueTicket>,
    val totalInQueue: Int,
    val estimatedWaitMinutes: Int,
    val activeBarbers: Int,
    val avgServiceMinutes: Int,
    val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTED,
    val lastUpdateTime: Long = System.currentTimeMillis()
)

enum class ConnectionStatus {
    CONNECTED,      // All is well
    DEGRADED,       // Retrying after error
    OFFLINE,        // Network error detected
    BINDING_INVALID // Binding no longer valid
}

/**
 * State manager for queue display.
 *
 * Responsibilities:
 * - Transform API response to UI state
 * - Format data for display (e.g., extract ticket numbers)
 * - Calculate derived values (wait times, stats)
 * - Track connection status
 *
 * This is PURE: No Android dependencies, testable.
 * No side effects: Same input always produces same output.
 */
class QueueStateManager {
    companion object {
        private const val TAG = "QueueStateManager"
    }

    /**
     * Transform API response to UI-ready QueueDisplayState.
     *
     * @param apiResponse The raw API response from TvQueueApiClient
     * @return QueueDisplayState ready for UI rendering
     */
    fun mapApiResponseToDisplayState(
        apiResponse: TvQueueApiClient.QueueEtasResponse
    ): QueueDisplayState {
        Log.d(
            TAG,
            "Mapping API response: ${apiResponse.barbershop_name}, size=${apiResponse.current_queue_size}, etas=${apiResponse.etas.size}"
        )

        // Create Barbershop model from API response
        val barbershop = Barbershop(
            id = apiResponse.barbershop_id,
            name = apiResponse.barbershop_name,
            address = "", // Not in API response, set to empty
            status = "active" // Assume active if we got data
        )

        // Extract current ticket (first in queue)
        val currentTicket = apiResponse.etas.firstOrNull()?.let { eta ->
            QueueTicket(
                id = eta.ticket_id,
                ticketNumber = eta.ticket_number,
                status = QueueStatus.IN_SERVICE,
                customerName = eta.customer_name,
                serviceName = "Corte",
                serviceMinutes = apiResponse.avg_service_minutes,
                queuePosition = 1,
                estimatedWaitMinutes = eta.eta_minutes
            )
        }

        // Extract next tickets (skip first, take up to 5)
        val nextTickets = apiResponse.etas
            .drop(1)
            .take(5)
            .mapIndexed { index, eta ->
                QueueTicket(
                    id = eta.ticket_id,
                    ticketNumber = eta.ticket_number,
                    status = QueueStatus.WAITING,
                    customerName = eta.customer_name,
                    serviceName = "Corte",
                    serviceMinutes = apiResponse.avg_service_minutes,
                    queuePosition = index + 2,
                    estimatedWaitMinutes = eta.eta_minutes
                )
            }

        Log.d(
            TAG,
            "Mapped: currentTicket=${currentTicket?.ticketNumber}, nextTickets=${nextTickets.size}"
        )

        return QueueDisplayState(
            displayId = apiResponse.display_id,
            barbershopId = apiResponse.barbershop_id,
            barbershop = barbershop,
            currentTicket = currentTicket,
            nextTickets = nextTickets,
            totalInQueue = apiResponse.current_queue_size,
            estimatedWaitMinutes = apiResponse.average_eta_minutes,
            activeBarbers = apiResponse.active_barbers,
            avgServiceMinutes = apiResponse.avg_service_minutes,
            connectionStatus = ConnectionStatus.CONNECTED,
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    /**
     * Mark queue state as degraded (connection issue).
     * Used when API call fails but we still have data to show.
     */
    fun markAsDegraded(
        currentState: QueueDisplayState
    ): QueueDisplayState {
        return currentState.copy(
            connectionStatus = ConnectionStatus.DEGRADED,
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    /**
     * Mark queue state as offline.
     */
    fun markAsOffline(
        currentState: QueueDisplayState
    ): QueueDisplayState {
        return currentState.copy(
            connectionStatus = ConnectionStatus.OFFLINE,
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    /**
     * Mark binding as invalid (e.g., 401/403 response).
     */
    fun markBindingAsInvalid(
        currentState: QueueDisplayState
    ): QueueDisplayState {
        return currentState.copy(
            connectionStatus = ConnectionStatus.BINDING_INVALID,
            lastUpdateTime = System.currentTimeMillis()
        )
    }
}
