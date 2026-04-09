package com.yotei.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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

@Composable
fun NextTicketsSection(
    tickets: List<QueueTicket>,
    modifier: Modifier = Modifier
) {
    if (tickets.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "SIGUIENTES TURNOS",
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 1.5.sp
            )
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = tickets,
                key = { ticket -> ticket.id }  // ✅ Stable key for proper recomposition
            ) { ticket ->
                NextTicketCard(
                    ticket = ticket,
                    position = tickets.indexOf(ticket) + 1
                )
            }
        }
    }
}

@Composable
fun NextTicketCard(
    ticket: QueueTicket,
    position: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Position badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(50.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "#$position en fila",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                if (!ticket.checkedInAt.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color(0xFF10B981).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "✓ Presente",
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = Color(0xFF10B981)
                            )
                        )
                    }
                }
            }

            // Ticket number
            Text(
                text = "#${String.format("%03d", ticket.ticketNumber)}",
                style = TextStyle(
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C5A5E)
                )
            )

            // Customer name
            Text(
                text = ticket.customerName,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                ),
                maxLines = 1
            )

            // Wait time
            Text(
                text = if (ticket.estimatedWaitMinutes == 0) "¡Próximo!" else "~${ticket.estimatedWaitMinutes} min de espera",
                style = TextStyle(
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
            )
        }
    }
}
