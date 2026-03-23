package com.yotei.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.yotei.tv.data.FakeQueueDataProvider
import com.yotei.tv.ui.QueueDisplayScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Show the full queue display with fake data
            val queueState = FakeQueueDataProvider.createFakeQueueState()
            QueueDisplayScreen(state = queueState)
        }
    }
}
