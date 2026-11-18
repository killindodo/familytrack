# FamilyTrack

Two Android apps (Kotlin + Jetpack Compose, Material 3) in one monorepo. **No backend, no accounts, no cloud** — the two phones talk by invisible SMS between paired numbers.

- **uncle-app** — one-time setup wizard, then a status screen with a big SOS button. Reports location **on demand** when the family checks in (SMS in → one GPS fix → SMS out; GPS works offline).
- **watcher-app** — map (OpenStreetMap/osmdroid), check-in button per uncle, history trails saved locally, "did not respond" + low-battery + home-circle alerts as local notifications.

See `docs/architecture.md` for the full plan. APKs are built by GitHub Actions on every push.

## Modules
| Module | Purpose |
|---|---|
| `core` | Shared data models (pure Kotlin, no Android) |
| `uncle-app` | Uncle device app |
| `watcher-app` | Watcher device app |

## Build
Debug APKs are produced automatically by `.github/workflows/android.yml` (Actions tab → latest run → `debug-apks` artifact).
