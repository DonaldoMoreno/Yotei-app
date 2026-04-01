package com.yotei.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun QueueDisplayHeader(
    shopName: String,
    shopAddress: String,
    modifier: Modifier = Modifier
) {
    // ✅ FIXED: Use mutableStateOf to allow updates
    val now = remember { mutableStateOf(LocalDateTime.now()) }
    
    // ✅ NEW: Update clock every second
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)  // Wait 1 second
            now.value = LocalDateTime.now()  // Update the time
        }
    }
    
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMM", java.util.Locale("es", "MX"))
    val formattedTime = now.value.format(timeFormatter)
    val formattedDate = now.value.format(dateFormatter)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo and shop info
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo box
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Y",
                    style = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C5A5E)
                    )
                )
            }

            Column {
                Text(
                    text = shopName,
                    style = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = shopAddress,
                    style = TextStyle(
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                )
            }
        }

        // Time display
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = formattedTime,
                style = TextStyle(
                    fontSize = 48.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Text(
                text = formattedDate,
                style = TextStyle(
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            )
        }
    }
}
