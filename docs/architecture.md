# FamilyTrack — Architecture Plan v2 (SMS Direct, Zero Cloud)

> Status: APPROVED by owner 2026-11-18 — cloud backend removed, SMS transport approved.
> Spec source: /root/projects/familytrack/.agents/memory/active_spec.md (authoritative)

## 1. Modules (Gradle monorepo, one repo: `familytrack`)

```
familytrack/
├── build.gradle.kts            (root, shared config)
├── settings.gradle.kts
├── gradle/libs.versions.toml   (single version catalog — no version drift)
├── core/                       (Kotlin + thin Android messaging layer, shared by both apps)
│   ├── Models.kt               (LocationFix, UncleStatus, AlertType, FixSource)
│   ├── SmsProtocol.kt          (FT1|… encode/parse + round-trip tests)
│   ├── Time.kt                 ("2 min ago" formatting, EN/HI aware)
│   └── sms/                    (FtReceiver, FtSender, FtBus — one copy for both apps)
├── uncle-app/                  (com.killindodo.familytrack.uncle)
│   ├── ui/        (Compose: Welcome, Pairing(zxing-embedded), Permissions, Done, StatusScreen)
│   ├── service/   (ForegroundService: SMS listener + on-demand GPS + SOS + geofence registration)
│   ├── receiver/  (BootReceiver: restart service; SmsReceiver: routes FT1 messages)
│   └── data/      (Room: pairing, geofences, outbox)
├── watcher-app/                (com.killindodo.familytrack.watcher)
│   ├── ui/        (Compose: AddUncle(QR), HomeMap(osmdroid), UncleDetail, Alerts)
│   ├── map/       (osmdroid + OSM tiles, offline cache)
│   ├── sms/       (send CHECK/GEOF, receive FIX/SOS/…, exact alarm +5 min no-answer)
│   └── data/      (Room: uncles, fixes history, 30-day cleanup)
```

No server module. No Firebase. No secrets of any kind.

## 2. Transport: invisible SMS
- Message format `FT1|TYPE|fields…` (see spec §3 table). Parser in `:core` with unit tests (runs in CI).
- Permissions needed on BOTH phones: `SEND_SMS`, `RECEIVE_SMS` (runtime, granted in wizard — sideload-only app family, so Play restrictions don't apply).
- Uncle identifies watcher by its paired number; watcher identifies uncle by sender number.
- Fallback honesty: received SMS also appears in the normal Messages app — an SOS is never lost even if the watcher app is dead.

## 3. Key flows (one line each)
- **Pairing:** watcher shows QR {v, code, watcherNumber} → uncle scans (zxing-embedded, no Play Services needed) or types → uncle sends `PAIR|code|name` → watcher stores uncle {name, number, color}. Colors: #10B981, #F59E0B, then palette.
- **Check-in:** `CHECK` → ONE high-accuracy fix (FusedLocationProvider.getCurrentLocation) → `FIX` (or `NOFIX`) → watcher: pin + history + cancel +5-min alarm.
- **No-answer:** exact alarm (setExactAndAllowWhileIdle) +5 min after `CHECK`; canceled on `FIX`; fires → local notification "did not respond".
- **SOS:** send immediately with best available fix (even without coords), follow-up `FIX` when a fix arrives; send failures → Room outbox → WorkManager retry.
- **Low battery:** < 15% → `LOWBAT` once (re-arm > 25%).
- **Geofence:** `GEOF` SMS → uncle registers circle with Android GeofencingClient (OS-level, offline, battery-light) → `GEFEVT` (debounce: 2 consecutive readings) → watcher notification + pin. Fallback: distance check on every `FIX`.
- **History:** all received fixes stored in watcher's Room; today + 7-day views; WorkManager deletes > 30 days.
- **Boot/kill resilience:** foreground service (location type) + START_STICKY + BOOT_COMPLETED receiver + WorkManager 15-min watchdog. Honest notification: "FamilyTrack is ready — shares location when family checks in".

## 4. Uncle-app permissions stairway (wizard)
1. Fine location — "So we can see where you are if you need help."
2. Background location ("Allow all the time") — needed because an SMS can arrive while the app is closed.
3. SMS (send + receive) — "This is how your phone talks to family."
4. Notifications (API 33+) — "Shows 'FamilyTrack is ready' — keeps the app working."
5. Battery exemption — ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS + OEM guidance (Xiaomi Autostart/no-restrictions, Oppo/Realme allow-background + lock-in-recents, Vivo i Manager, Samsung unmonitored apps).
- Every step skippable-but-warned; final screen summarizes what's ON vs needs attention.

## 5. Risks (honest list)
- SMS needs mobile signal: absence IS the "did not respond" alarm — working as designed.
- SMS cost: a few paise each; family scale = negligible; often bundled in plans.
- OEM battery managers can kill the listener: mitigations in §3 (FGS + watchdog + wizard guidance).
- GPS fix without internet (airplane data off): GPS hardware works offline; A-GPS assist unavailable → slower first fix; acceptable.
- GeofencingClient behavior varies by OEM; fallback distance-check on each FIX covers it.
- SMS timestamps unreliable across OEMs → we timestamp on receipt (watcher) and use payload ts only for display.

## 6. Build & CI (unchanged, already green)
- GitHub Actions on every push: `:core` tests (incl. SMS parser round-trip) + both debug APKs → `debug-apks` artifact.
- Signed releases in a later phase: keystore via GitHub Secrets (nothing secret in repo).
- This phone (ARM64) can't run the Android build tools — cloud CI is the build factory, free for public repos.

## 7. Phases (each confirmed building before the next)
1. ✅ Architecture v1 plan (superseded by this v2)
2. ✅ Scaffold: monorepo skeleton + :core compiles + CI green
3. 🔄 SMS protocol in `:core` (encode/parse + tests) + messaging layer in both apps (send/receive/route) — pure code, no user steps
4. Uncle-app: wizard → service (SMS listener + GPS-on-demand + SOS + geofence) — debug APK via CI
5. Watcher-app: Add-uncle → map → check-in → no-answer timer → history → cleanup
6. Hardening: OEM battery guides, outbox drills, EN/HI strings, Auditor gate
7. Signed release APKs + test checklist executed
