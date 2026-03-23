# TV Architecture

**Android TV Module — Arquitectura y Límites Permitidos**  
**Versión:** 1.0 — Marzo 2026  
**Estado:** Vigente

---

## 1. Propósito

Define la estructura arquitectónica permitida, límites de módulos, flujo de datos, y patrones prohibidos para la aplicación Android TV.

---

## 2. Allowed Layers (Capas Permitidas)

El módulo `app-tv` está organizado en **4 capas lógicas**:

```
┌─────────────────────────────────────────────────────────────┐
│                      UI Layer (Composables)                  │
│           (Components, Screens, Navigation)                  │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│               Presentation Layer (ViewModel)                 │
│        (State Management, Recomposition, ErrorHandling)      │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                  Data Layer (Repository)                     │
│         (Aggregation, Caching, Transformation)              │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│              Remote Data (Backend API + Realtime)            │
│                 (Supabase, Network Calls)                    │
└─────────────────────────────────────────────────────────────┘
```

### 2.1 UI Layer

**Responsabilidad:** Renderizado visual y navegación.

**Permitido:**
- Jetpack Compose Composables (stateless y stateful).
- Navegación entre pantallas.
- Binding de estado a UI.
- Manejo de affordances (botones, controles remoto).

**Prohibido:**
- Llamadas directas a APIs.
- Lógica de negocio.
- Instantiación de ViewModel o Repository.
- Estado mutable global.

**Ubicación:** `app-tv/src/main/java/com/yotei/tv/ui/`

```
ui/
├── screens/
│   └── QueueDisplayScreen.kt      ← pantalla principal
├── components/
│   ├── QueueDisplayHeader.kt
│   ├── CurrentTicketCard.kt
│   ├── NextTicketsSection.kt
│   └── QueueStatsPanel.kt
└── navigation/
    └── TvNavGraph.kt              ← rutas y navegación
```

---

### 2.2 Presentation Layer (ViewModel)

**Responsabilidad:** Gestión de estado, transformaciones, y orquestación.

**Permitido:**
- ViewModels con StateFlow / LiveData.
- Reacción a cambios de estado desde Repository.
- Transformación de datos (join, filter, map).
- Manejo de errores y loading states.
- Lógica de presentación (qué mostrar, cuándo mostrar).

**Prohibido:**
- Llamadas directas a APIs o Supabase.
- Lógica de negocio (decisiones sobre orden, elegibilidad).
- MutableState global.
- Acceso a Context o Activity (excepto recurso de strings).

**Ubicación:** `app-tv/src/main/java/com/yotei/tv/presentation/`

```
presentation/
└── viewmodel/
    └── QueueDisplayViewModel.kt   ← gestión de estado
```

**Ejemplo:**
```kotlin
class QueueDisplayViewModel(
    private val queueRepository: QueueRepository,
    private val connectionManager: ConnectionManager
) : ViewModel() {
    private val _uiState = MutableStateFlow<QueueDisplayUiState>(QueueDisplayUiState.Loading)
    val uiState: StateFlow<QueueDisplayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            queueRepository.queueStateFlow()
                .catch { error -> _uiState.value = QueueDisplayUiState.Error(error) }
                .collect { state -> _uiState.value = QueueDisplayUiState.Success(state) }
        }
    }

    // Transformación de datos (lógica de presentación, no de negocio)
    private fun transformQueueState(state: QueueState): DisplayModel {
        // ... mapear, filtrar, formatear para UI
    }
}
```

---

### 2.3 Data Layer (Repository)

**Responsabilidad:** Agregación de fuentes de datos, caching, y transformaciones antes de ViewModel.

**Permitido:**
- Implementación de interfaces de Repository.
- Lógica de caching (memoria, disco).
- Coordinación entre múltiples fuentes (API local, realtime subscriptions).
- Validación de datos recibidos.
- Transformación de DTOs a Domain Models.

**Prohibido:**
- Lógica de negocio.
- Acceso directo a UI.
- ViewModels o Composables.
- State global mutable.

**Ubicación:** `app-tv/src/main/java/com/yotei/tv/data/`

```
data/
├── repository/
│   └── QueueRepository.kt         ← acceso a datos
├── datasource/
│   ├── RemoteQueueDataSource.kt   ← APIs y realtime
│   └── LocalQueueDataSource.kt    ← persistencia local
└── mapper/
    └── QueueMapper.kt             ← transformación DTO → Domain
```

**Ejemplo:**
```kotlin
class QueueRepository(
    private val remoteDataSource: RemoteQueueDataSource,
    private val localDataSource: LocalQueueDataSource
) {
    fun queueStateFlow(): Flow<QueueState> = flow {
        // Obtener del backend, cachear, validar
        val state = remoteDataSource.getQueueState()
        if (state.isValid()) {  // Validación de datos
            localDataSource.cacheQueueState(state)
            emit(state)
        }
    }
}
```

---

### 2.4 Remote Data Layer

**Responsabilidad:** Comunicación con APIs y servicios backend.

**Permitido:**
- Clientes HTTP (Retrofit, OkHttp).
- Supabase client (realtime, autenticación).
- Manejo de errores de red.
- Implementación de backoff y reintentos.

**Prohibido:**
- Lógica de presentación.
- ViewModels o state management.
- Transformación compleja (solo DTO).

**Ubicación:** `core/network/` (módulo compartido)

```
network/
├── api/
│   └── QueueApi.kt               ← cliente HTTP
├── supabase/
│   └── SupabaseClient.kt         ← cliente realtime
└── dto/
    └── QueueStateDto.kt          ← modelos serialización
```

---

## 3. Module Boundaries (Límites de Módulos)

### 3.1 `app-tv` Module (Aplicación TV)

**Es propietario de:**
- UI (Composables, Screens).
- ViewModels específicos de TV.
- Configuración de TV (AndroidManifest TV-specific).
- Validación de parámetros de entrada (barbershop_id).
- Recursos de strings, colores, dimensiones de TV.
- Temas (Material3 personalizado para TV si es necesario).

**Depende de:**
- `core-model` (Domain Models: `QueueState`, `QueueTicket`, `Barbershop`).
- `core-common` (Composables generales, utilidades).
- `data` (Repository implementations).
- `network` (API clients).

**Nunca debe:**
- Acceder a la aplicación móvil (`app` module).
- Tener dependencias circulares.
- Contener lógica de negocio canónica.
- Definir contratos de dominio (eso es tarea de `core-model`).

**Ejemplo de dependencia permitida:**
```gradle
dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-common"))
    implementation(project(":data"))
    implementation(project(":network"))
}
```

---

### 3.2 `core-model` Module (Domain Models Compartidos)

**Es propietario de:**
- Enums canónicos (`QueueStatus`, `BarbershopStatus`, etc.).
- Data classes de dominio (`QueueTicket`, `Barbershop`, `QueueState`).
- Invariantes de negocio documentadas.

**Puede ser usado por:**
- `app` (app móvil).
- `app-tv` (app TV).
- `data` (repository implementations).
- `network` (DTOs y mappers).

**Nunca debe:**
- Contener lógica de presentación.
- Depender de Compose o Android UI.
- Depender de capas de datos o red.

---

### 3.3 `core-common` Module (Utilidades Compartidas)

**Es propietario de:**
- Extensiones Kotlin reutilizables.
- Composables base/base components.
- Temas y estilos compartidos (si aplica).
- Dependencias comunes (Compose, Material3).

**Puede ser usado por:**
- `app`.
- `app-tv`.
- Cualquier módulo que necesite utilidades.

---

### 3.4 `data` Module (Repository Implementations)

**Es propietario de:**
- Implementaciones de Repository (QueueRepository, etc.).
- Data sources (Remote, Local).
- Mappers (DTO → Domain Model).
- Caching y persistencia local.

**Depende de:**
- `core-model` (Domain Models).
- `network` (API clients).

**Puede ser usado por:**
- `app`.
- `app-tv`.

---

### 3.5 `network` Module (API Clients)

**Es propietario de:**
- Clientes HTTP (Retrofit).
- Clientes Supabase.
- DTOs (modelos de serialización).
- Configuración de red.

**Depende de:**
- `core-model` (mínimamente, solo si necesita Domain Models).

---

## 4. Data Flow (Flujo de Datos)

### Unidireccional (One-way binding)

**Regla:** Los datos fluyen desde Remote → Data → ViewModel → UI. **Nunca al revés** (excepto user input).

```
Remote Data (Supabase)
    ↓
DataSource (transformación DTO → Domain)
    ↓
Repository (agregación, caching)
    ↓
ViewModel (estado, transformaciones de presentación)
    ↓
UI (renderizado)
    ↓ (user input: botón remoto)
ViewModel (manejo de evento)
    ↓
Repository (llamada a acción)
    ↓
Remote Data (enviar cambio)
```

**Prohibido:**
- UI escribe directamente a Repository.
- ViewModel modifica campos de Domain Models.
- DataSource accede a ViewModels.

---

## 5. Forbidden Architecture Patterns (Patrones Prohibidos)

### ❌ Patrón 1: Business Logic in UI

```kotlin
// PROHIBIDO
@Composable
fun QueueScreen() {
    val isEligible = ticket.status == "waiting" && hasCheckedIn
    if (isEligible) { /* render */ }
}
```

### ❌ Patrón 2: Direct API Calls from UI

```kotlin
// PROHIBIDO
@Composable
fun TicketCard() {
    LaunchedEffect(Unit) {
        val response = supabase.from("queue").select().execute()
    }
}
```

### ❌ Patrón 3: Global Mutable State

```kotlin
// PROHIBIDO
object GlobalState {
    var queueState: QueueState? = null  // ← State global mutable
}
```

### ❌ Patrón 4: ViewModel Instantiation in Composables

```kotlin
// PROHIBIDO
@Composable
fun QueueScreen() {
    val viewModel = QueueDisplayViewModel()  // ← Crear aquí
}
```

**Correcto:** Usar `hiltViewModel()` o inyección de dependencias.

### ❌ Patrón 5: Circular Dependencies

```gradle
// PROHIBIDO
// app-tv depende de app
// app depende de app-tv
```

### ❌ Patrón 6: Repository Logic in ViewModel

```kotlin
// PROHIBIDO
class QueueViewModel {
    fun refreshQueue() {
        val remoteState = api.getQueue()
        val localState = db.getQueue()
        // ← Agregar datos aquí (es tarea de Repository)
        val merged = remoteState + localState
    }
}
```

---

## 6. TV is a Thin Client (TV es un Cliente Delgado)

**Declaración:** El módulo `app-tv` es **un cliente delgado que no replica lógica de backend**.

**Implicaciones:**
- No hay cálculos de ETA, orden de fila, o elegibilidad.
- No hay almacenamiento persistente de lógica de negocio.
- No hay "sincronización bidireccional" of state.
- No hay caché offline completo que implique offline-first.

**Única caché permitida:**
- Último estado conocido (para recuperación tras crash).
- Parámetros de configuración del dispositivo (brightness, language).
- Configuración de conexión (URL del backend).

**No es permitido:**
- Caché completo de cola (tickets históricos, etc.).
- Sincronización local-remoto de conflictos.
- Decisiones de negocio local basadas en caché stale.

---

## 7. Enforcement

- **Code Review:** Verificar que PRs respeten límites de módulos y capas.
- **Linting:** Usar Lint rules para detectar dependencias circulares.
- **TestingStrategy:** Escribir tests que verifiquen separación de capas.
- **Documentation:** Mantener estos limits actualizados y linkearlos en PRs.

---

**Versión:** 1.0 — Marzo 2026  
**Próxima revisión:** Septiembre 2026
