# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Rail Focus** is a railway journey meditation and tracking Android app that helps users plan high-speed rail trips and track their travel progress. The app uses graph algorithms to find reachable stations within a given time window and provides journey tracking with location-based features.

**Core Concept**: Users select their current city, choose a travel duration, and the app recommends reachable railway stations. During the journey, the app tracks progress and provides a "focus mode" timer for meditation.

**Package**: `com.hsr.railfocus`  
**Min SDK**: 26 (Android 8.0)  
**Target SDK**: 37

## Build Commands

```bash
# Build the app
./gradlew build

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests "com.hsr.railfocus.data.graph.RailGraphTest"

# Clean build
./gradlew clean

# Install debug build
./gradlew installDebug

# Assemble release APK
./gradlew assembleRelease
```

## Database Management

The app uses a **prebuilt Room database** located at `app/src/main/assets/databases/rail_focus.db` containing:
- **1,477 railway stations** across China
- **4,073 edges** (connections between stations with distances)

### Rebuilding the Database

When modifying the database schema or data:

```bash
# Rebuild the prebuilt database (matches Room entity schema)
python scripts/rebuild_db.py

# Query database for debugging
python query_db.py
python query_edges.py
python query_nan.py

# Validate edge data integrity
python scripts/check_edges.py
```

**Important**: After modifying Room entities, increment the database version in `RailDatabase.kt` and add a migration if needed.

## Architecture

Rail Focus follows **Clean Architecture** with clear separation between layers:

```
com.hsr.railfocus/
├── data/                    # Data layer
│   ├── graph/              # Graph algorithms (RailGraph, path finding)
│   ├── local/              # Room database
│   │   ├── entity/         # Room entities
│   │   └── dataaccess/     # DAOs
│   ├── location/           # Location services (GPS tracking)
│   ├── preferences/        # DataStore for user preferences
│   └── repository/         # Repository implementations
├── domain/                  # Business logic layer
│   ├── model/              # Domain models (Station, Journey, etc.)
│   │   └── journey/        # Journey tracking models (progress, speed model)
│   ├── repository/         # Repository interfaces
│   ├── service/            # Service interfaces
│   └── usecase/            # Use cases (FindReachableStations, StartJourney, etc.)
├── di/                      # Dependency injection (Hilt modules)
├── service/                 # Foreground services (FocusTimerService)
├── ui/                      # Presentation layer (Jetpack Compose)
│   ├── components/         # Reusable UI components
│   ├── focus/              # Focus session screen
│   ├── history/            # Journey history screen
│   ├── home/               # Home screen
│   ├── navigation/         # Navigation graph
│   ├── onboarding/         # Onboarding flow
│   ├── settings/           # Settings screen
│   ├── theme/              # Material 3 theme
│   └── timeselection/      # Time selection screen
└── util/                    # Utilities
```

### Key Architectural Components

#### 1. RailGraph Engine
`data/graph/RailGraph.kt` implements the core graph algorithms:
- **Dijkstra's algorithm** for shortest path finding
- **BFS** for reachable station analysis within time constraints
- Uses `TrainSpeedModel` to calculate realistic journey times (acceleration, cruising, deceleration)
- Adds 1-minute dwell time at intermediate stations

#### 2. Journey Tracking System
- `JourneyProgressTracker`: Tracks user's journey progress using GPS
- `StationArrivalDetector`: Detects when user arrives at stations
- `TrainSpeedModel`: Models realistic high-speed rail acceleration/deceleration
- Location-based automatic journey progress updates

#### 3. Dependency Injection
Uses **Hilt** for dependency injection:
- `AppModule`: Provides database and DAOs
- `RepositoryModule`: Provides repository implementations
- All ViewModels, repositories, and use cases are injected

#### 4. Database Strategy
**Prebuilt database approach** for instant startup:
- Database file included in `assets/databases/`
- Room loads it automatically on first launch via `createFromAsset()`
- No seeding delay — app starts instantly with full data
- Current version: 7 (see `RailDatabase.kt` for migration history)

## Cross-Platform Strategy

This Android app is the **MVP phase** of a planned cross-platform project. The architecture is designed with future Kotlin Multiplatform (KMP) migration in mind:

- **Shared logic candidates**: `domain/` package (models, use cases), `data/graph/` (graph algorithms)
- **Platform-specific**: UI (Jetpack Compose), database driver (Room), location services, foreground service
- Consistent color system defined in `ui/theme/RailColors.kt` (meant for cross-platform reuse)

See `rail_focus_platform_strategy.md` for detailed cross-platform design principles.

## Key Technologies

- **UI**: Jetpack Compose with Material 3
- **DI**: Hilt (Dagger)
- **Database**: Room with prebuilt SQLite database
- **Navigation**: Navigation Compose
- **Async**: Kotlin Coroutines + Flow
- **Location**: Google Play Services Location API
- **Maps**: MapLibre GL Native (free OSM tiles)
- **Charts**: Vico (for journey statistics)
- **Serialization**: Gson
- **Storage**: DataStore (for user preferences)

## Testing

- Unit tests in `app/src/test/java/`
- Instrumented tests in `app/src/androidTest/java/`
- Uses **MockK** for mocking in unit tests
- Tests for graph algorithms, repositories, and use cases are high priority

## Development Notes

- **MapLibre**: Uses free OpenStreetMap tiles (no API key required). Initialized in `RailFocusApplication.onCreate()`
- **Foreground Service**: `FocusTimerService` keeps focus timer running in background (uses `FOREGROUND_SERVICE_SPECIAL_USE`)
- **Permissions**: Location (coarse/fine), notifications, foreground service, wake lock
- **Theme**: Supports light/dark mode via user preference stored in DataStore
- **Edge-to-edge**: App uses `enableEdgeToEdge()` with proper inset handling

## Data Files

- `RailDataSet/`: Contains database schema definitions and backup DB files
- `scripts/`: Python utilities for database management and tile generation
- `app/src/main/assets/databases/rail_focus.db`: The prebuilt Room database (3.3 MB)
- `app/src/main/assets/tiles/`: Offline map tiles (if needed)

## Common Development Workflows

### Adding a New Station Data Field
1. Update `StationEntity.kt` in `data/local/entity/`
2. Update `Station.kt` domain model in `domain/model/`
3. Update mapping in `EntityMappers.kt`
4. Increment database version in `RailDatabase.kt` and add migration
5. Modify `rebuild_db.py` script to include the new field
6. Run `python scripts/rebuild_db.py` to regenerate the database

### Adding a New Screen
1. Create screen composable in `ui/<screen_name>/<ScreenName>Screen.kt`
2. Create ViewModel in `ui/<screen_name>/<ScreenName>ViewModel.kt`
3. Add navigation route in `ui/navigation/RailFocusNavGraph.kt`
4. Use Hilt `@HiltViewModel` for ViewModel injection

### Modifying Graph Algorithm
- Core logic is in `data/graph/RailGraph.kt`
- Use cases wrapping graph operations are in `domain/usecase/`
- Algorithm changes should maintain backward compatibility with existing journey data
- Test with `query_edges.py` to verify edge connectivity
