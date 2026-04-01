# TV App Architecture Design
**Date**: March 2026  
**Status**: Architecture Design Phase (Pre-Implementation)  
**Scope**: Android TV app for queue display system

---

## EXECUTIVE SUMMARY

The TV app is a **thin display client** that:
- **Bootstraps** itself by registering as a device with the backend
- **Pairs** with a barbershop location via 6-digit code
- **Displays** real-time queue information to customers waiting in line
- **Recovers** gracefully from network failures and binding revocation

Current progress: ✅ **Pairing flow complete and tested**. ✅ **Static queue display working**. ❌ **Real-time updates missing**. ❌ **No recovery/reconnect logic**.

**Target**: production-ready TV app with clear separation of concerns, resilient connection handling, and extensible architecture for future media/animation modes.

---

## PART 1: ANALYSIS OF CURRENT IMPLEMENTATION

### 1.1 What Works Well ✅

| Aspect | Current Implementation | Assessment |
|--------|------------------------|------------|
| **Pairing Flow** | Complete: code generation → polling → binding receipt | ✅ Production-ready |
| **Device Registration** | UUID-based device identity + barbershop selection | ✅ Secure + tested |
| **Secure Storage** | AES256-GCM via EncryptedSharedPreferences | ✅ Enterprise-grade |
| **State Machine** | Enum-based AppState with clear transitions | ✅ Maintainable |
| **UI/Queue Display** | TV-optimized screens (96sp code, 72sp ticket #) | ✅ High visibility |
| **Error Handling** | Graceful fallback to fake data | ✅ Resilient |
| **Binding Persistence** | Device secret + barbershop_id saved locally | ✅ Reliable restarts |

### 1.2 Critical Gaps ❌

| Gap | Impact | Severity |
|-----|--------|----------|
| **Queue display loads once** | Stale data after 10 seconds | **BLOCKING** |
| **No queue repository** | Queue logic hardcoded in MainActivity | **HIGH** |
| **Hardcoded backend URLs** | Not configurable per environment | **HIGH** |
| **No network monitoring** | Can't detect offline → frozen display | **HIGH** |
| **No periodic binding refresh** | Can't detect revocation until restart | **MEDIUM** |
| **No queue data caching** | Can't provide fallback on API failure | **MEDIUM** |
| **No error screen** | ERROR state not implemented | **MEDIUM** |
| **Inconsistent HTTP clients** | HttpURLConnection + Ktor mixed | **MEDIUM** |
| **No DI framework** | Manual object creation in MainActivity | **LOW** |

### 1.3 Current Architecture (Simplified)

```
MainActivity (Orchestrator)
    ├─ AppStateViewModel (State holder)
    │   ├─ PairingRepository (Device + pairing)
    │   │   ├─ PairingApiClient (Registration)
    │   │   ├─ DeviceIdentityManager (Secrets storage)
    │   │   └─ PairingCodeManager (Code polling)
    │   └─ [NO QueueRepository] ← Queue logic inline in MainActivity
    │
    ├─ FirstLaunchScreen (Register device)
    ├─ PairingScreen (Code + polling)
    └─ QueueDisplayScreen (Static, one-shot load)
```

**Problem**: Queue state management mixed with UI concerns in MainActivity.

---

## PART 2: TARGET ARCHITECTURE

### 2.1 Layered Architecture

```
┌─ PRESENTATION LAYER ─────────────────────────────────┐
│  MainActivity                                          │
│    └─ TvAppCoordinator (Navigation + state routing)   │
│                                                        │
│  Screens (Composable):                                │
│    ├─ BootScreen                                      │
│    ├─ FirstLaunchScreen                               │
│    ├─ PairingScreen                                   │
│    ├─ QueueDisplayScreen                              │
│    └─ ErrorScreen                                     │
│                                                        │
│  ViewModels:                                          │
│    ├─ TvAppViewModel (App-level state)                │
│    ├─ PairingViewModel (Pairing specific)             │
│    └─ QueueViewModel (Queue display)                  │
└────────────────────────────────────────────────────────┘

┌─ DOMAIN/STATE LAYER ──────────────────────────────────┐
│  TvAppStateManager (State machine coordinator)        │
│  PairingStateManager (Pairing-specific state)         │
│  QueueStateManager (Queue display state)              │
│  ConnectionStateManager (Network connectivity)        │
└────────────────────────────────────────────────────────┘

┌─ DATA LAYER ──────────────────────────────────────────┐
│  Repositories:                                         │
│    ├─ PairingRepository (Registration + binding)      │
│    ├─ QueueRepository (Queue data fetching)           │
│    └─ ConfigRepository (Display configuration)        │
│                                                        │
│  API Clients:                                         │
│    ├─ TvDeviceApiClient (Device operations)           │
│    ├─ TvPairingApiClient (Pairing code flow)          │
│    └─ TvQueueApiClient (Queue data fetch)             │
│                                                        │
│  Local Storage:                                        │
│    ├─ DeviceIdentityManager (Device secrets)          │
│    ├─ PairingStateCache (Binding + config)            │
│    └─ QueueDataCache (Fallback queue data) [FUTURE]   │
└────────────────────────────────────────────────────────┘

┌─ SHARED MODULES (core-*, network) ────────────────────┐
│  Models:                                               │
│    ├─ TvAppState (App state enum)                     │
│    ├─ PairingDevice (Device registration model)       │
│    ├─ PairingBinding (Binding contract)               │
│    ├─ QueueDisplayState (UI contract)                 │
│    └─ ConnectionStatus (Network state)                │
│                                                        │
│  HTTP Config:                                          │
│    ├─ TvApiConfig (Baseurl, timeouts, retries)        │
│    └─ TvApiClient (Unified HTTP client)               │
│                                                        │
│  Utilities:                                            │
│    ├─ TvAppConfig (Environment-specific config)       │
│    └─ TvLogging (Structured logging)                  │
└────────────────────────────────────────────────────────┘
```

### 2.2 Responsibility Matrix

| Component | Responsibility | Current | Target |
|-----------|-----------------|---------|--------|
| **MainActivity** | Navigation, composition setup | ✅ Exists | Keep (add coordinator) |
| **TvAppCoordinator** | Route state → screen, handle back stack | ❌ Missing | Add — NEW |
| **TvAppViewModel** | App-level state (device, pairing, queue) | ⚠️ AppStateViewModel exists | Rename + refactor |
| **PairingRepository** | Device registration, pairing workflow | ✅ Exists | Keep as-is |
| **QueueRepository** | Fetch, cache, refresh queue data | ❌ Missing | Add — NEW |
| **QueueStateManager** | Queue data → UI state (formatting, ETAs) | ❌ Missing | Add — NEW |
| **PairingScreen** | Code display + polling UI | ✅ Exists | Keep as-is |
| **QueueDisplayScreen** | Static queue rendering | ✅ Exists | Keep, add real-time updates |
| **DeviceIdentityManager** | Encrypt/decrypt device secrets | ✅ Exists | Keep as-is |

### 2.3 Key Design Principles

1. **TV is a display client**: All business logic (queue assignment, state changes) happens in backend. TV only fetches and displays.

2. **State derivation**: Each screen observes ViewModel state and recomposes automatically. No imperative navigation.

3. **Resilience by default**: All API calls have timeouts, retries, fallbacks. Missing data = show fake data, not crash.

4. **Single source of truth**: One TvAppStateManager holds ground truth for app state.

5. **Offline-friendly**: Can show stale queue data indefinitely if offline. Real-time updates opt-in.

6. **Configuration over customization**: Backend defines display config (colors, layout, refresh rate). TV does not hardcode.

---

## PART 3: STATE MACHINE DEFINITION

### 3.1 App State Enum (Ground Truth)

```kotlin
enum class TvAppState {
    // Initialization
    UNINITIALIZED,          // App just started, no persistent state
    BOOTSTRAPPING,          // Checking if device is already registered
    
    // Registration
    FIRST_LAUNCH,           // No device ID yet, show registration
    REGISTERING,            // Creating device in backend
    REGISTRATION_FAILED,    // Could not register
    
    // Pairing
    AWAITING_PAIRING,       // Device registered, waiting for pair code
    PAIRING_IN_PROGRESS,    // Code generated, polling for redemption
    PAIRING_FAILED,         // Code expired or network error
    
    // Paired states
    PAIRED,                 // Successfully paired, can display queue
    LOADING_DISPLAY_CONFIG, // Fetching display configuration
    DISPLAY_READY,          // Queue data available, displaying
    
    // Error/Recovery
    RECONNECTING,           // Network restored, retrying
    PAIRING_REVOKED,        // Binding no longer valid (re-pair)
    NETWORK_ERROR,          // Offline or API down
    UNRECOVERABLE_ERROR,    // Critical error, manual reset needed
}
```

### 3.2 Detailed State Transitions

```
                     ┌─────────────────────────────────────────────┐
                     │ UNINITIALIZED                               │
                     │ (App started, no state on disk)             │
                     └──────────────────┬──────────────────────────┘
                                        │
                                        ├─→ BOOTSTRAPPING
                                        │   (Check if device exists on disk)
                                        │
                                        ├─→ FIRST_LAUNCH
                                        │   (No device_id → need registration)
                                        │   └─→ REGISTERING
                                        │       ├─→ AWAITING_PAIRING ✓
                                        │       └─→ REGISTRATION_FAILED
                                        │           └─→ FIRST_LAUNCH (retry)
                                        │
                                        └─→ AWAITING_PAIRING
                                            (device_id exists, no binding)


AWAITING_PAIRING ════════════════════════════════════════════════════════════
    ↓
    PairingScreen displayed, user selects "Start Pairing"
    ├─→ PAIRING_IN_PROGRESS
    │   (Generate code: POST /api/pairing-codes/generate)
    │   (Code: "482917", TTL: 15 min)
    │   (Poll every 2 sec: GET /api/pairing-codes/{code}/status)
    │   │
    │   ├─→ (Staff enters code in dashboard)
    │   │   └─→ PAIRED ✓
    │   │       (Backend response includes binding + device_secret)
    │   │       (Save to EncryptedSharedPreferences)
    │   │       └─→ LOADING_DISPLAY_CONFIG
    │   │           (Fetch POST /api/display/default → display_id)
    │   │           └─→ DISPLAY_READY
    │   │               (Queue display visible)
    │   │
    │   ├─→ (Code expires after 15 min)
    │   │   └─→ PAIRING_FAILED
    │   │       (Show error, allow retry)
    │   │
    │   └─→ (Network error while polling)
    │       └─→ PAIRING_FAILED
    │           (Show error, allow retry)


PAIRED (Steady State) ═════════════════════════════════════════════════════════
    │
    ├─→ DISPLAY_READY
    │   (Polling: GET /api/display/{displayId}/queue-etas every 5 sec)
    │   (Live updates to queue display)
    │   │
    │   ├─→ (Binding invalid/revoked)
    │   │   └─→ PAIRING_REVOKED
    │   │       (Show overlay: "This display was removed")
    │   │       └─→ AWAITING_PAIRING (on user action)
    │   │
    │   ├─→ (Network timeout)
    │   │   └─→ NETWORK_ERROR
    │   │       (Show overlay: "Connection lost")
    │   │       (Continue showing stale queue, retry in background)
    │   │       └─→ RECONNECTING (network restored)
    │   │           (Retry API calls)
    │   │           └─→ DISPLAY_READY (success)
    │   │           └─→ PAIRING_REVOKED (binding invalid)
    │   │           └─→ UNRECOVERABLE_ERROR (repeated failures)
    │   │
    │   └─→ (Binding expires/needs refresh every 24h)
    │       └─→ POST /api/display/{displayId}/binding/refresh
    │           └─→ DISPLAY_READY (refresh success)


PAIRING_REVOKED ════════════════════════════════════════════════════════════
    (Display was deleted/revoked by staff)
    ├─→ Show error overlay
    ├─→ User taps "Reconnect" or "Clear and Re-pair"
    └─→ AWAITING_PAIRING


UNRECOVERABLE_ERROR ═════════════════════════════════════════════════════════
    (Device registration failed, device deleted, etc.)
    ├─→ Show error message
    ├─→ User taps "Factory Reset"
    ├─→ Clear all local state
    └─→ UNINITIALIZED → FIRST_LAUNCH
```

### 3.3 Pairing State Substates

While in `PAIRING_IN_PROGRESS`, track additional state:

```kotlin
sealed class PairingState {
    data class GeneratingCode(val deviceId: String) : PairingState()
    data class WaitingForRedemption(
        val code: String,
        val ttlSeconds: Int,
        val pollCount: Int = 0
    ) : PairingState()
    data class CodeExpired(val code: String) : PairingState()
    data class PollError(val code: String, val error: String) : PairingState()
    data class BindingReceived(val binding: PairingBinding) : PairingState()
}
```

### 3.4 Display State Substates

While in `DISPLAY_READY`, track:

```kotlin
data class DisplayState(
    val displayId: String,
    val barbershopId: String,
    val queueData: QueueDisplayData,
    val connectionStatus: ConnectionStatus,
    val lastRefreshTime: Long,
    val isLiveUpdateActive: Boolean = false
)

enum class ConnectionStatus {
    CONNECTED,      // API responding normally
    DEGRADED,       // Slow/retrying but recovering
    OFFLINE,        // No connection, showing stale data
    BINDING_INVALID // Need to re-pair
}
```

---

## PART 4: LAYER RESPONSIBILITIES

### 4.1 Presentation Layer (Screens & ViewModels)

#### MainActivity
- **Responsibility**: App entry point, Compose root, theme setup
- **Owns**: LocalPairingRepository composition local + theme
- **Delegates to**: TvAppCoordinator for state-to-screen routing

#### TvAppCoordinator (NEW)
- **Responsibility**: Map app state → screen composition
- **Owns**: Screen routing logic, back stack handling
- **Pattern**: `@Composable fun TvAppCoordinator(state: TvAppState, ...)`

#### TvAppViewModel
- **Responsibility**: Expose app-level state flows to UI
- **Owns**: Observe TvAppStateManager and expose as StateFlow
- **Delegates to**: TvAppStateManager for business logic

#### Screen ViewModels
- **TvPairingViewModel**: Pairing-specific state (code, polling progress)
- **TvQueueViewModel**: Queue displays state (current ticket, next 5, wait times)

#### Screens
- **BootScreen** (NEW): Splash during BOOTSTRAPPING
- **FirstLaunchScreen**: Device registration (already exists)
- **PairingScreen**: Code display + polling (already exists)
- **QueueDisplayScreen**: Queue rendering (already exists, + real-time updates)
- **ErrorScreen** (NEW): PAIRING_REVOKED, UNRECOVERABLE_ERROR, NETWORK_ERROR states

### 4.2 Domain/State Layer (State Managers)

#### TvAppStateManager (State Machine Heart)
- **Responsibility**: Hold + mutate app state, coordinate transitions
- **Owns**: Internal `_appState: MutableStateFlow<TvAppState>`
- **Methods**:
  ```kotlin
  suspend fun initialize()
  suspend fun registerDevice(barbershopId: String)
  suspend fun startPairing()
  suspend fun onPairingCodeRedeemed(binding: PairingBinding)
  suspend fun setDisplayReady(displayId: String, queueData: QueueDisplayData)
  suspend fun handleNetworkError(exception: Exception)
  suspend fun refreshBinding()
  suspend fun resetToFirstLaunch()
  ```
- **Emits**: State changes via Flow for ViewModels to observe

#### PairingStateManager (Substates)
- **Responsibility**: Track detailed pairing progress (code, polling count, TTL)
- **Owns**: `_pairingState: MutableStateFlow<PairingState>`
- **Listens to**: Code generation, each poll attempt, expiry

#### QueueStateManager (NEW) (Display Logic)
- **Responsibility**: Transform raw queue API response → UI-ready display state
- **Owns**: Formatting, ETA calculation, color coding, next-N selection
- **Methods**:
  ```kotlin
  fun mapApiResponseToDisplayState(
    apiData: QueueDisplayData,
    displayConfig: DisplayConfig
  ): QueueDisplayState
  
  fun getNextTicketsForDisplay(
    currentTicket: Ticket?,
    allTickets: List<Ticket>
  ): List<Ticket>  // Next 5
  
  fun calculateEstimatedWaitTime(
    position: Int,
    avgServiceTimeMin: Int,
    activeBarbers: Int
  ): Int
  ```

#### ConnectionStateManager (NEW)
- **Responsibility**: Monitor network connectivity, coordinate retry logic
- **Owns**: `_connectionStatus: MutableStateFlow<ConnectionStatus>`
- **Listens to**: API failures, timeouts, platform connectivity changes

### 4.3 Data Layer (Repositories & API Clients)

#### PairingRepository (Keep as-is)
- **Responsibility**: Device registration, code generation, polling
- **Owns**: Orchestrates PairingApiClient + DeviceIdentityManager
- **Methods**: Already exist + work ✅
  ```kotlin
  suspend fun registerDevice(barbershopId: String): PairingDevice
  suspend fun generatePairingCode(deviceId: String, deviceSecret: String): CodeResponse
  suspend fun pollPairingCodeStatus(deviceId: String, code: String): PairingCodeStatus
  suspend fun waitForPairingCodeRedemption(...): PairingBinding
  ```

#### QueueRepository (NEW)
- **Responsibility**: Fetch, cache, and refresh queue data
- **Owns**: Calls TvQueueApiClient, manages QueueDataCache
- **Methods**:
  ```kotlin
  suspend fun fetchQueueData(displayId: String, barbershopId: String): QueueDisplayData
  
  suspend fun startLiveQueuePolling(
    displayId: String,
    barbershopId: String,
    intervalMs: Long = 5000
  ): Flow<QueueDisplayData>
  
  suspend fun stopLiveQueuePolling()
  
  suspend fun getCachedQueueData(): QueueDisplayData?
  
  suspend fun refreshBinding(displayId: String): Boolean
  ```

#### ConfigRepository (NEW)
- **Responsibility**: Load and cache display configuration
- **Owns**: TvConfigApiClient, config caching
- **Methods**:
  ```kotlin
  suspend fun loadDisplayConfig(displayId: String): DisplayConfig
  suspend fun getCachedConfig(): DisplayConfig?
  ```

#### TvDeviceApiClient (NEW)
- **Responsibility**: HTTP calls for device operations
- **Calls**: `POST /api/devices`, `GET /api/devices/{id}`
- **Uses**: Unified TvApiClient

#### TvPairingApiClient (NEW, or extract from PairingApiClient)
- **Responsibility**: HTTP calls for pairing code flow
- **Calls**: `POST /api/pairing-codes/generate`, `GET /api/pairing-codes/{code}/status`
- **Uses**: Unified TvApiClient

#### TvQueueApiClient (NEW)
- **Responsibility**: HTTP calls for queue data
- **Calls**: `GET /api/display/{displayId}/queue-etas`, `POST /api/display/default`
- **Uses**: Unified TvApiClient

#### TvConfigApiClient (NEW)
- **Responsibility**: HTTP calls for display configuration
- **Calls**: `GET /api/display/{displayId}/config`
- **Uses**: Unified TvApiClient

### 4.4 Storage Layer

#### DeviceIdentityManager (Keep as-is)
- AES256-GCM encryption ✅
- Stores: device_id, device_secret, barbershop_id

#### PairingStateCache (NEW)
- Stores: binding_id, device_secret, display_id, config_hash
- Validates binding freshness (for 24h refresh logic)

#### QueueDataCache (FUTURE)
- Stores: last fetched queue data + timestamp
- Fallback when API down

---

## PART 5: DATA MODELS (Contracts)

### 5.1 Models to Preserve (Existing)

From `core-model/queue/`:
- ✅ `QueueState`
- ✅ `QueueTicket`
- ✅ `Barbershop`
- ✅ `QueueStatus`

### 5.2 Models to Add (Shared Layer)

**Location**: `core-model/tv/` (new package)

```kotlin
// ─── App State ──────────────────────────────────────
enum class TvAppState {
    UNINITIALIZED, BOOTSTRAPPING, FIRST_LAUNCH, REGISTERING,
    AWAITING_PAIRING, PAIRING_IN_PROGRESS, PAIRING_FAILED,
    PAIRED, LOADING_DISPLAY_CONFIG, DISPLAY_READY,
    RECONNECTING, PAIRING_REVOKED, NETWORK_ERROR,
    UNRECOVERABLE_ERROR
}

// ─── Pairing Models ─────────────────────────────────
data class PairingDevice(
    val id: String,                    // UUID
    val name: String,
    val model: String,
    val barbershopId: String,
    val createdAt: String
)

data class PairingBinding(
    val bindingId: String,             // UUID from backend
    val deviceId: String,
    val displayId: String,
    val barbershopId: String,
    val deviceSecret: String,          // Returned from backend after pairing
    val bindingStatus: String,         // "active", "revoked"
    val displayConfig: Map<String, Any>?,
    val createdAt: String,
    val expiresAt: String?             // Optional, if implementing 24h refresh
)

data class PairingCodeStatus(
    val code: String,
    val codeStatus: String,            // "pending", "used", "expired"
    val binding: PairingBinding?,      // Only when code_status == "used"
    val redeemedAt: String?,
    val redeemedByDisplayId: String?,
    val ttlSeconds: Int
)

// ─── Queue Display Models ────────────────────────────
data class QueueDisplayState(
    val displayId: String,
    val barbershopId: String,
    val barbershopName: String,
    val currentTicket: QueueTicket?,   // Ticket being served
    val nextTickets: List<QueueTicket>, // Next 5-10 tickets
    val queueStats: QueueStats,
    val connectionStatus: ConnectionStatus,
    val lastUpdateTime: Long,
    val config: DisplayConfig
)

data class QueueStats(
    val totalInQueue: Int,
    val estimatedWaitMinutes: Int,
    val activeBarbers: Int,
    val avgServiceMinutes: Int
)

data class QueueDisplayData(        // API response contract
    val barbershopId: String,
    val barbershopName: String,
    val currentQueueSize: Int,
    val activeBarbers: Int,
    val avgServiceMinutes: Int,
    val averageEtaMinutes: Int,
    val minEtaMinutes: Int,
    val maxEtaMinutes: Int,
    val customersCount: Int,
    val etas: List<EtaEntry>,
    val nextAvailableAt: String?,
    val status: String,
    val statusMessage: String?,
    val lastUpdated: String
)

data class EtaEntry(
    val ticketId: String,
    val estimatedMinutes: Int,
    val position: Int
)

// ─── Display Config ─────────────────────────────────
data class DisplayConfig(
    val displayId: String,
    val displayName: String,
    val barbershopId: String,
    val refreshIntervalSeconds: Int = 5,  // How often to poll queue
    val bindingRefreshHours: Int = 24,    // When to refresh binding
    val showNextTicketCount: Int = 5,     // How many "next" to show
    val theme: DisplayTheme?,
    val features: Map<String, Boolean>    // Feature flags
)

data class DisplayTheme(
    val primaryColor: String,
    val secondaryColor: String,
    val backgroundColor: String,
    val textColor: String
)

// ─── Connection State ───────────────────────────────
enum class ConnectionStatus {
    CONNECTED,      // API healthy, live updates
    DEGRADED,       // Retrying, showing stale data
    OFFLINE,        // No connection, fallback mode
    BINDING_INVALID // Re-pair needed
}

data class ConnectionState(
    val status: ConnectionStatus,
    val lastSuccessTime: Long?,
    val failureCount: Int = 0,
    val lastErrorMessage: String? = null,
    val retryCount: Int = 0
)
```

### 5.3 API Response Models (Internal)

**Location**: `app-tv/data/api/responses/`

```kotlin
// Device registration response
data class RegisterDeviceResponse(
    val id: String,
    val secret: String,  // Returned on registration, must be saved
    val barbershopId: String
)

// Code generation response
data class GenerateCodeResponse(
    val code: String,
    val ttlSeconds: Int,
    val expiresAt: String
)

// Queue API response
data class QueueApiResponse(
    val barbershopId: String,
    val barbershopName: String,
    val currentQueueSize: Int,
    val activeBarbers: Int,
    val avgServiceMinutes: Int,
    val averageEtaMinutes: Int,
    val etas: List<EtaData>,
    // ... other fields
)

data class EtaData(
    val ticketId: String,
    val estimatedMinutes: Int
)

// Display config response
data class DisplayConfigResponse(
    val id: String,
    val name: String,
    val refreshIntervalSeconds: Int,
    val theme: ThemeData?
)
```

---

## PART 6: CONNECTION RECOVERY & RESILIENCE

### 6.1 Polling Strategy (Queue Data)

```
START POLLING
    └─ Every 5 seconds (configurable)
    │  └─ GET /api/display/{displayId}/queue-etas
    │     ├─ Response 200 ✓
    │     │  └─ Update queue display, reset failure count
    │     │
    │     ├─ Response 401/403/404 (Binding invalid)
    │     │  └─ PAIRING_REVOKED state
    │     │  └─ Stop polling, show overlay
    │     │
    │     ├─ Response 500/503 or Timeout
    │     │  └─ failureCount += 1
    │     │  ├─ If failureCount == 1: NETWORK_ERROR state, show overlay
    │     │  ├─ If failureCount < 5: Retry in 10 sec (exponential backoff)
    │     │  ├─ If failureCount >= 5: Attempt binding refresh
    │     │  │   ├─ Success: Reset failureCount, resume polling
    │     │  │   └─ Failure: UNRECOVERABLE_ERROR state
    │     │  └─ Always show last known queue data (stale but useful)
    │     │
    │     └─ Network unreachable (no internet)
    │        └─ failureCount += 1
    │        └─ Switch to graceful degradation
    │        └─ Keep showing last queue for 30 minutes
    │        └─ If offline > 30 min: Show "Service Restored" message
```

### 6.2 Binding Refresh (24h cycle)

```
PAIRED STATE
    │
    ├─ Every 24 hours (from binding creation)
    │  └─ POST /api/display/{displayId}/binding/refresh
    │     ├─ 200 ✓ Success
    │     │  └─ Update cached binding, continue
    │     │
    │     └─ 401/403 (Binding revoked)
    │        └─ PAIRING_REVOKED state
    
    Alternative: Check binding freshness on each queue fetch response
    If binding older than 24h → trigger refresh before next poll
```

### 6.3 Offline-Friendly Display

When `ConnectionStatus == OFFLINE`:
- ✅ Continue showing last known queue data
- ✅ Grayed-out "data is stale" indicator
- ✅ Show "Reconnecting..." badge if retrying
- ❌ Never show blank/white screen
- ❌ Never crash on API error

---

## PART 7: IMPLEMENTATION CHECKLIST (High-Level)

### Must-Haves (Phase 1-2)
- [ ] Extract PairingRepository to `data:network` module (shared)
- [ ] Create QueueRepository (new)
- [ ] Create TvAppStateManager (state machine)
- [ ] Create TvAppCoordinator (screen routing)
- [ ] Create QueueStateManager (display formatting)
- [ ] Implement live queue polling (every 5 sec)
- [ ] Create ErrorScreen + error handling
- [ ] Move queue fetch from MainActivity to QueueRepository

### High-Priority (Phase 2-3)
- [ ] Implement binding refresh (24h cycle)
- [ ] Implement network monitoring (ConnectionStateManager)
- [ ] Extract hardcoded URLs → TvAppConfig
- [ ] Unify HTTP clients (pick Ktor, standardize)
- [ ] Add structured logging (Timber)
- [ ] Add recovery overlays (PAIRING_REVOKED, NETWORK_ERROR)

### Medium-Priority (Phase 3-4)
- [ ] Implement QueueDataCache (Room)
- [ ] Add dependency injection (Hilt)
- [ ] Unit tests for state managers
- [ ] API mocking for testing
- [ ] Feature flags for queue polling

### Nice-To-Have (Phase 4+)
- [ ] WebSocket for real-time updates (instead of polling)
- [ ] Animation mode / media playback hooks
- [ ] Multi-display support (if needed)
- [ ] Analytics / crash reporting

---

## PART 8: GOVERNANCE & CROSS-CUTTING CONCERNS

### 8.1 Configuration Management

**Problem**: Hardcoded URLs in MainActivity
**Solution**: Create `TvAppConfig` singleton

```kotlin
object TvAppConfig {
    val backendUrl = BuildConfig.BACKEND_URL  // e.g., "http://192.168.1.27:3000"
    val apiTimeoutSeconds = 10
    val pairingCodeTtlSeconds = 900  // 15 min
    val queuePollingIntervalSeconds = 5
    val bindingRefreshIntervalHours = 24
    
    // Environment-specific
    val isDebug = BuildConfig.DEBUG
    val logLevel = if (isDebug) Log.DEBUG else Log.INFO
}
```

### 8.2 Logging

**Goal**: Structured logs without depending on Timber yet

```kotlin
object TvLog {
    private fun log(level: Int, tag: String, msg: String, throwable: Throwable? = null) {
        if (TvAppConfig.logLevel <= level) {
            android.util.Log.println(level, tag, msg)
            throwable?.printStackTrace()
        }
    }
    
    fun d(tag: String, msg: String) = log(Log.DEBUG, tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) = log(Log.ERROR, tag, msg, t)
    // ...
}
```

### 8.3 Error Handling

**Pattern**: Always have a fallback

```kotlin
// ❌ Bad
val queue = repository.fetchQueue()  // Crashes if offline

// ✅ Good
val queue = try {
    repository.fetchQueue()
} catch (e: Exception) {
    TvLog.e("QueueVM", "Fetch failed: ${e.message}", e)
    repository.getCachedQueue() ?: FakeQueueData.default()
}
```

### 8.4 State Mutation Guard

**Pattern**: Only TvAppStateManager can mutate app state

```kotlin
// In AppStateViewModel:
val appState: StateFlow<TvAppState> = stateManager.appState

// Never:
_appState.value = PAIRED  // ❌ Only stateManager can do this
```

---

## PART 9: TESTING STRATEGY (Not Implemented Yet)

### Unit Tests (Soon)
```
TvAppStateManager
  ├─ test state transitions
  ├─ test pairing substate machine
  └─ test connection recovery logic

QueueStateManager
  ├─ test API response → UI state mapping
  ├─ test ETA calculations
  └─ test next-N ticket selection
```

### Integration Tests (Later)
```
PairingRepository + MockPairingApiClient
QueueRepository + MockQueueApiClient
```

### Instrumented Tests (Later)
```
Screen compositions (Pairing, Queue, Error)
Navigation flows
```

---

## SUMMARY: WHAT EXISTS → WHAT NEEDS CHANGE

| Component | Current | Target | Migration |
|-----------|---------|--------|-----------|
| **Screens** | FirstLaunch, Pairing, Queue (3) | + Boot, + Error (5) | Add 2 screens |
| **State Machine** | AppState enum ✅ | TvAppStateManager | Refactor + move to domain layer |
| **Queue Display** | Static (one-shot) | Live polling + real-time | Add QueueRepository + polling loop |
| **Pairing** | Complete ✅ | Keep as-is | Move to shared data module |
| **Storage** | DeviceIdentityManager ✅ | + PairingStateCache | Add cache layer |
| **API Clients** | HttpURLConnection + Ktor | Unified TvApiClient | Consolidate |
| **Config** | Hardcoded URLs in code | TvAppConfig singleton | Extract to config |
| **Error Handling** | Graceful fallback | + Recovery overlays | Add connection state monitoring |

---

### Key Insight

**The current implementation is >80% correct**. The gaps are not architectural flaws but missing pieces:
1. Real-time queue updates (most critical)
2. State manager organization (refactoring, not rewrite)
3. Error recovery overlays (UI work)
4. Configuration management (extraction, not new tech)

**Recommendation**: Don't rewrite. Instead:
- **Phase 1**: Extract + organize existing code into proper layers
- **Phase 2**: Add queue polling + state managers  
- **Phase 3**: Add error/recovery screens
- **Phase 4**: Add monitoring, caching, optimization

This maintains **zero disruption** to working pairing flow while solving real-time queue display.

