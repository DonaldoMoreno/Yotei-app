package com.yotei.tv.data

import com.yotei.coremodel.queue.*

object FakeQueueDataProvider {

    fun createFakeBarbershop(): Barbershop {
        return Barbershop(
            id = "shop-001",
            name = "Yottei Barbershop",
            address = "Calle Principal 123, CDMX",
            status = "active"
        )
    }

    fun createFakeCurrentTicket(): QueueTicket {
        return QueueTicket(
            id = "ticket-001",
            ticketNumber = 42,
            status = QueueStatus.IN_SERVICE,
            customerName = "Carlos Mendoza",
            serviceName = "Corte Clásico",
            serviceMinutes = 20,
            queuePosition = 1,
            estimatedWaitMinutes = 0,
            checkedInAt = "2024-03-22T14:30:00Z"
        )
    }

    fun createFakeQueueState(): QueueState {
        return QueueState(
            barbershop = createFakeBarbershop(),
            currentTicket = createFakeCurrentTicket(),
            nextTickets = emptyList(),
            totalInQueue = 1,
            averageServiceMinutes = 21,
            isLoading = false,
            errorMessage = null
        )
    }
}
