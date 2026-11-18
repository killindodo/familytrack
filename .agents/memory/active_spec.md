# Active Project Living Specification — FamilyTrack v2 (SMS Direct, Zero Cloud)

> [!IMPORTANT]
> Subagents must adhere strictly to this document. Updated 2026-11-18 after owner decision:
> **owner removed the server/cloud backend entirely. Firebase is OUT of scope. The two phones talk by invisible SMS.**

## 1. Project Overview & Plain English Goal
- **What is being built:** FamilyTrack — two native Android apps (Kotlin, Jetpack Compose, Material 3) in one monorepo, **no backend, no accounts, no cloud**:
  - `uncle-app` (installed on two elderly uncles' phones)
  - `watcher-app` (owner's phone only)
- **Primary user:** Owner (watcher) who checks where each uncle is **on demand**; the uncles, who have agreed and see an honest permanent notification.
- **Transport:** direct device-to-device **SMS** between the paired phone numbers. No internet required on either phone for location sharing. GPS works offline.
- **Target environment:** Android 8.0+ (minSdk 26), target 35. Sideloaded APKs only (no Play Store — fine, since SMS permissions are Play-restricted anyway).

## 2. Agreed User Experience (Visual & Functional)

### Uncle app
- One-time wizard: Welcome (large text) → name + pair via QR/6-digit code (QR is shown on the watcher's screen; contains version + 6-digit code + watcher phone number) → permissions stairway: fine location, background location ("Allow all the time"), SMS (send + receive), notifications, battery-optimization exemption with OEM guidance for Xiaomi/Oppo/Vivo/Samsung/Realme → Done.
- After setup: status screen only — big green "Sharing is ON" tick, uncle's name, one big red SOS button.
- Permanent foreground notification (honest wording): "FamilyTrack is ready — shares location when family checks in".
- Big warning on status screen if location or mobile signal is off.
- No settings/menus; exiting the app must not stop the service.

### Watcher app
- Add uncle (QR + 6-digit code shown on screen; N uncles supported, each = phone number + name + color).
- Home: map (OpenStreetMap via osmdroid) with a colored marker + label per uncle; bottom card: name, "last seen", battery %, speed, source of last fix; "Check location" button; "Navigate" → Google Maps directions.
- Tap uncle → history trail for today; last 7 days via date picker. History stored locally on the watcher phone (Room), auto-deleted after 30 days.

## 3. SMS Protocol (the whole system, in one table)
Format: pipe-delimited, version-tagged: `FT1|<TYPE>|<fields…>`. Compact to stay in 1 SMS part.

| Type | Direction | Fields | Meaning |
|---|---|---|---|
| `PAIR` | uncle→watcher | code, uncleName | "I scanned your QR / typed your code" → completes pairing |
| `CHECK` | watcher→uncle | msgId, ts | "Family wants to see where you are" |
| `FIX` | uncle→watcher | msgId, lat, lng, acc, spd, bat, ts | Answer to a CHECK (or follow-up after SOS) |
| `NOFIX` | uncle→watcher | msgId, reason(GPS_OFF\|NO_FIX\|LOC_OFF), bat, ts | Phone alive but no fix available |
| `SOS` | uncle→watcher | lat?, lng?, acc?, bat, ts | Big red button. If no fix yet: send immediately without coords, then follow up with `FIX` |
| `LOWBAT` | uncle→watcher | bat, ts | One-shot alert when battery < 15% (re-arms at > 25%) |
| `GEOF` | watcher→uncle | geofId, lat, lng, radiusM, label | Set/update a home circle on the uncle phone (registered with Android GeofencingClient) |
| `GEOFACK` | uncle→watcher | geofId, bat, ts | "Circle saved" |
| `GEFEVT` | uncle→watcher | geofId, IN\|OUT, lat, lng, bat, ts | Entered/left the circle (debounced 2 consecutive readings) |

- Watcher identifies the uncle by the **sender phone number** (stored at pairing).
- Received SMS also lands in the normal Messages app → SOS is never lost even if the watcher app is dead.

## 4. Data Flows
- **Check-in:** watcher taps "Check location" → sends `CHECK` → uncle app (foreground service listener) takes ONE high-accuracy GPS fix → replies `FIX` (or `NOFIX`) → watcher map pin + history row (Room) + cancels the 5-min alarm for that msgId.
- **No-answer:** on sending `CHECK`, watcher schedules an exact alarm (+5 min, AlarmManager setExactAndAllowWhileIdle). Fires → no `FIX` received → local notification "⚠️ <name> did not respond" (covers phone off, no signal, dead battery, killed app).
- **SOS:** uncle app gets fast fix → sends `SOS` (immediately, even without coords) → watcher shows high-priority local notification + map pin + history row. If send fails (no signal) → Room outbox, WorkManager retries until sent.
- **Low battery:** uncle side battery receiver → `LOWBAT` once per drop.
- **Geofence:** watcher draws circle on map → `GEOF` SMS → uncle registers with Android's GeofencingClient (OS-level, battery-light, works offline) → enter/exit → `GEFEVT` → watcher notification + pin. Also evaluated on every `FIX` as fallback.
- **History cleanup:** WorkManager periodic on watcher deletes `fixes` older than 30 days.

## 5. Storage (all local, no cloud)
- `uncle-app` (Room): pairing record (watcher number, code, name), geofences, SMS outbox.
- `watcher-app` (Room): uncles (name, number, color, pairedAt), fixes (uncleId, lat, lng, acc, spd, bat, receivedAt, source: CHECK|SOS|GEOF|LOWBAT).
- **No secrets, no keys, no tokens anywhere.**

## 6. Scope Boundaries
- **In-Scope:** pairing (QR/code), on-demand SMS location fetch, SOS, no-answer timer, low-battery alert, geofence via OS GeofencingClient, 7-day history on watcher phone, offline outbox, boot/kill resilience, EN + Hindi strings, debug + signed release via Gradle, CI-built APKs.
- **Out-of-Scope:** ANY cloud service (Firebase/Supabase/ntfy all removed), continuous background location streaming, Play Store, Google Maps API (only as an intent target for directions), uncle-facing settings/menus.

## 7. Edge Cases & Safety Rules
- Uncle phone off/no signal → no reply → watcher's 5-min "did not respond" notification (the alarm working as intended).
- SMS send failure → outbox + WorkManager retry (exponential backoff).
- `NOFIX` counts as an answer (phone alive) but shows "phone alive, no GPS" on the card.
- Geofence GPS-drift guard: 2 consecutive readings to flip state.
- Duplicate `CHECK` while one is being answered: answer the newest, mark older answered.
- If watcher app is dead: SOS still arrives as a plain SMS in Messages app.

## 8. Verification & Acceptance Criteria
- [ ] Check-in → fresh pin ≤ 60 s with mobile signal on both phones (no internet needed)
- [ ] SOS → watcher notification ≤ 30 s; also visible in Messages app
- [ ] No-answer notification at +5 min when uncle phone in airplane mode
- [ ] Reboot / swipe-kill / signal loss-recovery / battery saver / 1-hour standby: service alive, one check-in succeeds
- [ ] Both APKs: debug + signed release built by GitHub Actions
- [ ] SMS protocol parser unit tests green in CI (`:core`)
- [ ] Auditor Approval Certificate Signed
