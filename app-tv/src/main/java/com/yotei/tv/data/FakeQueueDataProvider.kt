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
            nextTickets = listOf(
                QueueTicket(
                    id = "ticket-002",
                    ticketNumber = 43,
                    status = QueueStatus.WAITING,
                    customerName = "Juan Rivera",
                    serviceName = "Corte Clásico",
                    serviceMinutes = 20,
                    queuePosition = 2,
                    estimatedWaitMinutes = 20,
                    checkedInAt = "2024-03-22T14:25:00Z"
                ),
                QueueTicket(
                    id = "ticket-003",
                    ticketNumber = 44,
                    status = QueueStatus.WAITING,
                    customerName = "Miguel López",
                    serviceName = "Corte Fade",
                    serviceMinutes = 25,
                    queuePosition = 3,
                    estimatedWaitMinutes = 45,
                    checkedInAt = "2024-03-22T14:20:00Z"
                ),
                QueueTicket(
                    id = "ticket-004",
                    ticketNumber = 45,
                    status = QueueStatus.WAITING,
                    customerName = "Roberto García",
                    serviceName = "Afeitado",
                    serviceMinutes = 15,
                    queuePosition = 4,
                    estimatedWaitMinutes = 65,
                    checkedInAt = "2024-03-22T14:15:00Z"
                )
            ),
            totalInQueue = 4,
            averageServiceMinutes = 21,
            isLoading = false,
            errorMessage = null
        )
    }
}
