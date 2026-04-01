# TV App Architecture: Executive Summary
**Status**: ✅ Architecture Design Complete  
**Next Action**: Begin Phase 1 Implementation  
**Estimated Timeline**: 2 weeks (10-15 dev days across 4 phases)  

---

## THE PROBLEM (Current State)
- ✅ Pairing works perfectly
- ✅ Queue display shows data once
- ❌ Queue data is static (not real-time)
- ❌ No error recovery (network failures = stale data forever)
- ❌ No proper state management (mix of UI + logic)
- ❌ Hardcoded URLs, inconsistent HTTP clients

**White screen issue**: SOLVED in previous session ✅  
**Real-time queue issue**: NEXT to solve ← YOU ARE HERE

---

## THE SOLUTION (Target Architecture)

### What We're Building
A **3-layer architecture** for TV app:

```
PRESENTATION LAYER
  ├─ Screens (FirstLaunch, Pairing, Queue, Boot, Error)
  ├─ ViewModels (TvAppViewModel, TvPairingViewModel, TvQueueViewModel)
  └─ Components (UI elements)

STATE LAYER
  ├─ TvAppStateManager (Central state machine)
  ├─ QueueStateManager (Display data formatting)
  └─ ConnectionStateManager (Network monitoring)

DATA LAYER
  ├─ Repositories (Pairing, Queue, Config)
  ├─ API Clients (Unified HTTP)
  └─ Local Storage (Device secrets, binding cache)
```

### Key Architectural Decisions

1. **TV is a thin client**: Backend owns all business logic, TV only displays
2. **Centralized state**: One TvAppStateManager holds ground truth
3. **Resilient by default**: All API failures have graceful fallbacks
4. **Polling, not WebSocket**: Simpler, more reliable for TV
5. **Offline-friendly**: Shows stale queue data if network down
6. **Configuration-driven**: Backend can control refresh rates, layouts

---

## STATE MACHINE (How It Works)

### Steady State (Normal Operation)
```
PAIRED → DISPLAY_READY → [Queue polling every 5 sec]
                         ├─ Show current ticket
                         ├─ Show next 5 tickets  
                         ├─ Show wait times
                         └─ Live updates from backend
```

### Error Recovery
```
Network down?
└─ NETWORK_ERROR 
   ├─ Show stale queue + "Reconectando..." badge
   ├─ Retry in background
   └─ Auto-recover when network restored

Binding revoked?
└─ PAIRING_REVOKED
   ├─ Show "Esta pantalla fue desvinculada"
   └─ User must contact staff to re-pair
```

### Bootstrap (First Time)
```
UNINITIALIZED 
→ BOOTSTRAPPING (check if device registered)
  → FIRST_LAUNCH (register device, select barbershop)
  → AWAITING_PAIRING (show pairing code)
  → PAIRING_IN_PROGRESS (poll for code redemption)
  → PAIRED (binding received) ✓
  → DISPLAY_READY (queue data loaded)
```

---

## FOUR PHASE MIGRATION PLAN

### Phase 1: Organize Code (1-2 days)
**Goal**: Refactor existing code into proper layers  
**No new functionality**, just reorganization  
**Safe to ship** ✅

- Extract `TvAppState` enum (shared)
- Create `TvAppStateManager` (central state machine)
- Create `TvAppCoordinator` (screen routing)
- Rename `AppStateViewModel` → `TvAppViewModel` (thinner)
- Move repositories to shared modules

**Outcome**: Clean, layered architecture ready for Phase 2

### Phase 2: Real-Time Queue (2-3 days)
**Goal**: Queue data refreshes every 5 seconds  
**Major feature**, tested on device  
**Ready to ship** ✅

- Create `QueueRepository` (fetch + polling logic)
- Create `QueueStateManager` (data → UI mapping)
- Create `TvQueueViewModel` (observable queue state)
- Create `BootScreen` (splash)
- Add live polling to `QueueDisplayScreen`

**Outcome**: Queue updates in real-time, no stale data

### Phase 3: Error Recovery (2-3 days)
**Goal**: Handle errors gracefully with recovery overlays  
**Major reliability improvement**  
**Ready to ship** ✅

- Extend `TvAppStateManager` with recovery logic
- Create `ErrorScreen` (revocation, network, etc.)
- Add `ConnectionStatusIndicator` overlay
- Implement binding refresh (24h cycle)
- Handle offline/reconnect scenarios

**Outcome**: Network failures don't break display, auto-recover

### Phase 4: Polish (1-2 days)
**Goal**: Production readiness  
**Internal improvements**  
**Final release package** ✅

- Create `TvAppConfig` (no more hardcoded URLs)
- Consolidate HTTP clients
- Add structured logging
- Add unit tests
- Document TODOs for Phase 5+

**Outcome**: Production-grade code quality

---

## MIGRATION STRATEGY

| Aspect | Approach |
|--------|----------|
| **Preserve pairing** | ✅ Zero changes to working pairing flow |
| **Avoid rewrite** | ✅ Refactor existing code, don't delete |
| **Ship incrementally** | ✅ Each phase is deployable |
| **Risk mitigation** | ✅ Rollback per phase is simple |
| **Testing** | ✅ Manual on device + unit tests |

### Why This Works
- Current infrastructure (pairing, API) is solid ✅
- We're only adding missing pieces (queue polling, state mgmt)
- Zero breaking changes per phase
- Can revert any phase without affecting others

---

## KEY DELIVERABLES

### Architecture Documents ✅ (Already Created)
1. **TV_APP_ARCHITECTURE.md** - Full design with state machine, layers, models
2. **TV_APP_MIGRATION_PLAN.md** - Detailed 4-phase plan with code examples
3. **TV_APP_FILES.md** - File-by-file recommendations (create/move/keep)
4. **This document** - Executive summary for quick reference

### Code Artefacts (To Be Implemented)
- 4 new domain state managers
- 3 new repository classes
- 3 new screen composables
- 2 new ViewModels
- 5 new shared data models
- Consolidated HTTP client
- Configuration singleton
- ~30 files total changes

---

## CRITICAL DESIGN DECISIONS

### Why Polling, Not WebSocket?
- ✅ Simpler to implement
- ✅ More reliable for TV (stateless per-request)
- ✅ No persistent connections needed
- ✅ Matches TV's "display client" nature
- Future: Can upgrade to WebSocket in Phase 5+

### Why Centralized TvAppStateManager?
- ✅ Single source of truth (no state conflicts)
- ✅ Testable state machine
- ✅ Clear state transitions
- ✅ Easier to debug than scattered state

### Why Offline-Friendly?
- ✅ TV on public WiFi = unreliable
- ✅ Better UX (show 30s-old data than nothing)
- ✅ Auto-recover when network restored
- ✅ No manual intervention needed

### Why Configuration-Driven?
- ✅ Can change behavior without rebuilding APK
- ✅ Different barbershops = different refresh rates
- ✅ A/B test different display modes
- ✅ Future: Media/animation support

---

## EXPECTED GAINS

| Metric | Current | Target | Impact |
|--------|---------|--------|--------|
| **Queue freshness** | 10+ min (stale) | 5 sec (live) | 🔴 Critical |  
| **Network resilience** | Crash on error | Show stale + retry | 🟢 Huge win |
| **Error clarity** | Confusing white screen | Clear error messages | 🟢 UX improvement |
| **Code maintainability** | Mixed concerns | Separation of layers | 🟢 Dev velocity |
| **Testability** | Hard to test | Unit testable | 🟢 Quality |
| **Configurability** | Hardcoded | Via BuildConfig | 🟢 Flexibility |

---

## POTENTIAL RISKS & MITIGATIONS

| Risk | Likelihood | Mitigation |
|------|-----------|-----------|
| Queue polling too aggressive | Low | Config parameterizes interval (5→30 sec) |
| Network state tracking incomplete | Low | Comprehensive ConnectionStateManager design |
| State manager complexity | Low | Phased: Phase 1 is simple, Phase 3 adds recovery |
| Breaking pairing flow | Very Low | Zero changes to PairingScreen/Repository |
| Battery drain from polling | Low | 5 sec interval + batching requests |
| Merge conflicts across phases | Low | Clean file boundaries per phase |

---

## QUESTIONS TO ASK BEFORE STARTING

1. **Polling rate**: Is 5 seconds OK, or should it be configurable per barbershop?
2. **Offline time**: How long should TV show stale data (30 min? 1 hour)?
3. **Battery**: Is polling every 5 sec acceptable for TV (not mobile)?
4. **Future media**: Will TV eventually play videos/ads? (affects architecture)
5. **Multi-display**: Will one TV show multiple barbershops? (not in current design)
6. **Analytics**: Should we log queue display metrics to backend? (Phase 5)

---

## WHAT'S NOT IN SCOPE (Yet)

### Phase 5+ Features
- [ ] WebSocket for true real-time (low priority for TV)
- [ ] Room database for queue caching (nice-to-have)
- [ ] Hilt dependency injection (not needed yet)
- [ ] Media/animation mode (future capability)
- [ ] Multi-display support (architectural consideration)
- [ ] Crash reporting (Sentry, Firebase)
- [ ] A/B testing (feature flags)

**Decision**: Keep architecture extensible for these, but don't implement yet.

---

## GETTING STARTED: YOUR NEXT STEPS

### Week 1: Phase 1 (Organize)
1. Create domain layer (state managers)
2. Create config/util layers
3. Refactor ViewModels
4. Move repositories to shared modules
5. Update MainActivity
6. Smoke test on device (pairing + queue display)

### Week 1.5: Phase 2 (Real-Time Queue)
1. Create QueueRepository + QueueStateManager
2. Create TvQueueViewModel + BootScreen
3. Add polling to QueueDisplayScreen
4. Test live updates on device
5. Add API response DTOs

### Week 2: Phase 3 (Error Recovery)
1. Extend TvAppStateManager + create ErrorScreen
2. Add ConnectionStatusIndicator
3. Implement binding refresh + recovery
4. Test network failure scenarios
5. Verify auto-recovery

### Week 2.5: Phase 4 (Polish)
1. Extract TvAppConfig
2. Add unit tests for state managers
3. Consolidate HTTP clients
4. Add structured logging
5. Final QA + documentation

---

## SUCCESS METRICS

When architecture is complete, you'll have:

✅ Queue data updates every 5 seconds (real-time)  
✅ Network failures handled gracefully (no white screen)  
✅ Auto-recovery when connection restored  
✅ Clear error messages to users  
✅ Clean, testable code structure  
✅ No hardcoded values  
✅ Unit test coverage for state machines  
✅ Production-ready TV app  

---

## FINAL CHECKLIST

Before starting Phase 1 implementation:

- [ ] Read all 3 architecture documents completely
- [ ] Ask any clarifying questions about design
- [ ] Set up feature branch (e.g., `feature/phase-1-refactor`)
- [ ] Review current PairingRepository implementation
- [ ] Confirm BuildConfig setup for different environments
- [ ] Test current app on device (baseline)
- [ ] Commit current state to main (save point for rollback)

---

## DOCUMENT REFERENCE

| Document | Purpose | Read Time |
|----------|---------|-----------|
| **TV_APP_ARCHITECTURE.md** | Full design details | 30 min |
| **TV_APP_MIGRATION_PLAN.md** | Phase-by-phase steps | 20 min |
| **TV_APP_FILES.md** | File recommendations | 15 min |
| **This summary** | Quick overview | 5 min |

**Total**: ~1 hour to fully understand the design.

---

## NEXT ACTION

👉 **Begin Phase 1: Organize code into proper layers**

Start with: `domain/state/TvAppStateManager.kt`

Questions? Review the detailed docs above or ask before coding.

---

**Design Date**: March 23, 2026  
**Status**: ✅ Ready for Implementation  
**Architect**: Architecture Analysis + Design  
**Reviewed by**: [Pending team review]  

