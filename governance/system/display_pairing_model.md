# Display Pairing Model

**Yottei — Modelo Global de Emparejamiento de Pantallas**  
**Versión:** 1.0 — Marzo 2026  
**Estado:** Vigente

---

## 1. Propósito

Define el modelo **global y vinculante** para cómo los dispositivos TV se vinculan a pantallas de display en la plataforma Yottei.

Este documento especifica:
- Entidades, identidades y ciclos de vida.
- Responsabilidades de cada componente (TV app, staff dashboard, backend).
- Flujos de pairing (numeric code, futuro QR).
- Estados y transiciones.
- Seguridad y expiración.
- Persistencia de datos.

---

## 2. Core Concepts

### 2.1 Device Identity

**Definición:** Identificador único y permanente de un dispositivo TV específico.

**Propiedades:**
- **device_id** (UUID): generado en primer launch de la app.
- **device_name** (string): "Living Room TV", "Barbershop Display 1" — asignado durante setup.
- **device_model** (string): "Roku Premiere", "Fire TV Cube" — detectado automáticamente.
- **device_secret** (secure token): usado para autenticación con backend (almacenado en EncryptedSharedPreferences).

**Generación:**
- Device ID se genera en **primer launch** de la app TV.
- Se almacena en **EncryptedSharedPreferences** (nunca se puede cambiar).
- Se envía al backend durante cualquier operación de pairing.
- No es secreto (se muestra en UI para debugging).

### 2.2 Display Identity

**Definición:** Identificador único de una "pantalla de display" en una barbería.

**Propiedades:**
- **display_id** (UUID): identificador único.
- **barbershop_id** (UUID): barbería a la que está asignada.
- **display_name** (string): "Waiting Room", "Queue Counter", etc.
- **display_config** (JSON): configuración compartida (colores, idioma, refresh rate, etc.).

**Propiedad:** El backend (TurnoExpress) es propietario de la identidad de display.

### 2.3 Device-Display Binding

**Definición:** Vinculación de un **device** a un **display**.

**Estructura:**
```json
{
  "binding_id": "uuid",
  "device_id": "uuid",
  "display_id": "uuid",
  "barbershop_id": "uuid",
  "created_at": "2026-03-22T...",
  "binding_status": "active" | "suspended" | "revoked",
  "display_config": { /* shared config snapshot */ }
}
```

**Propiedades clave:**
- **1:1 relationship:** Un device está vinculado a **exactamente 1 display** en un momento dado.
- **Immutable history:** Los bindings nunca se eliminan, solo se marcan como "revoked".
- **Sustitución:** Un device puede re-emparejarse a un display diferente (crea nuevo binding, marca el anterior como "revoked").
- **Persistencia:** La TV almacena localmente (`display_binding.json` en cache):
  - `device_id`
  - `display_id`
  - `barbershop_id`
  - `display_config` (snapshot)

---

## 3. Pairing Code Model

### 3.1 Temporary Pairing Code

**Definición:** Código numérico de 6 dígitos, **temporal y de un solo uso**, generado por el TV app durante pairing.

**Propiedades:**
- **code** (string): 6 dígitos (ej. "123456").
- **created_at** (timestamp): cuándo se generó.
- **expires_at** (timestamp): expira en **15 minutos**.
- **used** (boolean): marcado true después de ser canjeable.
- **code_status** (enum): `pending`, `used`, `expired`.
- **associated_device_id** (UUID): device que generó el código.

**Regla de Oro:** Un pairing code **nunca es una credencial de larga duración**.

El código es un **mecanismo de pairing temporal** que:
1. Se muestra en la pantalla del TV.
2. Se introduce en el staff dashboard.
3. Se canjea para generar un Device-Display Binding permanente.
4. Expira automáticamente en 15 minutos si no se usa.

### 3.2 Code Generation (en TV App)

Cuando la TV entra en `waiting_for_pairing` state:

```kotlin
// En el TV app
suspend fun generatePairingCode(deviceId: String): String {
    val code = generateRandomSixDigits()  // "123456"
    val codeData = PairingCodeData(
        code = code,
        deviceId = deviceId,
        createdAt = System.currentTimeMillis(),
        expiresAt = System.currentTimeMillis() + (15 * 60 * 1000),  // +15 min
        status = PairingCodeStatus.PENDING
    )
    // Guardar en memoria (no persistent)
    return code
}
```

El código **se guarda en memoria**, no en disco. Si el app se reinicia, se genera un nuevo código.

---

## 4. Pairing Flows

### 4.1 Numeric Code Pairing Flow (Current)

**Precondición:** TV app está instalada en un dispositivo. Primera ejecución o no vinculado a display.

**Paso 1: TV App — Generar Código**
```
TV App (unpaired)
  ↓
Show pairing screen: "Introduce este código en tu dashboard: 123456"
  ↓
Generate & display code (15 min validity)
  ↓
Poll backend: "Has este código sido canjeado?"
```

**Paso 2: Staff Dashboard — Introducir Código**
```
Staff Dashboard
  ↓
Staff entra en "Link Display" form
  ↓
Selecciona una pantalla (display)
  ↓
Introduce código TV: "123456"
  ↓
Envía al backend: POST /displays/{display_id}/pairing/redeem
    {
      "pairing_code": "123456",
      "display_id": "uuid",
      "staff_user_id": "uuid"
    }
```

**Paso 3: Backend — Validar y Binding**
```
Backend API
  ↓
Validar código:
  - Existe el código?
  - No ha expirado?
  - No ha sido usado?
  ✓ Validación exitosa
  ↓
Crear Device-Display Binding:
  - device_id = extractado del código
  - display_id = parámetro de request
  - barbershop_id = display.barbershop
  - binding_status = "active"
  ↓
Marcar código como "used"
  ↓
Retornar binding al dashboard:
    {
      "binding_id": "uuid",
      "device_id": "uuid",
      "display_id": "uuid",
      "display_config": { /* json */ }
    }
```

**Paso 4: TV App — Recibir Binding & Configurar**
```
TV App (polling)
  ↓
Recibe notificación: "Código aceptado"
  ↓
Fetch binding details del backend:
  GET /devices/{device_id}/binding
  ↓
Almacena localmente:
  - display_binding.json (en cache)
    {
      "device_id": "...",
      "display_id": "...",
      "barbershop_id": "...",
      "display_config": { /* snapshot */ }
    }
  ↓
Transición a estado "paired"
  ↓
Comienza a mostra contenido de display
```

**Duración total:** ~1-2 minutos.

---

### 4.2 QR Pairing Flow (Future)

**Nota:** El flujo QR reutiliza el modelo de pairing code subyacente. No introduce nuevos conceptos.

```
Staff Dashboard
  ↓
Display detail → "Generate QR for Pairing"
  ↓
Backend genera pair code (mismo modelo)
  ↓
Renderiza QR que contiene: "device_pairing:{code}"
  ↓
TV app (QR reader)
  ↓
Escanea QR → extrae código
  ↓
Sigue flujo idéntico al numeric code
```

El QR es solo un **vector de entrega** del código, no un modelo diferente.

---

## 5. TV App Pairing States

**Diccionario de estados del TV app respecto a pairing:**

| Estado | Significado | Comportamiento | Transición |
|--------|-------------|----------------|-----------|
| **unpaired** | No hay device_id aún (primera ejecución). | Generar device_id, ir a `waiting_for_pairing`. | → `waiting_for_pairing` |
| **waiting_for_pairing** | device_id existe, display_binding no existe (o expirado). | Generar código, mostrar en pantalla, esperar confirmación backend. | → `paired` (éxito) o `pairing_error` (fallo) |
| **paired** | device_id + display_id binding activos. | Mostrar contenido de display. Refrescar binding cada 24h. | → `unpaired` o `pairing_error` (binding revocado) |
| **pairing_error** | Error en validación de código o binding. | Mostrar error, ofrecer reintentar. Máximo 3 reintentos. | → `waiting_for_pairing` (reintentar) |

**Diagrama:**
```
[unpaired]
    ↓ (device_id generated)
[waiting_for_pairing] ←─────────────────┐
    ↓ (code redeemed)                    ↑ (retry)
    ├──→ [paired] ←──→ [pairing_error]   │
    │                      ↓ (max retries exceeded)
    │                  [show error permanently]
    └──→ [pairing_error] ──→ [show error, offer full reset]
```

---

## 6. Binding Lifecycle

### 6.1 Active Binding

**Duración:** Indefinida (hasta que sea revocado).

**Refresh Policy:**
- TV app **debe refrescar binding cada 24 horas**.
- Comprueba: `GET /devices/{device_id}/binding` → valida `display_id` sigue siendo el mismo.
- Si es diferente: marca como revoked, vuelve a `unpaired`.

### 6.2 Revoked Binding

**Cuándo se revoca:**
- Staff manualmente desvincula display en dashboard.
- BBQ (barbershop) cambia la configuración de display.
- TV device es marcado como "comprometido" por seguridad.
- Binding tiene edad > 1 año (rotación anual de seguridad).

**Comportamiento TV:**
- Continúa mostrando último contenido conocido por < 30 minutos.
- Luego transiciona a `unpaired` y muestra pairing screen nuevamente.

### 6.3 Deleted Device

**Cuándo:**
- Admin elimina registro del device del backend.

**Comportamiento TV:**
- Detecta en refresh: `404 Not Found` en binding fetch.
- Limpia `device_id` (o genera nuevo en siguiente reboot).
- Retorna a `waiting_for_pairing`.

---

## 7. Security Rules

### 7.1 Device Identity Security

**Regla:** El `device_secret` (token de autenticación) es la **credencial primaria** del device.

```kotlin
// En TV app
// Almacenar en EncryptedSharedPreferences SIEMPRE
val deviceSecret = EncryptedSharedPreferences
    .create("device_auth", context, ...)
    .getString("device_secret", null) ?: generateAndStore()

// Enviar en cada request al backend
val request = client
    .baseUrl("https://api.yottei.com")
    .addHeader("X-Device-ID", deviceId)
    .addHeader("X-Device-Secret", deviceSecret)
```

### 7.2 Pairing Code Security

**Regla:** Los pairing codes son **de un solo uso y con expiracion**.

- ❌ No reutilizar un código.
- ❌ No almacenar código en disco.
- ❌ No enviar código en logs.
- ✅ Expiración obligatoria: 15 minutos.
- ✅ Generar con suficiente entropía (Random.nextInt(1000000)).

### 7.3 Display Config Confidentiality

**Regla:** `display_config` puede contener información sensible (ej. URLs de API, settings privados).

- ✅ Almacenar en `EncryptedSharedPreferences`.
- ✅ Nunca loguear.
- ✅ Nunca transmitir sin HTTPS.
- ❌ No compartir entre devices.

### 7.4 Binding Validation

**Regla:** Antes de renderizar display content, TV DEBE validar el binding.

```kotlin
// ✅ CORRECTO
fun canRenderDisplayContent(): Boolean {
    val binding = loadDisplayBinding() ?: return false
    // Validar binding no expirado
    val isValid = isBindingValid(binding)
    return isValid && isDeviceIdMatching(binding)
}

// ❌ PROHIBIDO
fun canRenderDisplayContent(): Boolean {
    return lastDisplayIdShown != null  // ← Usar caché stale
}
```

---

## 8. Backend Responsibilities (TurnoExpress)

### 8.1 Pairing Code Management

- **Generar** códigos (random, 15 min TTL, secure storage).
- **Validar** códigos en redención (existe, no expirado, no usado).
- **Marcar como usado** después de redención exitosa.
- **Limpiar** códigos expirados (batch cleanup diario).

### 8.2 Device-Display Binding Management

- **Crear** binding cuando código se canjea.
- **Validar** requests con device_id + device_secret.
- **Refrescar** binding info si se solicita.
- **Revocar** binding cuando staff lo solicita.
- **Monitorear** devices "en exceso" (múltiples devices simultáneamente).

### 8.3 Display Config Delivery

- **Almacenar** configuración de display a nivel de display_id.
- **Snapshottear** config en el momento de binding.
- **Distribuir** cambios de config a devices vinculados (proactivamente o en poll).

---

## 9. Staff Dashboard Responsibilities

### 9.1 Display Management

- **Crear** displays (asignar a barbería).
- **Editar** detalles (nombre, configuración).
- **Listar** displays de la barbería.

### 9.2 Pairing UI

- **Mostrar** lista de displays sin device vinculado.
- **Formulario** para introducir pairing code.
- **Validar** localmente que es 6 dígitos.
- **Enviar** a backend: POST /displays/{display_id}/pairing/redeem.
- **Mostrar** resultado (éxito / error).
- **Confirmación** visual: "Display vinculado a {device_name}".

### 9.3 Unbinding / Device Management

- **Desvinculación:** Staff puede desvinculara un display de un device.
- **Historial:** Mostrar qué devices han estado vinculados a un display.
- **Reset:** Opción de "resetear pairing" si device se perdió/robó.

---

## 10. Backward Compatibility & Future

### 10.1 Adding QR Pairing

El QR pairing se implementará **sin cambiar el modelo de código subyacente**.

Cambios únicamente en:
- TV app: agregar QR reader capability.
- Dashboard: agregar botón "Generate QR" → renderiza código en QR.
- Ningún cambio en backend (código sigue siendo el mismo).

### 10.2 Future: Passwordless / OAuth Pairing

Si en futuro se añade OAuth (ej. "Link with Google Account"):
- Mantener modelo de device_secret primario.
- OAuth puede ser un **método adicional** de autenticación, no reemplazo.
- Device identity sigue siendo UUID + secret, no OAuth identity.

---

## 11. Enforcement & Monitoring

### 11.1 Code Review

- Verificar que pairing code **nunca se loguea**.
- Verificar que device_secret se almacena encrypted.
- Verificar que binding validation ocurre antes de renderizar content.

### 11.2 Monitoring

- **Pairing success rate:** % de codes canjeados exitosamente (meta: > 99%).
- **Pairing latency:** tiempo desde code generation → binding ready (meta: < 2min).
- **Binding validity:** % de devices con binding válido (meta: > 99%).
- **Code expiration:** % de codes que expiran sin canjearse (meta: < 5%).

### 11.3 Security Auditing

- Log (sin detalles sensibles):
  - "Device {device_id} paired to display {display_id}".
  - "Device {device_id} unpaired (staff revocation)".
  - "Pairing code {hex(sha256(code))} redeemed".
- Nunca log: plaintext codes, secrets, full configs.

---

## 12. Terminology & Glossary

| Término | Definición |
|---------|-----------|
| **Device** | Dispositivo TV físico ejecutando app-tv. |
| **Display** | Entidad lógica de display contenedor (pertenece a barbería). |
| **Binding** | Vinculación activa de device ↔ display. |
| **Pairing Code** | Código numérico temporal (6 dígitos) para iniciar pairing. |
| **Device ID** | UUID único por dispositivo TV (generado en primer launch). |
| **Device Secret** | Token de autenticación para el device (almacenado encrypted). |
| **Staff Dashboard** | Plataforma web para managers (emparejamiento, configuración). |
| **Pairing State** | Estado actual del device regarding binding (unpaired, paired, etc.). |

---

**Versión:** 1.0 — Marzo 2026  
**Próxima revisión:** Septiembre 2026
