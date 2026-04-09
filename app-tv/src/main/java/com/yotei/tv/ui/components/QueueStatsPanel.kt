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
import com.yotei.coremodel.queue.Barbershop

@Composable
fun QueueStatsPanel(
    barbershop: Barbershop,
    totalInQueue: Int,
    estimatedWaitMinutes: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Queue count
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "👥",
                    style = TextStyle(fontSize = 28.sp),
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "$totalInQueue",
                        style = TextStyle(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "en fila",
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }

        // Estimated wait time - always show
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Tiempo estimado",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    ),
                    maxLines = 1
                )
                Text(
                    text = if (estimatedWaitMinutes == 0) 
                        "Sin clientes" 
                    else 
                        "~${estimatedWaitMinutes} min",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    ),
                    maxLines = 1
                )
            }
        }

        // Shop status
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (barbershop.status == "active") 
                        Color(0xFF10B981).copy(alpha = 0.2f) 
                    else 
                        Color(0xFFEF4444).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = if (barbershop.status == "active") 
                                Color(0xFF10B981) 
                            else 
                                Color(0xFFEF4444),
                            shape = RoundedCornerShape(50.dp)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (barbershop.status == "active") "Abierto" else "Cerrado",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (barbershop.status == "active") 
                            Color(0xFF10B981) 
                        else 
                            Color(0xFFEF4444)
                    )
                )
            }
        }
    }
}
