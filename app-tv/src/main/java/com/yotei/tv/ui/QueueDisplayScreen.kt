package com.yotei.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yotei.coremodel.queue.Barbershop
import com.yotei.coremodel.queue.QueueState
import com.yotei.coremodel.queue.QueueTicket
import com.yotei.coremodel.queue.QueueStatus
import com.yotei.tv.ui.components.*
import com.yotei.tv.ui.viewmodel.TvQueueViewModel
import com.yotei.tv.domain.state.QueueDisplayState
import com.yotei.tv.domain.state.ConnectionStatus

/**
 * Updated QueueDisplayScreen that uses live data from TvQueueViewModel.
 * 
 * This new version collects queue state from the ViewModel's Flow
 * and displays real-time updates every 5 seconds.
 * 
 * @param viewModel The TvQueueViewModel providing live queue data
 * @param modifier Layout modifier
 */
@Composable
fun QueueDisplayScreenLive(
    viewModel: TvQueueViewModel,
    modifier: Modifier = Modifier
) {
    // Collect queue state and connection status from ViewModel
    val queueDisplayState = viewModel.queueState.collectAsState()
    val connectionStatus = viewModel.connectionStatus.collectAsState()
    
    // Convert to the display format and render
    if (queueDisplayState.value != null) {
        val displayState = queueDisplayState.value!!
        QueueDisplayScreenContent(
            displayState = displayState,
            connectionStatus = connectionStatus.value,
            modifier = modifier
        )
    } else {
        // Show loading state while waiting for first data
        Box(
            modifier = modifier.fillMaxSize()
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            LoadingState()
        }
    }
}

/**
 * Renders the actual queue display content given a display state.
 * Extracted for reusability with both old and new data sources.
 */
@Composable
private fun QueueDisplayScreenContent(
    displayState: QueueDisplayState,
    connectionStatus: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E293B),
                        Color(0xFF0F172A)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with shop info and live clock
            QueueDisplayHeader(
                shopName = displayState.barbershop.name,
                shopAddress = displayState.barbershop.address
            )

            // Connection status indicator overlay (if degraded/offline)
            if (connectionStatus != ConnectionStatus.CONNECTED) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when (connectionStatus) {
                                ConnectionStatus.DEGRADED -> Color(0xFFFBBF24)
                                ConnectionStatus.OFFLINE -> Color(0xFFF87171)
                                ConnectionStatus.BINDING_INVALID -> Color(0xFFF87171)
                                else -> Color.Transparent
                            }
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (connectionStatus) {
                            ConnectionStatus.DEGRADED -> "Reconectando..."
                            ConnectionStatus.OFFLINE -> "Sin conexión"
                            ConnectionStatus.BINDING_INVALID -> "Desvinculada"
                            else -> ""
                        },
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    )
                }
            }

            // Main content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Main content area
                    Column(
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Current ticket - fixed size
                        if (displayState.currentTicket != null) {
                            CurrentTicketCard(
                                ticket = displayState.currentTicket,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                            )
                        }

                        // Next tickets - expandable, takes remaining space
                        NextTicketsSection(
                            tickets = displayState.nextTickets,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }

                    // Right sidebar - Queue stats
                    QueueStatsPanel(
                        barbershop = displayState.barbershop,
                        totalInQueue = displayState.totalInQueue,
                        estimatedWaitMinutes = displayState.estimatedWaitMinutes,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // Footer - removed to gain space for ETA display
        }
    }
}

/**
 * Legacy QueueDisplayScreen for backward compatibility.
 * Use QueueDisplayScreenLive for the new ViewModel-based version.
 */
@Composable
fun QueueDisplayScreen(
    state: QueueState,
    modifier: Modifier = Modifier,
    onRefreshBinding: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E293B),
                        Color(0xFF0F172A)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with shop info and live clock
            QueueDisplayHeader(
                shopName = state.barbershop.name,
                shopAddress = state.barbershop.address
            )

            // Main content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            ) {
                if (state.isLoading) {
                    LoadingState(modifier = Modifier.align(Alignment.Center))
                } else if (state.errorMessage != null) {
                    ErrorState(
                        error = state.errorMessage ?: "Unknown error",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Main content area (left 2 cols)
                        Column(
                            modifier = Modifier
                                .weight(2f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // Current ticket
                            CurrentTicketCard(
                                ticket = state.currentTicket,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Next tickets
                            NextTicketsSection(
                                tickets = state.nextTickets,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Right sidebar - Queue stats
                        QueueStatsPanel(
                            barbershop = state.barbershop,
                            totalInQueue = state.totalInQueue,
                            estimatedWaitMinutes = calculateEstimatedWait(
                                state.nextTickets,
                                state.averageServiceMinutes
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }

            // Footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Powered by YOTTEI • Sistema de turnos inteligente",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = androidx.compose.foundation.shape.CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⟳",
                style = TextStyle(
                    fontSize = 32.sp,
                    color = Color.White
                )
            )
        }
        Text(
            text = "Cargando...",
            style = TextStyle(
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        )
    }
}

@Composable
fun ErrorState(
    error: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    color = Color(0xFFEF4444).copy(alpha = 0.2f),
                    shape = androidx.compose.foundation.shape.CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚠",
                style = TextStyle(
                    fontSize = 36.sp
                )
            )
        }
        Text(
            text = "Error",
            style = TextStyle(
                fontSize = 24.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = Color.White
            )
        )
        Text(
            text = error,
            style = TextStyle(
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        )
    }
}

fun calculateEstimatedWait(tickets: List<QueueTicket>, avgServiceMinutes: Int): Int {
    return if (tickets.isEmpty()) 0 else tickets.sumOf { it.estimatedWaitMinutes } / tickets.size
}
