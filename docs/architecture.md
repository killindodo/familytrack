# FamilyTrack — Architecture Plan (Phase 1)

> Status: PROPOSED — awaiting owner "Go ahead" before any code is written.
> Spec source: /root/projects/familytrack/.agents/memory/active_spec.md

## 1. Modules (Gradle monorepo, one repo: `familytrack`)

```
familytrack/
├── build.gradle.kts            (root, shared config)
├── settings.gradle.kts
├── gradle/libs.versions.toml   (single version catalog — no version drift)
├── core/                       (shared Kotlin module — no Android UI)
│   ├── PairingModels.kt        (codes, payloads)
│   ├── LocationModels.kt       (LocationUpdate, UncleStatus, GeoEvent)
│   ├── Fsm.kt                  (shared enums: AppRole, AlertType, GeofenceType)
│   └── Time.kt                 ("2 min ago" formatting, EN/HI aware)
├── uncle-app/                  (com.killindodo.familytrack.uncle)
│   ├── ui/ (Compose: Welcome, Pairing, Permissions, Done, StatusScreen)
│   ├── service/LocationService.kt      (foreground, FusedLocationProvider)
│   ├── service/BootReceiver.kt         (BOOT_COMPLETED → restart service)
│   ├── data/RoomQueue.kt               (offline outbox)
│   └── watchdog/WorkManagerWatchdog.kt (every 15 min: ensure service alive)
├── watcher-app/                (com.killindodo.familytrack.watcher)
│   ├── ui/ (Compose: SignIn, AddUncle(QR), HomeMap, UncleDetail, Alerts)
│   ├── map/OsmdroidMap.kt      (osmdroid + OSM tiles)
│   └── data/Repo.kt            (Firestore listeners, cleanup)
└── functions/                  (Cloud Functions, Node 20)
    ├── onUncleEventCreated.ts  (SOS / geofence → FCM to paired watcher)
    ├── onLocationWrite.ts      (battery <15% alert, one-shot)
    ├── scheduledStaleCheck.ts  (no update >10 min → alert; 30-day cleanup)
    └── pairing/codes.ts        (pairing code issue/redeem)
```

## 2. Data flow (the whole story in one line each)

**Location reporting: ON-DEMAND (owner decision).** No continuous streaming. Watcher taps "Check location" → writes `uncles/{id}/commands/{autoId} {type:"CHECK_IN", ts}` → uncle device has a lightweight Firestore listener on its commands subcollection (near-zero battery) → on request: takes ONE high-accuracy fix → writes `uncles/{id}.lastLocation` + appends `uncles/{id}/locations/{autoId}` → marks command answered. Requests older than 30 min expire silently. If the write fails (offline), the fix goes to the Room outbox and syncs on reconnect. If no answer within 5 min → watcher alert "did not respond" (Cloud Function on scheduled check or on command age).

**SOS:** StatusScreen red button → immediate high-accuracy fix + write `uncles/{id}/events` {type:"SOS", location, ts} → Cloud Function `onUncleEventCreated` → FCM high-priority data message to paired watcher's token (stored on `watchers/{uid}.fcmToken`).

**Watcher live:** HomeMap = Firestore `onSnapshot(uncles where pairedWatcherUid == myUid)` → marker per uncle (distinct color: uncle1 #10B981 emerald, uncle2 #F59E0B amber, N-th = palette) → bottom card per selected uncle (name, "2 min ago", battery %, speed) → "Navigate" → intent `google.navigation:q=lat,lng`.

**History:** tap uncle → today's polyline from `uncles/{id}/locations` (ts range query) + date picker (last 7 days, Firestore limit via composite index on createdAt desc).

**Geofence:** watcher draws circle on map (default 100 m around "Home") → saved to `uncles/{id}.geofences[]` → uncle app downloads on pair + listens to doc changes → each fix: distance check (haversine, clamp) → enter/leave event (debounced: state change only after 2 consecutive readings) → `events` doc → Cloud Function → FCM.

**Alerts b/c/d:**
- (b) no answer to a check-in within 5 min: Cloud Function scheduled every 1 min scans unanswered `commands` older than 5 min → FCM "did not respond" alert (flag on command to avoid spam).
- (c) battery <15%: `onLocationWrite` function fires once per battery drop (flag on doc, cleared when battery rises >25%).
- (d) geofence: via events (above).
- History cleanup: scheduled function deletes `locations/{ts < now-30d}` (batched ≤500/run). Functions require Firebase **Blaze** plan (see §6 decision).

**Pairing:** watcher "Add uncle" → generates 6-digit code + QR (payload: JSON {project, code, v}) → code stored `pairingCodes/{code}` {watcherUid, expiresAt: +10 min, used:false} → uncle app scans QR (Mobile Vision / ML Kit barcode, no extra account) or types code → uncle device creates `uncles/{id}` {name, pairedWatcherUid, createdAt} → code marked used → watcher sees new uncle appear. One-time, short-lived. No secrets in repo; all access via rules.

## 3. Firestore security rules (the lock)

```
match /watchers/{uid} {
  allow read, write: if request.auth != null && request.auth.uid == uid;
}
match /uncles/{id} {
  // uncle device writes only its own doc's lastLocation/status fields
  allow read: if request.auth != null &&
    (resource.data.pairedWatcherUid == request.auth.uid ||
     resource.data.deviceUid == request.auth.uid);
  allow create: if request.auth != null && request.resource.data.pairedWatcherUid == request.auth.uid;
  allow update: if request.auth != null &&
    (resource.data.pairedWatcherUid == request.auth.uid      // watcher may edit geofences/name
     || resource.data.deviceUid == request.auth.uid);        // own device writes location/battery
  allow delete: if request.auth != null && resource.data.pairedWatcherUid == request.auth.uid;
  match /locations/{ts} {
    allow read: if request.auth != null && get(/databases/$(database)/documents/uncles/$(id)).data.pairedWatcherUid == request.auth.uid;
    allow create: if request.auth != null && request.resource.data.deviceUid == request.auth.uid;
    allow delete: if request.auth != null && get(...).data.pairedWatcherUid == request.auth.uid; // cleanup
  }
  match /events/{eventId} {
    allow read: if request.auth != null && get(/databases/$(database)/documents/uncles/$(id)).data.pairedWatcherUid == request.auth.uid;
    allow create: if request.auth != null && request.resource.data.deviceUid == request.auth.uid;
  }
}
match /pairingCodes/{code} {
  allow read: if request.auth != null;      // code itself is the secret, short-lived
  allow create: if request.auth != null && request.resource.data.watchersUid == request.auth.uid;
  allow update: if request.auth != null;    // mark used
}
```
deviceUid = the anonymous auth uid of the uncle device, stored on the doc at creation.

## 4. Uncle-app permissions flow (the honest stairway)

1. **Fine location** — "So we can see where you are if you need help."
2. **Background location ("Allow all the time")** — separate screen, exact Android wording, only requested AFTER fine location granted (Play/OS requirement).
3. **Notifications (API 33+)** — "This shows 'Location sharing is on' — it keeps the app working."
4. **Battery exemption** — ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS + per-OEM autostart guidance screens (detected by manufacturer): Xiaomi (Autostart + Battery saver "No restrictions"), Oppo/Realme (Battery → "Allow background activity" + "Lock app in recents"), Vivo (i Manager → Autostart + High background power), Samsung (Battery → Unmonitored apps + "Remove app from sleeping").
5. Each step can be skipped-but-warned (never trap the uncle in a dead end); final screen summarizes what's ON vs needs attention.

## 5. Battery-kill resilience (the OEM war)

- A foreground service keeps the Firestore command listener alive + honest notification; GPS is only powered during an actual check-in or SOS. Battery impact is near-idle.
- BOOT_COMPLETED receiver → startForegroundService.
- WorkManager periodic watchdog (15 min): checks service alive; if dead → restart.
- Room outbox survives kills; on next launch, backfill sync.
- Status screen warning banner if: location off / airplane mode / no internet.

## 6. ⚠️ The one external constraint (needs your one decision)

Firebase **Cloud Functions cannot be deployed on the free "Spark" plan** — alert pushes (SOS/stale/battery/geofence) need the **Blaze plan** (requires adding a card; actual usage at our scale stays inside the free quotas: 2M invocations, 5 GB storage — expected bill ₹0).

- **Option A (Recommended):** Enable Blaze → full alerts as specified. Realistically ₹0/month.
- **Option B:** Stay on Spark → SOS/stale/battery/geofence alerts appear **only inside the watcher app while it's open** (Firestore listeners). No push notifications when app is closed. Zero cost, no card.

## 7. Build & distribution strategy (the phone-build reality)

This phone (ARM64) cannot run the Android build tools (aapt2 is x86_64-only) — a local Compose build here is impossible. The honest path, which also matches your "debug + signed release via Gradle" requirement:

**GitHub Actions CI** (free for public repos, unlimited minutes):
- On every push: `./gradlew :uncle-app:assembleDebug :watcher-app:assembleDebug` + signed release (`minifyEnabled true`, R8).
- Keystore: generated once, stored base64 in **GitHub Secrets** (KESTORE_B64, KESTORE_PASS, KEY_PASS, KEY_ALIAS); gradle reads from env → nothing secret in the repo.
- APKs uploaded to the GitHub Release page → you download `uncle-release.apk` / `watcher-release.apk` from your phone and sideload. Permanent, repeatable, versioned.

## 8. Phases (each confirmed building before the next)

1. ✅ Architecture plan (this document) — **awaiting Go-ahead**
2. Scaffold: monorepo skeleton + :core compiles + CI green (empty APKs build in Actions)
3. Firebase project + config + security rules deployed + anonymous auth verified
4. Uncle-app: setup wizard → foreground service → Firestore writes (debug APK via CI)
5. Watcher-app: sign-in → pair → live map → history
6. Functions: SOS/stale/battery/geofence alerts + 30-day cleanup
7. Hardening: OEM battery guides, offline outbox drills, EN/HI strings, Auditor gate
8. Deliverables pack: signed release APKs + test checklist executed

## 9. Known risks (so nothing surprises us)

- Firebase anonymous-auth devices survive reinstalls poorly — pairing QR includes the project+code so re-pair is one scan.
- osmdroid tile usage policy: fine at family scale.
- Firestore free tier: 50k reads/day — our read pattern (1 snapshot listener × 2 uncles) uses a few thousand/day worst case. Safe.
- Firestore free tier: on-demand model removes the write-volume concern entirely (a handful of writes/day per uncle).
