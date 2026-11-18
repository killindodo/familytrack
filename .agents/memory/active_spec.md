# Active Project Living Specification — FamilyTrack (Single Source of Truth)

> [!IMPORTANT]
> Subagents must adhere strictly to this document. Updated during Gap Elimination with owner.

## 1. Project Overview & Plain English Goal
- **What is being built:** FamilyTrack — two native Android apps (Kotlin, Jetpack Compose, Material 3) in one monorepo sharing a Firebase backend: `uncle-app` (installed on two elderly uncles' phones) and `watcher-app` (owner's phone only).
- **Primary user:** Owner (watcher) who checks where each uncle is **on demand**; the uncles, who have agreed and must see an honest permanent notification.
- **Target environment:** Android 8.0+ (minSdk 26), target latest stable (35). Sideloaded APKs, no Play Store.

## 2. Agreed User Experience (Visual & Functional)

### Uncle app
- One-time wizard: Welcome (large text) → name + pair via QR/6-digit code from watcher → permissions stairway (fine location, "Allow all the time" background, notifications, battery-optimization exemption with OEM guidance for Xiaomi/Oppo/Vivo/Samsung/Realme) → Done.
- After setup, app opens ONLY to a status screen: big green "Sharing is ON" tick (standby state), uncle's name, one big red SOS button.
- **Location is ON-DEMAND (owner decision, 2026-11-18):** the app does NOT continuously stream location. When the watcher taps "Check location", the uncle device receives the request and reports ONE high-accuracy fix.
- Permanent foreground notification: "FamilyTrack is ready — shares location when family checks in" (honest wording; owner approved intent of openness).
- Big warning on status screen if location or internet is off.
- No settings/menus; exiting the app must not stop the service.

### Watcher app
- Sign in → "Add uncle" screen: pairing QR + 6-digit code. Design supports N uncles (2 initially).
- Home: map (OpenStreetMap via osmdroid) with a distinct color + label per uncle; bottom card: name, "last seen", battery %, speed; "Navigate" button → Google Maps directions.
- **"Check location" button per uncle** — sends a check-in request, shows a subtle progress state until the fresh fix arrives.
- Tap uncle → history trail for today; last 7 days via date picker.

## 3. Scope Boundaries
- **In-Scope:** pairing (one-time short-lived code), on-demand location fetch, SOS alerts, alert notifications (SOS / no-answer-to-check-in within 5 min / battery <15% / geofence enter-leave), geofence set on watcher map, 7-day history, offline queueing of the on-demand fix, boot/kill resilience, EN + Hindi strings, debug + signed release via Gradle, CI-built APKs on GitHub Actions.
- **Out-of-Scope (DO NOT TOUCH):** continuous background location streaming (replaced by on-demand), Play Store publishing, Google Maps API, in-app settings for the uncle, any uncle-facing menus.

## 4. Edge Cases & Safety Rules
- Uncle offline when check-in arrives: request stays queued in Firestore; when connectivity returns, uncle answers it (max age 30 min — older requests expire silently).
- No answer within 5 min of a check-in → watcher alert "did not respond" (covers phone off, dead, service killed).
- GPS drift guard on geofence events: 2 consecutive readings to flip state.
- Room outbox for the fix write if network drops mid-report.
- Firestore free-tier safety: on-demand model removes write-volume risk.

## 5. Verification & Acceptance Criteria
- [ ] Check-in request → fresh fix on watcher map ≤ 30 s with internet on both phones
- [ ] SOS → watcher alert ≤ 15 s
- [ ] No-answer alert at +5 min when uncle phone in airplane mode
- [ ] Reboot / swipe-kill / airplane on-off / battery saver / 1-hour standby: service alive, one check-in succeeds
- [ ] Both APKs: debug + signed release built by GitHub Actions
- [ ] Auditor Approval Certificate Signed
