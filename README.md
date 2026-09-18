# Rail Focus

Rail Focus is an Android app for high-speed rail journeys: pick your departure city and how long you're willing to travel, and it recommends every railway station you can reach. During the trip it tracks your progress by location and offers a focus timer for a quiet, meditative ride.

## Features

- **Reachable-station recommendations** - graph search (Dijkstra + BFS) over a prebuilt rail network, with realistic journey times from a train speed model (acceleration, cruising, deceleration, plus per-station dwell)
- **Journey tracking** - GPS-based progress tracking with automatic station arrival detection
- **Focus mode** - a foreground-service timer that keeps running while you ride
- **Journey history & statistics** - past trips with charts (Vico)
- **Offline map** - MapLibre GL with free OpenStreetMap tiles, no API key required
- **Light / dark theme**, onboarding flow, and settings stored via DataStore

## Tech Stack

- **UI**: Jetpack Compose, Material 3
- **Architecture**: Clean Architecture (`data` / `domain` / `ui`), unidirectional data flow through use cases
- **DI**: Hilt
- **Persistence**: Room with a prebuilt database (1,477 stations, 4,073 edges, loaded via `createFromAsset()`), DataStore for preferences
- **Async**: Kotlin Coroutines + Flow
- **Location**: Google Play Services Location
- **Maps**: MapLibre GL Native
- **Charts**: Vico
- **Min / Target SDK**: 26 / 37

## Project Structure

```
app/src/main/java/com/hsr/railfocus/
├── data/          # Room, graph algorithms (RailGraph), repositories, location, DataStore
├── domain/        # Models, repository/service interfaces, use cases
├── ui/            # Compose screens by feature (home, focus, history, settings, ...)
├── di/            # Hilt modules
├── service/       # Foreground services (focus timer)
└── util/          # Utilities
```

## Build & Test

```bash
./gradlew build                  # Build the app
./gradlew test                   # Run unit tests (JUnit 4 + MockK)
./gradlew connectedAndroidTest   # Instrumented tests (needs a device/emulator)
./gradlew installDebug           # Install debug APK
```

Database tooling lives in [scripts/](scripts/):

```bash
python scripts/rebuild_db.py     # Regenerate the prebuilt database after schema changes
python scripts/check_edges.py    # Validate edge data integrity
```

See [AGENTS.md](AGENTS.md) for the full development guide, including how to add a screen, modify the graph algorithm, or change the database schema.

## License

To be determined.
