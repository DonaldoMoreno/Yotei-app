# TV App Migration Plan: From Current to Target Architecture
**Status**: Post-Design, Pre-Implementation  
**Audience**: Development team  
**Goal**: Migrate incrementally while preserving working pairing flow

---

## OVERVIEW

The current TV app works well for pairing and initial queue display. This plan evolves it into a production-ready system with real-time updates, proper state management, and resilient error handling.

**Key principle**: Every phase is **shippable**. No breaking changes to users.

---

## PHASE 0: CURRENT STATE (As-Is)
**Duration**: Reference baseline  
**Status**: ✅ Complete (post-redesign analysis)

**What exists**:
- Two screens working: `FirstLaunchScreen`, `PairingScreen`
- Queue display static one-shot load
- Basic state machine in `AppStateViewModel`
- Pairing fully integrated and tested
- Secure storage (AES256-GCM) ✅

**What's missing**:
- Real-time queue updates
- Error recovery screens
- Proper repository layer for queue
- State manager separation

---

## PHASE 1: ORGANIZE + EXTRACT
**Duration**: 1-2 days  
**Goal**: Move code to proper layers without changing behavior  
**Breaking changes**: None  
**Ship after**: Yes, this is stable

### 1.1 Create Domain Layer Structure

```
app-tv/src/main/java/com/yotei/tv/
  ├─ domain/                          [NEW]
  │  ├─ state/
  │  │  ├─ TvAppState.kt             [NEW - move from AppStateViewModel]
  │  │  ├─ PairingState.kt           [NEW - substates]
  │  │  └─ TvAppStateManager.kt       [NEW - state machine, basic version]
  │  │
  │  └─ usecase/
  │     ├─ PairingUseCase.kt         [NEW - coordinates pairing steps]
  │     └─ QueueUseCase.kt           [NEW - placeholder for Phase 2]
  │
  ├─ data/                            [EXISTING - refactor]
  │  ├─ repository/
  │  │  ├─ PairingRepository.kt      [KEEP - move here if in app-tv/]
  │  │  └─ [QueueRepository.kt]      [PLACEHOLDER for Phase 2]
  │  │
  │  ├─ api/
  │  │  ├─ TvApiClient.kt            [NEW - unified HTTP client]
  │  │  ├─ TvPairingApiClient.kt     [NEW - pairing endpoints]
  │  │  ├─ TvQueueApiClient.kt       [NEW - queue endpoints]
  │  │  └─ TvConfigApiClient.kt      [NEW - config endpoints]
  │  │
  │  ├─ local/
  │  │  ├─ DeviceIdentityManager.kt  [KEEP]
  │  │  └─ PairingStateCache.kt      [NEW - binding caching]
  │  │
  │  └─ model/
  │     └─ [API response DTOs]       [NEW]
  │
  └─ ui/                              [EXISTING]
     ├─ screens/
     │  ├─ FirstLaunchScreen.kt      [KEEP]
     │  ├─ PairingScreen.kt          [KEEP]
     │  ├─ QueueDisplayScreen.kt     [KEEP]
     │  ├─ BootScreen.kt             [NEW - Phase 2]
     │  └─ ErrorScreen.kt            [NEW - Phase 2]
     │
     ├─ components/
     │  ├─ [Existing cards/panels]   [KEEP]
     │  └─ ConnectionStatusIndicator.kt [NEW - Phase 2]
     │
     └─ TvAppCoordinator.kt          [NEW - navigation], BootScreen.kt             [PLACEHOLDER for Phase 2]
     ├─ ErrorScreen.kt            [PLACEHOLDER for Phase 2]
     │
     └─ viewmodel/
        ├─ TvAppViewModel.kt         [RENAME from AppStateViewModel]
        ├─ TvPairingViewModel.kt     [NEW - pairing-specific state]
        └─ TvQueueViewModel.kt       [NEW - queue-specific state]
```

### 1.2 Create TvAppStateManager (First Version)

**Location**: `app-tv/domain/state/TvAppStateManager.kt`

```kotlin
/**
 * Central state machine for TV app. Holds ground truth of app state.
 * Version 1: Basic transitions, no recovery logic yet.
 */
class TvAppStateManager(
    private val pairingRepository: PairingRepository
) {
    private val _appState = MutableStateFlow(TvAppState.UNINITIALIZED)
    val appState: StateFlow<TvAppState> = _appState.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    suspend fun initialize() {
        _appState.value = TvAppState.BOOTSTRAPPING
        // Check if device already registered
        val deviceId = pairingRepository.getDeviceId()
        if (deviceId != null) {
            // Device exists, check binding
            val binding = pairingRepository.getCachedBinding()
            if (binding != null) {
                _appState.value = TvAppState.PAIRED
            } else {
                _appState.value = TvAppState.AWAITING_PAIRING
            }
        } else {
            _appState.value = TvAppState.FIRST_LAUNCH
        }
    }
    
    suspend fun registerDevice(barbershopId: String) {
        _appState.value = TvAppState.REGISTERING
        try {
            pairingRepository.registerDevice(barbershopId)
            _appState.value = TvAppState.AWAITING_PAIRING
        } catch (e: Exception) {
            _errorMessage.value = e.message
            _appState.value = TvAppState.REGISTRATION_FAILED
        }
    }
    
    suspend fun startPairing() {
        _appState.value = TvAppState.PAIRING_IN_PROGRESS
        // Code generation + polling handled in repository
    }
    
    suspend fun onPairingCodeRedeemed(binding: PairingBinding) {
        _appState.value = TvAppState.PAIRED
        // Binding saved to local storage by PairingRepository
    }
    
    // More methods in Phase 3
}
```

### 1.3 Create TvAppViewModel (Rename from AppStateViewModel)

**Location**: `app-tv/ui/viewmodel/TvAppViewModel.kt`

```kotlin
class TvAppViewModel(
    private val stateManager: TvAppStateManager,
    private val pairingRepository: PairingRepository
) : ViewModel() {
    
    val appState = stateManager.appState
    val errorMessage = stateManager.errorMessage
    
    fun registerDevice(barbershopId: String) {
        viewModelScope.launch {
            stateManager.registerDevice(barbershopId)
        }
    }
    
    fun startPairing() {
        viewModelScope.launch {
            stateManager.startPairing()
        }
    }
    
    // ViewModels for finer-grained state in Phase 2
}
```

### 1.4 Update MainActivity to Use New Structure

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val pairingRepository = remember { createPairingRepository() }
            val stateManager = remember { TvAppStateManager(pairingRepository) }
            val viewModel = remember { TvAppViewModel(stateManager, pairingRepository) }
            
            // Observe app state
            val appState = viewModel.appState.collectAsState()
            
            when (appState.value) {
                TvAppState.UNINITIALIZED -> { /* null */ }
                TvAppState.BOOTSTRAPPING -> { /* null or splash */ }
                TvAppState.FIRST_LAUNCH -> FirstLaunchScreen(...)
                TvAppState.REGISTERING -> FirstLaunchScreen(..., isLoading = true)
                TvAppState.AWAITING_PAIRING -> PairingScreen(...)
                TvAppState.PAIRING_IN_PROGRESS -> PairingScreen(...)
                TvAppState.PAIRED -> QueueDisplayScreen(...)
                // More cases added in Phase 2
                else -> Text("Error state: ${appState.value}")
            }
        }
    }
}
```

### 1.5 Files to Create/Move

| File | Action | Reason |
|------|--------|--------|
| `domain/state/TvAppState.kt` | Create | Extract enum from AppStateViewModel |
| `domain/state/TvAppStateManager.kt` | Create | Central state machine |
| `domain/state/PairingState.kt` | Create | Pairing substates |
| `ui/viewmodel/TvAppViewModel.kt` | Create (rename from AppStateViewModel) | Clearer naming |
| `data/repository/PairingRepository.kt` | Move (if in app-tv) | Shared responsibility |
| `data/api/TvApiClient.kt` | Create | Unified HTTP client |
| `data/local/PairingStateCache.kt` | Create | Binding caching |
| `ui/TvAppCoordinator.kt` | Create | Navigation coordinator |

### 1.6 Testing After Phase 1

```
✅ PairingScreen still generates code
✅ Code still polls
✅ Binding still received
✅ State machine still transitions correctly
✅ No visual changes
```

---

## PHASE 2: ADD REAL-TIME QUEUE UPDATES
**Duration**: 2-3 days  
**Goal**: Queue display refreshes in real-time every 5 seconds  
**Breaking changes**: None  
**Ship after**: Yes, major feature addition

### 2.1 Create QueueRepository

**Location**: `app-tv/data/repository/QueueRepository.kt`

```kotlin
class QueueRepository(
    private val queueApiClient: TvQueueApiClient,
    private val configRepository: ConfigRepository
) {
    
    /**
     * Fetch queue data once.
     */
    suspend fun fetchQueueData(
        displayId: String,
        barbershopId: String
    ): Result<QueueDisplayData> = runCatching {
        queueApiClient.getQueueEtas(displayId, barbershopId)
    }
    
    /**
     * Start live polling of queue data.
     * Returns a Flow that emits new queue data every 5 seconds.
     */
    fun liveQueuePolling(
        displayId: String,
        barbershopId: String,
        intervalMs: Long = 5000
    ): Flow<Result<QueueDisplayData>> = flow {
        while (currentCoroutineContext().isActive) {
            val result = try {
                val data = queueApiClient.getQueueEtas(displayId, barbershopId)
                Result.success(data)
            } catch (e: Exception) {
                Result.failure(e)
            }
            
            emit(result)
            delay(intervalMs)
        }
    }
    
    /**
     * Stop polling (via canceling the coroutine).
     */
    // Handled automatically by Flow cancellation
}
```

### 2.2 Create QueueStateManager

**Location**: `app-tv/domain/state/QueueStateManager.kt`

```kotlin
class QueueStateManager {
    
    /**
     * Transform API response to UI-ready state.
     */
    fun mapApiResponseToDisplayState(
        apiData: QueueDisplayData,
        displayConfig: DisplayConfig
    ): QueueDisplayState {
        // Format for display
        val currentTicket = getNextTicketById(apiData.etas.firstOrNull()?.ticketId)
        val nextTickets = apiData.etas
            .drop(1)
            .take(displayConfig.showNextTicketCount)
            .map { eta ->
                QueueTicket(
                    id = eta.ticketId,
                    ticketNumber = eta.ticketId.takeLast(4),
                    estimatedWaitMinutes = eta.estimatedMinutes
                )
            }
        
        return QueueDisplayState(
            displayId = apiData.displayId,
            barbershopId = apiData.barbershopId,
            barbershopName = apiData.barbershopName,
            currentTicket = currentTicket,
            nextTickets = nextTickets,
            queueStats = QueueStats(
                totalInQueue = apiData.currentQueueSize,
                estimatedWaitMinutes = apiData.averageEtaMinutes,
                activeBarbers = apiData.activeBarbers,
                avgServiceMinutes = apiData.avgServiceMinutes
            ),
            connectionStatus = ConnectionStatus.CONNECTED,
            lastUpdateTime = System.currentTimeMillis(),
            config = displayConfig
        )
    }
    
    // More helpers...
}
```

### 2.3 Create TvQueueViewModel

**Location**: `app-tv/ui/viewmodel/TvQueueViewModel.kt`

```kotlin
class TvQueueViewModel(
    private val queueRepository: QueueRepository,
    private val queueStateManager: QueueStateManager
) : ViewModel() {
    
    private val _queueState = MutableStateFlow<QueueDisplayState?>(null)
    val queueState: StateFlow<QueueDisplayState?> = _queueState.asStateFlow()
    
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    /**
     * Start live queue updates.
     * Call this when entering PAIRED state.
     */
    fun startLiveQueueUpdates(
        displayId: String,
        barbershopId: String,
        displayConfig: DisplayConfig
    ) {
        viewModelScope.launch {
            queueRepository.liveQueuePolling(displayId, barbershopId)
                .collect { result ->
                    result
                        .onSuccess { apiData ->
                            val uiState = queueStateManager.mapApiResponseToDisplayState(
                                apiData, displayConfig
                            )
                            _queueState.value = uiState
                            _connectionStatus.value = ConnectionStatus.CONNECTED
                        }
                        .onFailure { exception ->
                            TvLog.e("QueueVM", "Queue fetch failed: ${exception.message}", exception)
                            // Keep showing last known state, mark as degraded
                            _connectionStatus.value = ConnectionStatus.DEGRADED
                        }
                }
        }
    }
}
```

### 2.4 Update QueueDisplayScreen to Use Live Data

```kotlin
@Composable
fun QueueDisplayScreen(
    viewModel: TvQueueViewModel,
    // ...
) {
    val queueState = viewModel.queueState.collectAsState()
    val connectionStatus = viewModel.connectionStatus.collectAsState()
    
    Surface(modifier = Modifier.fillMaxSize()) {
        when {
            queueState.value != null -> {
                QueueContent(queueState.value!!, connectionStatus.value)
            }
            else -> {
                LoadingScreen()
            }
        }
    }
}
```

### 2.5 Update TvAppStateManager for Phase 2

Add methods to coordinate queue loading:

```kotlin
suspend fun onPairingCodeRedeemed(
    binding: PairingBinding,
    displayConfig: DisplayConfig
) {
    _appState.value = TvAppState.PAIRED
    // Queue will start fetching automatically in QueueViewModel
}
```

### 2.6 Files to Create

| File | Action |
|------|--------|
| `data/repository/QueueRepository.kt` | Create |
| `data/repository/ConfigRepository.kt` | Create |
| `data/api/TvQueueApiClient.kt` | Create |
| `domain/state/QueueStateManager.kt` | Create |
| `ui/viewmodel/TvQueueViewModel.kt` | Create |

### 2.7 Testing After Phase 2

```
✅ QueueDisplayScreen updates every 5 seconds
✅ Shows real barbershop name
✅ Shows real 1+ tickets
✅ No white screen, always has something to display
✅ Graceful fallback if API fails
```

---

## PHASE 3: ADD ERROR RECOVERY & SCREENS
**Duration**: 2-3 days  
**Goal**: Handle errors gracefully with recovery overlays  
**Breaking changes**: None  
**Ship after**: Yes, major reliability improvement

### 3.1 Extend TvAppStateManager with Recovery

```kotlin
// In TvAppStateManager
private val _connectionState = MutableStateFlow(ConnectionState())
val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

/**
 * Handle network errors during queue polling.
 */
fun onQueuePollError(exception: Exception) {
    val newState = _connectionState.value.copy(
        status = ConnectionStatus.OFFLINE,
        failureCount = _connectionState.value.failureCount + 1,
        lastErrorMessage = exception.message
    )
    _connectionState.value = newState
    
    if (_connectionState.value.failureCount == 1) {
        _appState.value = TvAppState.NETWORK_ERROR
    }
}

/**
 * Handle binding revocation (401/403 from queue API).
 */
fun onBindingRevoked() {
    _appState.value = TvAppState.PAIRING_REVOKED
}

/**
 * Attempt recovery when network restored.
 */
suspend fun attemptRecovery() {
    _appState.value = TvAppState.RECONNECTING
    
    // Try to refresh binding
    try {
        pairingRepository.refreshBinding()
        _appState.value = TvAppState.PAIRED
        _connectionState.value = _connectionState.value.copy(
            status = ConnectionStatus.CONNECTED,
            failureCount = 0
        )
    } catch (e: Exception) {
        _appState.value = TvAppState.UNRECOVERABLE_ERROR
    }
}
```

### 3.2 Create BootScreen

**Location**: `app-tv/ui/screens/BootScreen.kt`

```kotlin
@Composable
fun BootScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF10B981))
            Spacer(modifier = Modifier.height(24.dp))
            Text("Iniciando...", color = Color.White, fontSize = 24.sp)
        }
    }
}
```

### 3.3 Create ErrorScreen

**Location**: `app-tv/ui/screens/ErrorScreen.kt`

```kotlin
@Composable
fun ErrorScreen(
    state: TvAppState,
    errorMessage: String?,
    onRetry: () -> Unit,
    onReset: () -> Unit
) {
    val (title, message, icon) = when (state) {
        TvAppState.PAIRING_REVOKED -> Triple(
            "Esta pantalla fue desvinculada",
            "Por favor, contacte al personal.",
            Icons.Default.Warning
        )
        TvAppState.NETWORK_ERROR -> Triple(
            "Sin conexión",
            "Reintentando...",
            Icons.Default.CloudOff
        )
        TvAppState.UNRECOVERABLE_ERROR -> Triple(
            "Error irrecuperable",
            "Reiniciando la pantalla...",
            Icons.Default.Error
        )
        else -> Triple("Error", errorMessage ?: "Unknown error", Icons.Default.Error)
    }
    
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFF87171),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(title, fontSize = 48.sp, color = Color.White, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, fontSize = 28.sp, color = Color(0xFFCED5DC), textAlign = TextAlign.Center)
            
            if (state == TvAppState.PAIRING_REVOKED) {
                Spacer(modifier = Modifier.height(40.dp))
                Button(onClick = onReset) {
                    Text("Reintentar")
                }
            }
        }
    }
}
```

### 3.4 Create ConnectionStatusIndicator

**Location**: `app-tv/ui/components/ConnectionStatusIndicator.kt`

```kotlin
@Composable
fun ConnectionStatusIndicator(status: ConnectionStatus) {
    if (status == ConnectionStatus.CONNECTED) return
    
    val (color, text) = when (status) {
        ConnectionStatus.OFFLINE -> Color(0xFFF87171) to "Sin conexión"
        ConnectionStatus.DEGRADED -> Color(0xFFFBBF24) to "Reconectando..."
        ConnectionStatus.BINDING_INVALID -> Color(0xFFF87171) to "Desvinculada"
        else -> return
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.1f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(text, color = color, fontSize = 14.sp)
    }
}
```

### 3.5 Update MainActivity for All States

```kotlin
when (appState.value) {
    TvAppState.UNINITIALIZED -> {}
    TvAppState.BOOTSTRAPPING -> BootScreen()
    TvAppState.FIRST_LAUNCH -> FirstLaunchScreen(...)
    TvAppState.REGISTERING -> FirstLaunchScreen(..., isLoading = true)
    TvAppState.AWAITING_PAIRING -> PairingScreen(...)
    TvAppState.PAIRING_IN_PROGRESS -> PairingScreen(...)
    TvAppState.PAIRED -> QueueDisplayScreen(...) + ConnectionStatusIndicator(...)
    TvAppState.DISPLAY_READY -> QueueDisplayScreen(...) + ConnectionStatusIndicator(...)
    TvAppState.NETWORK_ERROR -> {
        Box(modifier = Modifier.fillMaxSize()) {
            QueueDisplayScreen(...)  // Show stale data
            ConnectionStatusIndicator(ConnectionStatus.OFFLINE)  // Overlay
        }
    }
    TvAppState.RECONNECTING -> ErrorScreen(state, null, onRetry, onReset)
    TvAppState.PAIRING_REVOKED -> ErrorScreen(state, "Esta pantalla fue desvinculada", onRetry, onReset)
    TvAppState.UNRECOVERABLE_ERROR -> ErrorScreen(state, viewModel.errorMessage.value, {}, onReset)
    // ... other states
}
```

### 3.6 Files to Create

| File | Action |
|------|--------|
| `ui/screens/BootScreen.kt` | Create |
| `ui/screens/ErrorScreen.kt` | Create |
| `ui/components/ConnectionStatusIndicator.kt` | Create |
| Update `TvAppStateManager.kt` | Add recovery methods |

### 3.7 Testing After Phase 3

```
✅ Pulling network cable → NETWORK_ERROR state, stale queue visible
✅ Re-connecting network → RECONNECTING, then PAIRED
✅ Binding revoked → PAIRING_REVOKED screen
✅ Back button works from error states
✅ No crashes or white screens
```

---

## PHASE 4: POLISH & OPTIMIZATION
**Duration**: 1-2 days  
**Goal**: Production readiness  
**Ship after**: Yes, final production release  

### 4.1 Add Configuration Management

Create `TvAppConfig` to eliminate hardcoded values:

```kotlin
object TvAppConfig {
    const val API_BASE_URL = BuildConfig.BACKEND_URL
    const val QUEUE_POLLING_INTERVAL_SECONDS = 5
    const val BINDING_REFRESH_INTERVAL_HOURS = 24
    const val API_TIMEOUT_SECONDS = 10
    const val PAIRING_CODE_TTL_SECONDS = 900
    const val CONNECTION_RETRY_MAX_ATTEMPTS = 5
    const val CONNECTION_RETRY_BACKOFF_SECONDS = 10
}
```

### 4.2 Consolidate HTTP Clients

- Replace `PairingCodeManager` + `PairingApiClient` with `TvApiClient`
- All requests go through one unified client with:
  - Consistent timeout handling
  - Retry logic
  - Error mapping
  - Request/response logging

### 4.3 Add Structured Logging

Create simple logging utility:

```kotlin
object TvLog {
    fun d(tag: String, msg: String) = Log.d(tag, msg)
    fun e(tag: String, msg: String, e: Exception) = Log.e(tag, msg, e)
    // ...
}
```

### 4.4 Add Unit Tests for State Managers

```
TvAppStateManagerTest
  ├─ testInitializeNewDevice()
  ├─ testPairingFlow()
  ├─ testRecoveryOnNetworkError()
  └─ testBindingRevocation()

QueueStateManagerTest
  ├─ testMapApiResponseToDisplayState()
  ├─ testNextTicketsSelection()
  └─ testEstimatedWaitCalculation()
```

### 4.5 Files to Update

| File | Action |
|------|--------|
| `app-tv/config/TvAppConfig.kt` | Create |
| `data/api/TvApiClient.kt` | Enhance |
| All API calls | Use BuildConfig instead of hardcoded URLs |
| `app-tv/util/TvLog.kt` | Create or enhance |

### 4.6 Testing After Phase 4

```
✅ All hardcoded URLs come from config
✅ Unit tests pass for state managers
✅ API requests have timeouts
✅ Retry logic works for transient failures
✅ Logging captures important events
```

---

## PHASE 5: FUTURE (POST-MVP)
**Duration**: TBD  
**Goal**: Advanced features  

### Ideas for later (do NOT implement yet):
- [ ] WebSocket for real-time updates (instead of polling)
- [ ] Room database for queue data caching
- [ ] Hilt dependency injection
- [ ] Multi-display support
- [ ] Media/animation mode hooks
- [ ] Better retry strategy (configurable backoff)
- [ ] Analytics integration

---

## DEPLOYMENT STRATEGY

### Phase 1 Release (Organize)
- **Version**: 2.1.0
- **Changes**: Refactoring only, no new UI
- **Risk**: Low (existing functionality preserved)
- **Testing**: Manual smoke test (pairing + queue display)

### Phase 2 Release (Real-Time Queue)
- **Version**: 2.2.0
- **Changes**: Queue refreshes every 5 seconds
- **Risk**: Medium (new polling loop, potential battery drain)
- **Testing**: 
  - Queue updates in real-time ✅
  - No excessive battery drain ✅
  - Graceful fallback when API fails ✅

### Phase 3 Release (Error Recovery)
- **Version**: 2.3.0
- **Changes**: Error screens, recovery overlays
- **Risk**: Low (new states that didn't exist before)
- **Testing**:
  - Killing network → stale queue + overlay ✅
  - Restoring network → recovery ✅
  - Binding revoked → clear error message ✅

### Phase 4 Release (Polish)
- **Version**: 2.4.0
- **Changes**: Config management, logging, tests
- **Risk**: Low (internal refactoring)
- **Testing**: Unit test coverage ✅

---

## ROLLBACK PLAN

If a phase breaks production:

1. **Phase 1-2 issues**: Revert to previous release
   - No data stored in new way, safe rollback
   
2. **Phase 3 issues**: If error screens malfunction
   - Revert and use simpler error handling
   
3. **Phase 4 issues**: Configuration or logging
   - Revert, keep existing hardcoded values

---

## SUCCESS CRITERIA

| Phase | Success Metric |
|-------|----------------|
| 1 | Code refactored, pairing works identically |
| 2 | Queue updates live every 5 sec with real data |
| 3 | Network errors don't crash, show recovery overlays |
| 4 | All config in BuildConfig, unit tests pass |
| Overall | **White screen issue GONE**, TV displays real queue data reliably |

---

## RESOURCE ESTIMATES

| Phase | Effort | Timeline |
|-------|--------|----------|
| 1 | 1-2 dev days | Mon-Tue |
| 2 | 2-3 dev days | Wed-Fri |
| 3 | 2-3 dev days | Mon-Wed |
| 4 | 1-2 dev days | Thu-Fri |
| **Total** | **6-10 dev days** | **1.5-2 weeks** |

*(Assuming solo developer, refactoring + testing included)*

---

## DEPENDENCIES & BLOCKERS

### No external blockers:
- ✅ Backend APIs already exist and tested
- ✅ Existing pairing flow works
- ✅ No new libraries needed (except maybe Room later)
- ✅ No AGP/SDK version constraints

### Internal dependencies:
- Phase 2 depends on Phase 1 (refactored state manager)
- Phase 3 depends on Phase 2 (queue polling loop exists)
- Phase 4 independent (can run in parallel with Phase 3)

---

## SIGN-OFF

**Architecture**: ✅ Designed  
**Ready to code**: ✅ Yes  
**Blocking issues**: ❌ None  
**Recommended start**: Phase 1 (refactoring)  

Next: Implementation per the plan above.

