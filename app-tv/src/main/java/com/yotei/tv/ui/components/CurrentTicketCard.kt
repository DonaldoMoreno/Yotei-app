package com.yotei.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yotei.coremodel.queue.QueueTicket
import com.yotei.coremodel.queue.QueueStatus

@Composable
fun CurrentTicketCard(
    ticket: QueueTicket?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = Color(0xFF1E293B).copy(alpha = 0.5f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(28.dp)
    ) {
        if (ticket != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // Ticket number - large display
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFF1C5A5E),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#${String.format("%03d", ticket.ticketNumber)}",
                        style = TextStyle(
                            fontSize = 65.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                // Ticket details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Customer name
                    Text(
                        text = ticket.customerName,
                        style = TextStyle(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )

                    // Status badge
                    val statusColor = getStatusColor(ticket.status, !ticket.checkedInAt.isNullOrEmpty())
                    val statusLabel = getStatusLabel(ticket.status, !ticket.checkedInAt.isNullOrEmpty())
                    
                    Box(
                        modifier = Modifier
                            .background(
                                color = statusColor,
                                shape = RoundedCornerShape(50.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }
        } else {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⏱",
                        style = TextStyle(fontSize = 48.sp)
                    )
                }
                Text(
                    text = "Sin turnos en espera",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                )
                Text(
                    text = "Escanea el código QR para unirte a la fila",
                    style = TextStyle(
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

private fun getStatusColor(status: QueueStatus, checkedIn: Boolean): Color {
    return when {
        status == QueueStatus.WAITING && checkedIn -> Color(0xFF10B981)
        status == QueueStatus.WINDOW_BOOKED || status == QueueStatus.AWAITING_ACTIVATION -> Color(0xFFA855F7)
        status == QueueStatus.WAITING -> Color(0xFFEAB308)
        status == QueueStatus.READY -> Color(0xFF10B981)
        status == QueueStatus.SKIPPED_NOT_PRESENT -> Color(0xFFF97316)
        status == QueueStatus.CALLED -> Color(0xFF3B82F6)
        status == QueueStatus.IN_SERVICE -> Color(0xFF10B981)
        else -> Color(0xFF6B7280)
    }
}

private fun getStatusLabel(status: QueueStatus, checkedIn: Boolean): String {
    return when {
        status == QueueStatus.WAITING && checkedIn -> "Confirmado"
        status == QueueStatus.WINDOW_BOOKED -> "Reservado"
        status == QueueStatus.AWAITING_ACTIVATION -> "Activando"
        status == QueueStatus.WAITING -> "En espera"
        status == QueueStatus.READY -> "Confirmado"
        status == QueueStatus.SKIPPED_NOT_PRESENT -> "Ausente"
        status == QueueStatus.CALLED -> "Llamado"
        status == QueueStatus.IN_SERVICE -> "En atención"
        else -> status.name
    }
}
