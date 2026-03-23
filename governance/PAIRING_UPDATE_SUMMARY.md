# Governance Documentation Update — Display Pairing Model

**Status:** Complete  
**Date:** March 22, 2026  
**Scope:** Android TV app pairing model integration

---

## Summary

Successfully updated governance documentation across two workspace roots to define the first-class display pairing flow for the Android TV app.

---

## 1. System Governance Created

**Location:** `Yotei_app/governance/system/`

###  📄 [governance/system/README.md](governance/system/README.md)
- Describes the system governance structure
- Defines relationship to TV and backend governance
- Lists precedence hierarchy

### 📄 [governance/system/display_pairing_model.md](governance/system/display_pairing_model.md)  
**Key Content:**
- Core concepts: Device Identity, Display Identity, Device-Display Binding
- Pairing code model (6 digits, 15-minute TTL, single-use)
- Numeric code pairing flow (current) and QR pairing flow (future)
- TV app pairing states: `unpaired` → `waiting_for_pairing` → `paired` / `pairing_error`
- Binding lifecycle and revocation
- Security rules (device secret storage, code handling)
- Backend and dashboard responsibilities
- Backward compatibility and extensibility
- Monitoring and enforcement

---

## 2. TV Governance Updated

**Location:** `Yotei_app/governance/tv/`

###  📄 [tv_app_principles.md](governance/tv/tv_app_principles.md)
**Added:**
- **Principle 8: Required Pairing Before Display Content**
  - TV must never show production content without valid binding
  - Pairing is mandatory prerequisite
  - Code is temporary, never a long-term credential
  - Security note on code/secret handling

###  📄 [tv_state_contract.md](governance/tv/tv_state_contract.md)
**Added:**
- **Section 5: PairingState Contract**
  - Sealed class hierarchy (Unpaired, WaitingForPairing, Paired, PairingError)
  - Fields: deviceId, pairingCode, displayId, displayConfig, bindingValidUntil
  - PairingErrorReason enum
  - Contract rules (immutability, no state fabrication)
- **Section 11: Pairing State Validation**
  - Binding validation before transitioning to `Paired`
  - Sample code for validation logic

### 📄 [tv_device_behavior.md](governance/tv/tv_device_behavior.md)
**Added:**
- **Section 2.0: Pairing Startup Flow (Prerequisite)**
  - Completely specified TV pairing flow
  - Pairing Mode visual state and behavior
  - Code generation, display, validation
  - Polling loop for code redemption
  - Transition to queue display after pairing
  - Sample Kotlin implementation
- **Section 2.3: Display Configuration After Pairing**
  - Display binding cache structure
  - Configuration application and snapshot rules
- **Section 3.0: Binding Refresh & Validation**
  - 24-hour refresh requirement
  - Revocation detection and handling
  - Sample refresh implementation

### 📄 [tv_ui_rules.md](governance/tv/tv_ui_rules.md)
**Added:**
- **Section 8: Pairing Screen UI Rules (New)**
  - **8.1 Pairing Code Display:**
    - 72sp Bold monospace numbers
    - Large, clear, distance-readable (3+ meters)
    - Validity timer in yellow
    - Loading animation (subtle, non-distracting)
  - **8.2 Pairing Error Screen:**
    - Error icon + message
    - Auto-retry indication
    - Persistent display (not dismissible)
  - **8.3 Pairing Success Screen (Transition):**
    - Success confirmation with binding details
    - Barbershop and display names
    - Countdown to queue display
    - 2-3 second duration
- **Renumbered Sections:** Updated component rules (8→9) and enforcement (9→10)

### 📄 [tv_non_functional_requirements.md](governance/tv/tv_non_functional_requirements.md)
**Added:**
- **Section 4.3: Pairing Latency Requirements**
  - Max latencies for each pairing operation (<2 operations per second)
  - Pairing success rate goal: > 99.5%
  - Code expiration: 15 minutes
  - Polling interval: 2 seconds (not too frequent, not too slow)
  - Target: <2 minute total pairing cycle

### 📄 [tv_architecture.md](governance/tv/tv_architecture.md)
**Updated:**
- **Section 2.1:** Added pairing screens and components
  - New PairingScreen.kt
  - New pairing/ component folder
  - Updated navigation graph reference
- **Section 2.2:** Added PairingViewModel
  - Sample PairingViewModel with state management
- **Section 2.3:** Added PairingRepository
  - Data layer for pairing (new)
  - Local device and binding stores
  - Mapper for binding transformation
  - Sample repository implementation
- **Section 2.4:** Added pairing API endpoints (new)
  - Required endpoints: checkCodeStatus, getDeviceBinding
  - Data models: DisplayBindingDto

---

## 3. File Structure Summary

### Created Files  
✅ `Yotei_app/governance/system/README.md`  
✅ `Yotei_app/governance/system/display_pairing_model.md`

### Modified Files  
✏️ `Yotei_app/governance/tv/tv_app_principles.md` (+30 lines)  
✏️ `Yotei_app/governance/tv/tv_state_contract.md` (+80 lines)  
✏️ `Yotei_app/governance/tv/tv_device_behavior.md` (+120 lines)  
✏️ `Yotei_app/governance/tv/tv_ui_rules.md` (+110 lines)  
✏️ `Yotei_app/governance/tv/tv_non_functional_requirements.md` (+35 lines)  
✏️ `Yotei_app/governance/tv/tv_architecture.md` (+120 lines)

---

## 4. Key Governance Rules

### Pairing Prerequisites
- TV app **must not** render queue display content without valid binding
- Pairing is **mandatory** on first launch or when binding expires
- Device runs in `unpaired` → `waiting_for_pairing` → `paired` cycle

### Pairing Code Contract
- **Format:** 6 random digits (e.g., "123456")
- **Lifespan:** 15 minutes from generation
- **Storage:** Memory only (never persisted in code)
- **Use:** Single-use (cannot be redeemed twice)
- **Auto-renewal:** TV generates new code when prior expires

### Binding Persistence
- **Location:** `EncryptedSharedPreferences` + cache dir
- **Contents:** deviceId, displayId, barbershopId, displayConfig snapshot
- **Validity:** 24 hours (then must refresh from backend)
- **Refresh:** Automatic every 24h, manual on staff revocation

### UI Expectations
- **Pairing screen:** 72sp pairing code, centered, high contrast (#FFFFFF on #10101A)
- **Error handling:** Visible error messages, persistent (not dismissible)
- **Success:** 2-3 second confirmation before auto-transitioning to display
- **All screens:** Fullscreen, no status bar, designed for 3+ meter viewing distance

### Performance SLA
- **Pairing cycle:** < 2 minutes (generation → binding ready)
- **Code generation:** < 1 second
- **Poll latency:** ≤ 2 seconds per check
- **Success rate:** > 99.5% of codes redeemed successfully
- **Code expiration:** Automatic generation of new code after 15 minutes

---

## 5. Relationship to TurnoExpress Backend

The display pairing model is **complementary** to backend governance:

- **Governance backend** (TurnoExpress): defines domain truth (pairing code generation, binding creation, revocation logic)
- **Governance TV** (Yottei_app): defines client implementation of pairing (UI states, startup flow, validation)
- **Governance system** (Yottei_app): defines global pairing model (device identity, code contract, binding lifecycle)

**In conflicts:** Backend governance takes precedence. TV governance must conform.

---

## 6. Future Extensibility

### QR Pairing (Planned)
The pairing model is designed to support QR codes without refactoring:
- QR is a **delivery method** for the pairing code, not a separate model
- Backend generates same 6-digit code
- Staff dashboard renders code as QR
- TV app decodes QR and follows same numeric code flow
- **No changes needed** to binding model, validation, or persistence

### Passwordless / OAuth Pairing (Future)
- Can be added as additional auth method (not replacement)
- Device identity remains UUID + secret (not OAuth identity)
- OAuth could supplement (not replace) device secrets

---

## 7. Validation Checklist

- [x] Pairing states fully defined (Unpaired, WaitingForPairing, Paired, PairingError)
- [x] Code contract specified (6 digits, 15-min TTL, single-use)
- [x] Binding lifecycle documented (generation, refresh, revocation)
- [x] UI rules for pairing screens created
- [x] API endpoints required for pairing listed
- [x] Startup flow fully specified with code samples
- [x] Security rules for device secret and code handling defined
- [x] Backward compatibility and future extensibility covered
- [x] Non-functional requirements (latency, success rate) documented
- [x] Enforcement mechanisms specified

---

## 8. Version & Updates

**Governance Version:** 1.0 — March 2026  
**Status:** Vigente (Active)  
**Next Review:** September 2026

All governance documents include update dates and revision schedules.

---

## Implementation Next Steps

1. **Create PairingViewModel** in `app-tv/src/main/java/com/yotei/tv/presentation/viewmodel/`
2. **Create PairingRepository** in `app-tv/src/main/java/com/yotei/tv/data/repository/`
3. **Create PairingScreen (Composable)** in `app-tv/src/main/java/com/yotei/tv/ui/screens/`
4. **Implement display binding local storage** in `LocalDisplayBindingStore`
5. **Create backend API client interface** in `network/api/PairingApi.kt`
6. **Update MainActivity** to check pairing state before showing queue display
7. **Add tests** for pairing state transitions and code validation

---

**End of Documentation Update Summary**
