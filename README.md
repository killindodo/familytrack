# FamilyTrack

Two Android apps (Kotlin + Jetpack Compose, Material 3) in one monorepo, sharing a Firebase backend.

- **uncle-app** — one-time setup wizard, then a status screen with a big SOS button. Reports location **on demand** when the family checks in.
- **watcher-app** — live map (OpenStreetMap/osmdroid), check-in button per uncle, history trails, FCM alerts.

See `docs/architecture.md` for the full plan. APKs are built by GitHub Actions on every push.

## Modules
| Module | Purpose |
|---|---|
| `core` | Shared data models (pure Kotlin, no Android) |
| `uncle-app` | Uncle device app |
| `watcher-app` | Watcher device app |

## Build
Debug APKs are produced automatically by `.github/workflows/android.yml` (Actions tab → latest run → `debug-apks` artifact).
