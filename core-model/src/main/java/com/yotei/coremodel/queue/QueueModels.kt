package com.yotei.coremodel.queue

/**
 * Core queue domain models for Yotei
 */

// Status types matching the web implementation
enum class QueueStatus {
    WINDOW_BOOKED,
    AWAITING_ACTIVATION,
    WAITING,
    READY,
    SKIPPED_NOT_PRESENT,
    CALLED,
    IN_SERVICE
}

// A ticket in the queue
data class QueueTicket(
    val id: String,
    val ticketNumber: Int,
    val status: QueueStatus,
    val customerName: String,
    val serviceName: String,
    val serviceMinutes: Int,
    val queuePosition: Int = 0,
    val estimatedWaitMinutes: Int = 0,
    val checkedInAt: String? = null,
    val barbershopId: String? = null
)

// Barbershop info
data class Barbershop(
    val id: String,
    val name: String,
    val address: String,
    val status: String // "active", "closed", etc
)

// Queue state snapshot
data class QueueState(
    val barbershop: Barbershop,
    val currentTicket: QueueTicket?,
    val nextTickets: List<QueueTicket> = emptyList(),
    val totalInQueue: Int = 0,
    val averageServiceMinutes: Int = 15,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

fun formatTicketNumber(number: Int): String {
    return String.format("%03d", number)
}

