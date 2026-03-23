package com.yotei.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.yotei.tv.data.PairingRepository
import com.yotei.tv.data.PairingCodeManager
import com.yotei.tv.ui.screens.FirstLaunchScreen
import com.yotei.tv.ui.screens.PairingScreen
import com.yotei.tv.ui.QueueDisplayScreen
import com.yotei.tv.data.FakeQueueDataProvider
import com.yotei.coremodel.queue.Barbershop
import com.yotei.coremodel.queue.QueueState
import com.yotei.coremodel.queue.QueueTicket
import com.yotei.coremodel.queue.QueueStatus
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Main Activity for TV App.
 *
 * Orchestrates the device registration and pairing flow:
 * 1. First Launch → Device Registration (barbershop selection)
 * 2. After Registration → Pairing Screen (code display + polling)
 * 3. After Pairing → Queue Display (normal operation)
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Supabase config for device registration
            val supabaseUrl = "https://qghrryxqfxxqhmedegis.supabase.co"  // Your Supabase project
            val anonKey = "YOUR_SUPABASE_ANON_KEY"      // TODO: Use BuildConfig
            
            // TurnoExpress backend for pairing codes
            val turnoExpressUrl = "http://192.168.1.27:3000"  // Local network IP of host machine
            
            val pairingRepository = remember {
                PairingRepository(
                    context = applicationContext,
                    baseUrl = turnoExpressUrl,  // Use TurnoExpress backend, not Supabase
                    anonKey = anonKey
                )
            }

            // Initialize pairing code manager for backend communication
            val httpClient = remember {
                HttpClient {
                    install(ContentNegotiation) {
                        json(Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        })
                    }
                }
            }
            val pairingCodeManager = remember {
                PairingCodeManager(
                    httpClient = httpClient,
                    apiBaseUrl = turnoExpressUrl  // Use local backend URL for pairing codes
                )
            }

            // Provide repository to entire composable tree
            CompositionLocalProvider(LocalPairingRepository provides pairingRepository) {
                // Use a factory to create ViewModel with constructor argument
                val viewModel = remember {
                    AppStateViewModel(pairingRepository)
                }

                val appState = viewModel.appState.value
                val deviceId = viewModel.deviceId.value
                val deviceSecret = viewModel.deviceSecret.value  // Get device secret from ViewModel
                val deviceName = viewModel.deviceName.value
                val deviceModel = viewModel.deviceModel.value
                val errorMessage = viewModel.errorMessage.value
                val pollingProgress = viewModel.pollingProgress.value

                when (appState) {
                    AppStateViewModel.AppState.INITIALIZING -> {
                        // TODO: Show splash screen
                    }

                    AppStateViewModel.AppState.FIRST_LAUNCH -> {
                        FirstLaunchScreen(
                            deviceName = deviceName ?: "TV",
                            deviceModel = deviceModel ?: "Unknown",
                            onRegisterDevice = { barbershopId ->
                                viewModel.registerDevice(barbershopId)
                            }
                        )
                    }

                    AppStateViewModel.AppState.REGISTERING -> {
                        FirstLaunchScreen(
                            deviceName = deviceName ?: "TV",
                            deviceModel = deviceModel ?: "Unknown",
                            onRegisterDevice = { },
                            isLoading = true
                        )
                    }

                    AppStateViewModel.AppState.WAITING_PAIRING_CODE -> {
                        PairingScreen(
                            deviceId = deviceId ?: "",
                            deviceSecret = deviceSecret ?: "",  // NEW: Pass device secret
                            pairingCodeManager = pairingCodeManager,  // NEW: Pass pairing code manager
                            onPairingComplete = { binding ->
                                android.util.Log.d("MainActivity", "onPairingComplete callback triggered")
                                android.util.Log.d("MainActivity", "  binding.barbershop_id: ${binding.barbershop_id}")
                                viewModel.waitForPairingCodeRedemption(binding)  // ✅ PASS BINDING
                            },
                            onError = { error ->
                                // Already handled in ViewModel
                            }
                        )
                    }

                    AppStateViewModel.AppState.POLLING_BINDING -> {
                        PairingScreen(
                            deviceId = deviceId ?: "",
                            deviceSecret = deviceSecret ?: "",
                            pairingCodeManager = pairingCodeManager,
                            onPairingComplete = { binding ->
                                // If we ever reach here, transition to PAIRED with the binding
                                viewModel.waitForPairingCodeRedemption(binding)
                            },
                            onError = { }
                        )
                    }

                    AppStateViewModel.AppState.PAIRED -> {
                        // Show queue display with real data from backend
                        val barbershopId = viewModel.barbershopId.value
                        var queueState by remember { mutableStateOf<QueueState?>(null) }
                        var isLoadingQueue by remember { mutableStateOf(true) }

                        android.util.Log.d("MainActivity", "=== PAIRED STATE ENTERED ===")
                        android.util.Log.d("MainActivity", "barbershopId from viewModel: $barbershopId")

                        // Fetch real queue data from API
                        LaunchedEffect(barbershopId) {
                            android.util.Log.d("MainActivity", "LaunchedEffect triggered with barbershopId: $barbershopId")
                            
                            if (barbershopId != null && barbershopId.isNotEmpty()) {
                                try {
                                    val url = "http://192.168.1.27:3000/api/display/$barbershopId/queue-etas"
                                    android.util.Log.d("MainActivity", "Fetching queue data from: $url")
                                    
                                    val response = httpClient.get(url)
                                    android.util.Log.d("MainActivity", "Response status: ${response.status}")
                                    
                                    if (response.status.isSuccess()) {
                                        val jsonText = response.bodyAsText()
                                        android.util.Log.d("MainActivity", "Response body length: ${jsonText.length} chars")
                                        android.util.Log.d("MainActivity", "Response body (first 200 chars): ${jsonText.take(200)}")
                                        
                                        // Parse JSON and convert to QueueState
                                        val parsed = Json.decodeFromString<kotlinx.serialization.json.JsonElement>(jsonText)
                                        val queueDataMap = mutableMapOf<String, Any?>()
                                        
                                        if (parsed is kotlinx.serialization.json.JsonObject) {
                                            parsed.forEach { (key, value) ->
                                                queueDataMap[key] = jsonValueToAny(value)
                                            }
                                        }
                                        
                                        android.util.Log.d("MainActivity", "Parsed queue data map keys: ${queueDataMap.keys}")
                                        
                                        queueState = convertQueueDataToState(queueDataMap)
                                        android.util.Log.d("MainActivity", "✓ Queue state created successfully")
                                        android.util.Log.d("MainActivity", "  nextTickets count: ${queueState?.nextTickets?.size}")
                                    } else {
                                        android.util.Log.e("MainActivity", "✗ Queue fetch failed with status: ${response.status}")
                                        queueState = FakeQueueDataProvider.createFakeQueueState()
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("MainActivity", "✗ Error loading queue data: ${e.message}", e)
                                    // Fall back to fake data if fetch fails
                                    queueState = FakeQueueDataProvider.createFakeQueueState()
                                } finally {
                                    isLoadingQueue = false
                                }
                            } else {
                                android.util.Log.e("MainActivity", "✗ CRITICAL: barbershopId is null or empty!")
                                android.util.Log.e("MainActivity", "  Cannot fetch real queue data - will use fake data")
                                queueState = FakeQueueDataProvider.createFakeQueueState()
                                isLoadingQueue = false
                            }
                        }

                        if (isLoadingQueue) {
                            // Show loading state
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else if (queueState != null) {
                            QueueDisplayScreen(
                                state = queueState!!,
                                onRefreshBinding = { viewModel.refreshBinding() }
                            )
                        }
                    }

                    AppStateViewModel.AppState.ERROR -> {
                        // Show error screen with retry button
                        // TODO: Implement ErrorScreen composable
                    }
                }
            }
        }
    }
}

/**
 * Convert API response from /api/display/{barbershop_id}/queue-etas to QueueState
 */
fun convertQueueDataToState(apiData: Map<String, Any?>): QueueState {
    // Extract basic info
    val barbershopId = apiData["barbershop_id"] as? String ?: ""
    val barbershopName = apiData["barbershop_name"] as? String ?: "Barbería"
    
    // Create fake barbershop (in real impl, fetch from API)
    val barbershop = Barbershop(
        id = barbershopId,
        name = barbershopName,
        address = "Dirección",
        status = "active"
    )
    
    // Extract queue data
    val currentQueueSize = (apiData["current_queue_size"] as? Number)?.toInt() ?: 0
    val avgServiceMinutes = (apiData["avg_service_minutes"] as? Number)?.toInt() ?: 20
    
    // Extract ETAs list and convert to next tickets
    val etasJson = apiData["etas"] as? List<*> ?: emptyList<Any>()
    val nextTickets = mutableListOf<QueueTicket>()
    
    etasJson.forEachIndexed { index, etaObj ->
        if (etaObj is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val etaMap = etaObj as Map<String, Any?>
            val position = ((etaMap["position"] as? Number)?.toInt()) ?: (index + 1)
            val etaMinutes = ((etaMap["eta_minutes"] as? Number)?.toInt()) ?: 0
            
            nextTickets.add(
                QueueTicket(
                    id = "ticket-$position",
                    ticketNumber = position,
                    status = QueueStatus.WAITING,
                    customerName = "Cliente #$position",
                    serviceName = "Servicio",
                    serviceMinutes = avgServiceMinutes,
                    queuePosition = position,
                    estimatedWaitMinutes = etaMinutes,
                    checkedInAt = ""
                )
            )
        }
    }
    
    // Return QueueState
    return QueueState(
        barbershop = barbershop,
        currentTicket = null,  // No current ticket from this API
        nextTickets = nextTickets,
        totalInQueue = currentQueueSize,
        averageServiceMinutes = avgServiceMinutes,
        isLoading = false,
        errorMessage = null
    )
}

/**
 * Convert JsonElement to Any for dynamic parsing
 */
fun jsonValueToAny(element: JsonElement): Any? =
    when {
        element is JsonNull -> null
        element is JsonPrimitive -> {
            val prim = element
            when {
                prim.isString -> prim.content
                prim.content == "true" -> true
                prim.content == "false" -> false
                else -> {
                    // Try to parse as number
                    prim.content.toIntOrNull() ?: prim.content.toDoubleOrNull() ?: prim.content
                }
            }
        }
        element is JsonArray -> element.map { jsonValueToAny(it) }
        element is JsonObject -> element.mapValues { (_, v) -> jsonValueToAny(v) }
        else -> null
    }
