# TV App File Structure & Implementation Recommendations
**Status**: Architecture Design Phase  
**Purpose**: Clear guidance on file organization, what to keep/move/create/delete

---

## QUICK REFERENCE MATRIX

| File/Module | Current Status | Target | Action | Phase |
|-------------|----------------|--------|--------|-------|
| **Screens** | | | | |
| `FirstLaunchScreen.kt` | ✅ Works | Keep as-is | **KEEP** | - |
| `PairingScreen.kt` | ✅ Works | Keep as-is | **KEEP** | - |
| `QueueDisplayScreen.kt` | ⚠️ Static | Add live updates | **ENHANCE** | 2 |
| `BootScreen.kt` | ❌ Missing | New splash | **CREATE** | 2 |
| `ErrorScreen.kt` | ❌ Missing | Error/recovery | **CREATE** | 3 |
| **ViewModels** | | | | |
| `AppStateViewModel.kt` | ✅ Works | Rename + refactor | **RENAME to TvAppViewModel** | 1 |
| `TvPairingViewModel.kt` | ❌ Missing | Pairing state | **CREATE** | 2 |
| `TvQueueViewModel.kt` | ❌ Missing | Queue state | **CREATE** | 2 |
| **Repositories** | | | | |
| `PairingRepository.kt` | ✅ In app-tv | Move to shared | **MOVE to data:network** | 1 |
| `QueueRepository.kt` | ❌ Missing | Queue ops | **CREATE** | 2 |
| `ConfigRepository.kt` | ❌ Missing | Display config | **CREATE** | 2 |
| **API Clients** | | | | |
| `PairingApiClient.kt` | ✅ Exists | Consolidate | **MERGE into TvApiClient** | 1 |
| `PairingCodeManager.kt` | ✅ Exists | Consolidate | **MERGE into TvApiClient** | 1 |
| `TvQueueApiClient.kt` | ❌ Missing | Queue endpoints | **CREATE** | 2 |
| `TvConfigApiClient.kt` | ❌ Missing | Config endpoints | **CREATE** | 2 |
| **State Management** | | | | |
| `TvAppState.kt` | ✅ Enum in AppStateViewModel | Extract | **EXTRACT to domain/state** | 1 |
| `TvAppStateManager.kt` | ❌ Missing | State machine | **CREATE** | 1 |
| `QueueStateManager.kt` | ❌ Missing | Display mapping | **CREATE** | 2 |
| `ConnectionStateManager.kt` | ❌ Missing | Network state | **CREATE** | 3 |
| **Models** | | | | |
| `QueueModels.kt` (core-model) | ✅ Exists | Reuse | **KEEP** | - |
| `PairingBinding.kt` | ❌ Missing | Shared model | **CREATE in core-model/tv** | 1 |
| `TvAppState.kt` (models) | ❌ Missing | Shared enum | **CREATE in core-model/tv** | 1 |
| `QueueDisplayState.kt` | ❌ Missing | UI-ready state | **CREATE in core-model/tv** | 2 |
| `DisplayConfig.kt` | ❌ Missing | Display config | **CREATE in core-model/tv** | 2 |
| **Storage** | | | | |
| `DeviceIdentityManager.kt` | ✅ Works | Keep as-is | **KEEP** | - |
| `PairingStateCache.kt` | ❌ Missing | Binding cache | **CREATE** | 1 |
| `QueueDataCache.kt` | ❌ Missing (Future) | Queue cache | **CREATE** | 4+ |
| **Config** | | | | |
| Hardcoded URLs in MainActivity | ❌ Bad | Config object | **EXTRACT to TvAppConfig** | 4 |
| `TvAppConfig.kt` | ❌ Missing | App config | **CREATE** | 4 |
| **Components** | | | | |
| Existing UI components | ✅ Works | Keep | **KEEP** | - |
| `ConnectionStatusIndicator.kt` | ❌ Missing | Status overlay | **CREATE** | 3 |
| **Utilities** | | | | |
| Logging in code | Scattered | Centralized | **CREATE TvLog.kt** | 4 |
| HTTP config | Hardcoded | Centralized | **CREATE TvApiConfig.kt** | 1 |

---

## DETAILED FILE-BY-FILE BREAKDOWN

### SCREENS (UI Layer)

#### ✅ KEEP AS-IS

**`app-tv/src/main/java/com/yotei/tv/ui/screens/FirstLaunchScreen.kt`**
- Barbershop registration screen
- Working perfectly
- No changes needed
- **Do not touch**

**`app-tv/src/main/java/com/yotei/tv/ui/screens/PairingScreen.kt`**
- Code generation + polling UI
- Tested and working (just added binding pass-through in previous session)
- **Keep callback signature**: `onPairingComplete(binding: PairingApiClient.GetBindingResponse)`
- **Do not add logic here**, keep it pure UI
- No changes until Phase 5

**`app-tv/src/main/java/com/yotei/tv/ui/QueueDisplayScreen.kt`**
- Current: Loads queue once, displays static data
- Phase 2: Hook to `TvQueueViewModel.queueState` Flow
- Replace one-shot load with:
  ```kotlin
  val queueState = viewModel.queueState.collectAsState()
  val connectionStatus = viewModel.connectionStatus.collectAsState()
  // Use these in UI layer
  ```
- Current components (`CurrentTicketCard`, `NextTicketsSection`, etc.) stay as-is
- **Action**: ENHANCE in Phase 2

#### ❌ CREATE NEW

**`app-tv/src/main/java/com/yotei/tv/ui/screens/BootScreen.kt`** 
- **Phase 2**: Show during `BOOTSTRAPPING` state
- Simple splash with spinner
- Content:
  ```kotlin
  @Composable
  fun BootScreen() {
      // Just progress indicator + "Iniciando..." text
  }
  ```
- **Size**: ~30 lines
- **Dependencies**: None

**`app-tv/src/main/java/com/yotei/tv/ui/screens/ErrorScreen.kt`**
- **Phase 3**: Handle error states
- Conditional UI based on error type:
  - `PAIRING_REVOKED`: "Esta pantalla fue desvinculada"
  - `NETWORK_ERROR`: "Sin conexión - Reintentando"
  - `UNRECOVERABLE_ERROR`: "Error irrecuperable"
- Content:
  ```kotlin
  @Composable
  fun ErrorScreen(
      state: TvAppState,
      errorMessage: String?,
      onRetry: () -> Unit,
      onReset: () -> Unit
  ) { ... }
  ```
- **Size**: ~100 lines
- **Dependencies**: None

---

### VIEWMODELS (Presentation Layer State)

#### ⚠️ RENAME

**`app-tv/src/main/java/com/yotei/tv/AppStateViewModel.kt`** → **`TvAppViewModel.kt`**

**Action**: 
1. Keep the file in: `app-tv/src/main/java/com/yotei/tv/ui/viewmodel/TvAppViewModel.kt`
2. Refactor to simplify:
   ```kotlin
   class TvAppViewModel(
       private val stateManager: TvAppStateManager,
       private val pairingRepository: PairingRepository
   ) : ViewModel() {
       val appState = stateManager.appState
       val errorMessage = stateManager.errorMessage
       
       fun registerDevice(barbershopId: String) { ... }
       // Delegate most logic to stateManager
   }
   ```
3. **Remove**: All device registration logic → move to stateManager
4. **Remove**: All pairing polling logic → move to PairingRepository
5. **Remove**: All queue fetching logic → move to QueueRepository (Phase 2)

**Why**: ViewModel should be thin. Real logic in stateManager.

#### ❌ CREATE NEW

**`app-tv/src/main/java/com/yotei/tv/ui/viewmodel/TvPairingViewModel.kt`**
- **Phase 2**: Optional, for pairing-specific state
- Manages: code generation, polling progress, TTL countdown
- Could stay in `TvAppViewModel` initially (not blocking)
- **Current plan**: Create in Phase 2 if pairing needs finer control

**`app-tv/src/main/java/com/yotei/tv/ui/viewmodel/TvQueueViewModel.kt`**
- **Phase 2**: REQUIRED - Queue display state
- Manages: queue data flow, connection status, live polling
- Content:
  ```kotlin
  class TvQueueViewModel(
      private val queueRepository: QueueRepository,
      private val queueStateManager: QueueStateManager
  ) : ViewModel() {
      val queueState: StateFlow<QueueDisplayState?> = ...
      val connectionStatus: StateFlow<ConnectionStatus> = ...
      
      fun startLiveQueueUpdates(displayId, barbershopId) { ... }
      fun stopLiveQueueUpdates() { ... }
  }
  ```
- **Size**: ~80 lines
- **Dependencies**: QueueRepository, QueueStateManager

---

### REPOSITORIES (Data Layer)

#### ✅ KEEP (but move)

**`app-tv/src/main/java/com/yotei/tv/data/PairingRepository.kt`**
- Current location: `app-tv/`
- **Action Phase 1**: MOVE to `data:network` module (shared)
- Path: `data/src/main/java/com/yotei/data/repository/PairingRepository.kt`
- Keep implementation as-is
- Update imports in app-tv project

**`app-tv/src/main/java/com/yotei/tv/data/DeviceIdentityManager.kt`**
- Current location: `app-tv/`
- **Action Phase 1**: MOVE to `data:network` module for reuse
- Path: `data/src/main/java/com/yotei/data/local/DeviceIdentityManager.kt`
- Keep implementation exactly (AES256-GCM is perfect)

#### ❌ CREATE NEW

**`app-tv/src/main/java/com/yotei/tv/data/repository/QueueRepository.kt`**
- **Phase 2**: REQUIRED
- Manages: queue data fetching, live polling, fallback
- Content:
  ```kotlin
  class QueueRepository(
      private val queueApiClient: TvQueueApiClient,
      private val configRepository: ConfigRepository
  ) {
      suspend fun fetchQueueData(...): Result<QueueDisplayData>
      fun liveQueuePolling(...): Flow<Result<QueueDisplayData>>
      // No cache yet (Phase 4)
  }
  ```
- **Size**: ~80 lines
- **Next**: Becomes part of `data:network` later

**`app-tv/src/main/java/com/yotei/tv/data/repository/ConfigRepository.kt`**
- **Phase 2**: REQUIRED
- Manages: display configuration fetching
- Content:
  ```kotlin
  class ConfigRepository(
      private val configApiClient: TvConfigApiClient
  ) {
      suspend fun loadDisplayConfig(displayId: String): DisplayConfig
      suspend fun getCachedConfig(): DisplayConfig?  // Phase 4
  }
  ```
- **Size**: ~40 lines

---

### API CLIENTS (HTTP Layer)

#### ⚠️ CONSOLIDATE

**`app-tv/src/main/java/com/yotei/tv/data/PairingApiClient.kt`**  
**`app-tv/src/main/java/com/yotei/tv/data/PairingCodeManager.kt`**

- **Action Phase 1**: Create unified `TvApiClient.kt`, migrate both into it
- **Don't delete old files yet**, but stop using them
- Deprecate gradually in Phase 2

**New file**: `app-tv/src/main/java/com/yotei/tv/data/api/TvApiClient.kt`
- Unified HTTP layer for ALL TV API calls
- Uses Ktor HttpClient (or Retrofit, if preferred)
- Config:
  ```kotlin
  class TvApiClient(
      private val httpClient: HttpClient,
      private val apiBaseUrl: String = TvAppConfig.API_BASE_URL
  ) {
      // Device registration
      suspend fun registerDevice(barbershopId: String): RegisterDeviceResponse
      
      // Pairing codes
      suspend fun generatePairingCode(deviceId: String, secret: String): GenerateCodeResponse
      suspend fun getPairingCodeStatus(code: String): PairingCodeStatus
      
      // Queue
      suspend fun getQueueEtas(displayId: String, barbershopId: String): QueueDisplayData
      
      // Config
      suspend fun getDisplayConfig(displayId: String): DisplayConfig
  }
  ```
- **Size**: ~150 lines
- **Error handling**: Timeouts, retries, fallbacks

#### ❌ CREATE NEW

**`app-tv/src/main/java/com/yotei/tv/data/api/TvQueueApiClient.kt`**
- **Phase 2**: Extract queue endpoints
- Could live in TvApiClient directly (simpler)
- Separate only if file gets large

**`app-tv/src/main/java/com/yotei/tv/data/api/TvConfigApiClient.kt`**
- **Phase 2**: Extract config endpoints
- Could live in TvApiClient directly (simpler)
- Separate only if file gets large

---

### STATE MANAGEMENT (Domain Layer)

#### ❌ CREATE NEW (MOST IMPORTANT)

**`app-tv/src/main/java/com/yotei/tv/domain/state/TvAppStateManager.kt`**
- **Phase 1**: REQUIRED - State machine heart
- Content:
  ```kotlin
  class TvAppStateManager(
      private val pairingRepository: PairingRepository,
      private val queueRepository: QueueRepository? = null  // Phase 2
  ) {
      private val _appState = MutableStateFlow(TvAppState.UNINITIALIZED)
      val appState: StateFlow<TvAppState> = _appState.asStateFlow()
      
      suspend fun initialize()
      suspend fun registerDevice(barbershopId: String)
      suspend fun startPairing()
      suspend fun onPairingCodeRedeemed(binding: PairingBinding)
      suspend fun onNetworkError(exception: Exception)
      suspend fun attemptRecovery()
      // ... more in Phase 3
  }
  ```
- **Size**: ~200 lines (grows in Phase 3-4)
- **Why separate file**: Central coordination, testable, reusable

**`app-tv/src/main/java/com/yotei/tv/domain/state/TvAppState.kt`**
- **Phase 1**: Extract enum from AppStateViewModel
- **Move to**: `core-model/src/main/java/com/yotei/coremodel/tv/TvAppState.kt`
- Shared across project
- Content:
  ```kotlin
  enum class TvAppState {
      UNINITIALIZED, BOOTSTRAPPING, FIRST_LAUNCH, REGISTERING,
      AWAITING_PAIRING, PAIRING_IN_PROGRESS, PAIRING_FAILED,
      PAIRED, LOADING_DISPLAY_CONFIG, DISPLAY_READY,
      RECONNECTING, PAIRING_REVOKED, NETWORK_ERROR, UNRECOVERABLE_ERROR
  }
  ```

**`app-tv/src/main/java/com/yotei/tv/domain/state/QueueStateManager.kt`**
- **Phase 2**: Display data mapping
- Content:
  ```kotlin
  class QueueStateManager {
      fun mapApiResponseToDisplayState(
          apiData: QueueDisplayData,
          displayConfig: DisplayConfig
      ): QueueDisplayState
      
      fun getNextTicketsForDisplay(tickets: List<Ticket>, count: Int): List<Ticket>
      fun calculateEstimatedWaitTime(position: Int, ...): Int
  }
  ```
- **Size**: ~100 lines
- **Why separate**: Testable, reusable formatting logic

**`app-tv/src/main/java/com/yotei/tv/domain/state/ConnectionStateManager.kt`**
- **Phase 3**: Network connectivity tracking
- Content:
  ```kotlin
  class ConnectionStateManager {
      private val _connectionState = MutableStateFlow(ConnectionState())
      val connectionState: StateFlow<ConnectionState> = ...
      
      fun recordSuccess()
      fun recordFailure(exception: Exception)
      fun reset()
  }
  ```
- **Size**: ~60 lines

#### ❌ CREATE NEW (Optional in Phase 2)

**`app-tv/src/main/java/com/yotei/tv/domain/state/PairingState.kt`**
- **Phase 2**: Optional - Pairing substates (not blocking)
- If you want fine-grained pairing progress (e.g., code TTL countdown)
- Currently handled in PairingScreen

**`app-tv/src/main/java/com/yotei/tv/ui/TvAppCoordinator.kt`**
- **Phase 1**: Optional - Screen routing
- If MainActivity becomes complex
- Initially, simple when/else in MainActivity is fine
- Promote to Coordinator only if needed

---

### MODELS (Data Contracts)

#### ✅ KEEP (Existing)

**`core-model/src/.../queue/QueueModels.kt`**
- `QueueState`, `QueueTicket`, `Barbershop`, `QueueStatus`
- Reuse for TV app
- No changes needed

#### ❌ CREATE NEW (Shared Models)

**Location**: `core-model/src/main/java/com/yotei/coremodel/tv/` (new package)

**`TvAppState.kt`**
- **Phase 1**: Enum extracted from code
- Shared across TV app modules
- ~30 lines

**`TvPairingModels.kt`**
- **Phase 1**: Contains:
  ```kotlin
  data class PairingBinding(
      val bindingId: String,
      val deviceId: String,
      val displayId: String,
      val barbershopId: String,
      val deviceSecret: String,
      val bindingStatus: String,
      val createdAt: String
  )
  
  data class PairingDevice(
      val id: String,
      val name: String,
      val model: String,
      val barbershopId: String
  )
  ```
- ~50 lines

**`TvQueueModels.kt`**
- **Phase 2**: Contains:
  ```kotlin
  data class QueueDisplayState(
      val displayId: String,
      val barbershopId: String,
      val currentTicket: QueueTicket?,
      val nextTickets: List<QueueTicket>,
      val queueStats: QueueStats,
      val connectionStatus: ConnectionStatus,
      val lastUpdateTime: Long,
      val config: DisplayConfig
  )
  
  data class QueueDisplayData(  // API contract
      val barbershopId: String,
      val barbershopName: String,
      val currentQueueSize: Int,
      val activeBarbers: Int,
      val avgServiceMinutes: Int,
      val etas: List<EtaEntry>
  )
  
  enum class ConnectionStatus {
      CONNECTED, DEGRADED, OFFLINE, BINDING_INVALID
  }
  ```
- ~100 lines

**`TvDisplayModels.kt`**
- **Phase 2**: Contains:
  ```kotlin
  data class DisplayConfig(
      val displayId: String,
      val displayName: String,
      val barbershopId: String,
      val refreshIntervalSeconds: Int = 5,
      val theme: DisplayTheme?
  )
  ```
- ~40 lines

#### ❌ CREATE NEW (API Response DTOs)

**Location**: `app-tv/src/main/java/com/yotei/tv/data/api/responses/`

These are internal (not shared) because they mirror backend contracts.

**`RegisterDeviceResponse.kt`**
```kotlin
data class RegisterDeviceResponse(
    val id: String,
    val secret: String,
    val barbershopId: String
)
```

**`GenerateCodeResponse.kt`**
```kotlin
data class GenerateCodeResponse(
    val code: String,
    val ttlSeconds: Int
)
```

**`PairingCodeStatusResponse.kt`**
```kotlin
data class PairingCodeStatusResponse(
    val code: String,
    val codeStatus: String,
    val binding: PairingBinding?,
    val ttlSeconds: Int
)
```

**`QueueApiResponse.kt`**
```kotlin
data class QueueApiResponse(
    val barbershopId: String,
    val barbershopName: String,
    val currentQueueSize: Int,
    val activeBarbers: Int,
    val etas: List<EtaEntry>
)
```

---

### LOCAL STORAGE (Persistence)

#### ✅ KEEP (no changes)

**`app-tv/src/main/java/com/yotei/tv/data/DeviceIdentityManager.kt`**
- Encryption/decryption using EncryptedSharedPreferences
- Stores: device_id, device_secret, barbershop_id
- **Action Phase 1**: Move to `data:network` module
- No functional changes

#### ❌ CREATE NEW

**`app-tv/src/main/java/com/yotei/tv/data/local/PairingStateCache.kt`**
- **Phase 1**: Simple SharedPreferences wrapper
- Stores: binding object, config hash, refresh timestamp
- Content:
  ```kotlin
  class PairingStateCache {
      fun saveBinding(binding: PairingBinding)
      fun getBinding(): PairingBinding?
      fun clearBinding()
      
      fun isBindingFresh(maxAgeHours: Int = 24): Boolean
  }
  ```
- **Size**: ~70 lines

**`app-tv/src/main/java/com/yotei/tv/data/local/QueueDataCache.kt`**
- **Phase 4+**: Optional - fallback queue data
- Uses Room (later)
- Stores: last fetched queue data + timestamp

---

### CONFIGURATION & UTILITIES

#### ❌ CREATE NEW

**`app-tv/src/main/java/com/yotei/tv/config/TvAppConfig.kt`**
- **Phase 4**: REQUIRED for production
- Contains all constants extracted from code
- Content:
  ```kotlin
  object TvAppConfig {
      const val API_BASE_URL = BuildConfig.BACKEND_URL
      const val QUEUE_POLLING_INTERVAL_SECONDS = 5
      const val BINDING_REFRESH_INTERVAL_HOURS = 24
      const val API_TIMEOUT_SECONDS = 10
      const val PAIRING_CODE_TTL_SECONDS = 900
      const val MAX_RETRY_ATTEMPTS = 5
      
      val isDebug = BuildConfig.DEBUG
  }
  ```
- **Size**: ~20 lines

**`app-tv/src/main/java/com/yotei/tv/util/TvLog.kt`**
- **Phase 4**: Optional but recommended
- Thin wrapper around android.util.Log
- Content:
  ```kotlin
  object TvLog {
      fun d(tag: String, msg: String) = Log.d(tag, msg)
      fun e(tag: String, msg: String, t: Throwable? = null) = Log.e(tag, msg, t)
  }
  ```
- **Size**: ~20 lines

**`app-tv/src/main/java/com/yotei/tv/data/api/TvApiConfig.kt`**
- **Phase 1**: HTTP client configuration
- Content:
  ```kotlin
  object TvApiConfig {
      val httpClient: HttpClient by lazy {
          HttpClient {
              install(ContentNegotiation) { ... }
              install(HttpTimeout) {
                  requestTimeoutMillis = TvAppConfig.API_TIMEOUT_SECONDS * 1000L
              }
          }
      }
  }
  ```

---

### COMPONENTS & UI HELPERS

#### ✅ KEEP (existing)

All existing UI components (buttons, cards, text styles) in `ui/components/` stay as-is.

#### ❌ CREATE NEW

**`app-tv/src/main/java/com/yotei/tv/ui/components/ConnectionStatusIndicator.kt`**
- **Phase 3**: Network status overlay
- Shows "Sin conexión" / "Reconectando..." badges
- ~60 lines

**`app-tv/src/main/java/com/yotei/tv/ui/components/LoadingIndicator.kt`**
- **Phase 2**: Optional, for queue loading state
- Simple spinner + text

---

## DIRECTORY STRUCTURE (Target)

```
Yotei_app/
│
├─ app-tv/
│  ├─ build.gradle.kts           [keep]
│  ├─ src/main/
│  │  ├─ AndroidManifest.xml     [keep]
│  │  └─ java/com/yotei/tv/
│  │     │
│  │     ├─ MainActivity.kt       [update - use TvAppCoordinator]
│  │     │
│  │     ├─ domain/              [NEW - Phase 1]
│  │     │  ├─ state/
│  │     │  │  ├─ TvAppStateManager.kt
│  │     │  │  ├─ QueueStateManager.kt
│  │     │  │  ├─ ConnectionStateManager.kt
│  │     │  │  └─ PairingState.kt [optional]
│  │     │  └─ usecase/           [optional]
│  │     │
│  │     ├─ data/
│  │     │  ├─ api/
│  │     │  │  ├─ TvApiClient.kt          [NEW - Phase 1]
│  │     │  │  ├─ TvApiConfig.kt          [NEW - Phase 1]
│  │     │  │  ├─ TvQueueApiClient.kt     [NEW - Phase 2]
│  │     │  │  ├─ TvConfigApiClient.kt    [NEW - Phase 2]
│  │     │  │  └─ responses/              [NEW - Phase 1-2]
│  │     │  │     ├─ RegisterDeviceResponse.kt
│  │     │  │     ├─ GenerateCodeResponse.kt
│  │     │  │     └─ QueueApiResponse.kt
│  │     │  │
│  │     │  ├─ repository/
│  │     │  │  ├─ (PairingRepository.kt)  [MOVE to data:network - Phase 1]
│  │     │  │  ├─ QueueRepository.kt      [NEW - Phase 2]
│  │     │  │  └─ ConfigRepository.kt     [NEW - Phase 2]
│  │     │  │
│  │     │  └─ local/
│  │     │     ├─ (DeviceIdentityManager.kt) [MOVE to data:network - Phase 1]
│  │     │     ├─ PairingStateCache.kt    [NEW - Phase 1]
│  │     │     └─ QueueDataCache.kt       [NEW - Phase 4+]
│  │     │
│  │     ├─ config/
│  │     │  └─ TvAppConfig.kt             [NEW - Phase 4]
│  │     │
│  │     ├─ util/
│  │     │  └─ TvLog.kt                   [NEW - Phase 4]
│  │     │
│  │     ├─ ui/
│  │     │  ├─ TvAppCoordinator.kt        [NEW - Phase 1 optional]
│  │     │  │
│  │     │  ├─ screens/
│  │     │  │  ├─ FirstLaunchScreen.kt    [KEEP]
│  │     │  │  ├─ PairingScreen.kt        [KEEP]
│  │     │  │  ├─ QueueDisplayScreen.kt   [ENHANCE - Phase 2]
│  │     │  │  ├─ BootScreen.kt           [NEW - Phase 2]
│  │     │  │  └─ ErrorScreen.kt          [NEW - Phase 3]
│  │     │  │
│  │     │  ├─ components/
│  │     │  │  ├─ (existing cards/panels) [KEEP]
│  │     │  │  ├─ ConnectionStatusIndicator.kt [NEW - Phase 3]
│  │     │  │  └─ LoadingIndicator.kt     [NEW - Phase 2]
│  │     │  │
│  │     │  └─ viewmodel/
│  │     │     ├─ TvAppViewModel.kt                 [RENAME from AppStateViewModel - Phase 1]
│  │     │     ├─ TvPairingViewModel.kt            [NEW - Phase 2 optional]
│  │     │     └─ TvQueueViewModel.kt              [NEW - Phase 2]
│  │     │
│  │     └─ FakeQueueDataProvider.kt               [KEEP]
│  │
│  ├─ src/test/                  [ADD - Phase 4]
│  │  └─ java/com/yotei/tv/
│  │     ├─ domain/
│  │     │  ├─ TvAppStateManagerTest.kt
│  │     │  └─ QueueStateManagerTest.kt
│  │     └─ data/
│  │        └─ repository/
│  │           └─ QueueRepositoryTest.kt
│  │
│  └─ src/androidTest/[instrumented tests - later]
│
├─ core-model/
│  └─ src/main/java/com/yotei/coremodel/
│     ├─ queue/
│     │  └─ QueueModels.kt       [KEEP]
│     │
│     └─ tv/                      [NEW - Phase 1]
│        ├─ TvAppState.kt         [NEW]
│        ├─ TvPairingModels.kt    [NEW]
│        ├─ TvQueueModels.kt      [NEW - Phase 2]
│        └─ TvDisplayModels.kt    [NEW - Phase 2]
│
├─ data/                          [Shared data module]
│  └─ src/main/java/com/yotei/data/
│     ├─ repository/
│     │  └─ PairingRepository.kt  [MOVE from app-tv - Phase 1]
│     │
│     └─ local/
│        └─ DeviceIdentityManager.kt [MOVE from app-tv - Phase 1]
│
└─ governance/tv/                 [NEW - Added this session]
   ├─ TV_APP_ARCHITECTURE.md
   ├─ TV_APP_MIGRATION_PLAN.md
   └─ TV_APP_FILES.md            [This file]
```

---

## FILE MODIFICATION CHECKLIST

### Phase 1 (Organize)

- [ ] Create `domain/state/TvAppState.kt` extract enum
- [ ] Create `domain/state/TvAppStateManager.kt` - new state machine
- [ ] Create `core-model/tv/TvAppState.kt` - shared enum
- [ ] Create `core-model/tv/TvPairingModels.kt` - shared models
- [ ] Create `data/api/TvApiClient.kt` - unified HTTP
- [ ] Create `data/api/TvApiConfig.kt` - HTTP config
- [ ] Create `data/local/PairingStateCache.kt` - binding cache
- [ ] Move `PairingRepository.kt` to `data:network` module
- [ ] Move `DeviceIdentityManager.kt` to `data:network` module
- [ ] Rename `AppStateViewModel.kt` → `TvAppViewModel.kt`
- [ ] Refactor `AppStateViewModel` to delegate to `TvAppStateManager`
- [ ] Update `MainActivity.kt` to use new structure
- [ ] Update imports across project

### Phase 2 (Real-Time Queue)

- [ ] Create `data/repository/QueueRepository.kt`
- [ ] Create `data/repository/ConfigRepository.kt`
- [ ] Create `domain/state/QueueStateManager.kt`
- [ ] Create `ui/viewmodel/TvQueueViewModel.kt`
- [ ] Create `ui/screens/BootScreen.kt`
- [ ] Create `core-model/tv/TvQueueModels.kt`
- [ ] Create `core-model/tv/TvDisplayModels.kt`
- [ ] Enhance `QueueDisplayScreen.kt` - add live polling
- [ ] Add API response DTOs in `data/api/responses/`

### Phase 3 (Error Recovery)

- [ ] Create `domain/state/ConnectionStateManager.kt`
- [ ] Create `ui/screens/ErrorScreen.kt`
- [ ] Create `ui/components/ConnectionStatusIndicator.kt`
- [ ] Update `TvAppStateManager` - add recovery methods
- [ ] Update `MainActivity.kt` - handle all error states

### Phase 4 (Polish)

- [ ] Create `config/TvAppConfig.kt`
- [ ] Create `util/TvLog.kt`
- [ ] Update all hardcoded values → use TvAppConfig
- [ ] Add unit tests in `src/test/`
- [ ] Consolidate HTTP clients (remove old ones)

---

## DEPENDENCIES ACROSS FILES

### Phase 1 Dependencies
```
MainActivity
  ↓ uses
TvAppViewModel
  ↓ uses
TvAppStateManager
  ↓ uses
PairingRepository + DeviceIdentityManager
```

### Phase 2 Dependencies
```
QueueDisplayScreen
  ↓ uses
TvQueueViewModel
  ↓ uses
QueueRepository + QueueStateManager
  ↓ uses
TvQueueApiClient + ConfigRepository
```

### Phase 3 Dependencies
```
ErrorScreen
  ↓ uses
TvAppStateManager (extended)
  ↓ uses
ConnectionStateManager
```

### Phase 4 Dependencies
```
All API clients
  ↓ read from
TvAppConfig
  ↓ reads from
BuildConfig
```

---

## FILES TO REMOVE/DEPRECATE

### Phase 1
- None (preserve everything until Phase 2)

### Phase 2
- ⚠️ Deprecate `PairingApiClient.kt` (migrate to `TvApiClient`)
- ⚠️ Deprecate `PairingCodeManager.kt` (migrate to `TvApiClient`)
- Don't delete yet, just stop using

### Phase 3
- Can now safely delete old API client files

### Phase 4
- None planned

---

## COMMON MISTAKES TO AVOID

1. **❌ Don't move PairingRepository too early**
   - It has dependencies on app-tv features
   - Move in Phase 1 once refactored

2. **❌ Don't add Room/Hilt yet**
   - Not needed for MVP architecture
   - Consider only in Phase 4+

3. **❌ Don't change MainActivity's structure too much**
   - Keep it as composition root
   - Use TvAppCoordinator only if it grows complex

4. **❌ Don't expose repositories to UI directly**
   - Flow always: UI → ViewModel → StateManager → Repository
   - Never: UI → Repository

5. **❌ Don't hardcode BuildConfig/URLs in logic**
   - Use TvAppConfig singleton
   - Easier to change per environment

6. **❌ Don't mix state mutation across layers**
   - Only TvAppStateManager can change app state
   - ViewModels observe, don't mutate

---

## VALIDATION CHECKLIST

After each phase, verify:

**Phase 1**:
- [ ] Pairing still works identically
- [ ] Queue display still loads on pairing
- [ ] No import errors
- [ ] Builds successfully
- [ ] Tests pass (if existing)

**Phase 2**:
- [ ] Queue updates every 5 seconds with real data
- [ ] Shows correct barbershop name + ticket #
- [ ] Gracefully handles API failures
- [ ] No excessive logging

**Phase 3**:
- [ ] Killing network shows error overlay
- [ ] Restoring network auto-recovers
- [ ] Error screens display correctly
- [ ] Back button works from errors

**Phase 4**:
- [ ] All URLs from TvAppConfig
- [ ] Unit tests pass
- [ ] No hardcoded values in code
- [ ] Logging consistent

---

## NOTES FOR DEVELOPERS

1. **Start with Phase 1**: It's the foundation. Don't skip to Phase 2.
2. **Each phase is shippable**: You can release after each phase.
3. **Tests early**: Add unit tests in Phase 1, not at the end.
4. **Run locally**: Test changes on actual device (NVIDIA Shield).
5. **Ask questions**: Ambiguities in this plan should be clarified before coding.
6. **Small commits**: One file per commit for easy review.
7. **Branch strategy**: Feature branches per phase (e.g., `feature/phase-1-refactor`).

---

## FINAL SUMMARY

| Component | Files to Create | Files to Move | Files to Keep | Total Changes |
|-----------|-----------------|---------------|---------------|---------------|
| **State** | 4 new managers | - | 1 refactored ViewModel | 5 |
| **Data** | 2 repositories + 3 API clients + cache | 2 moved | - | 7 |
| **Models** | 5 new shared models | - | 1 reused | 6 |
| **UI** | 3 screens + 1 indicator | - | 4 kept | 7 |
| **Config** | 2 config files | - | - | 2 |
| **Docs** | ✅ This file + 2 other docs | - | - | 3 |
| | | | **TOTAL** | **~30 files** |

**Estimated effort**: 10-15 dev days (1.5–2 weeks) across 4 phases.

