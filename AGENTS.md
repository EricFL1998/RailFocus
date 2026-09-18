# Repository Guidelines

## Project Structure & Module Organization

Rail Focus is a single-module Android app (Kotlin, Jetpack Compose) that recommends reachable railway stations and tracks journeys with a focus timer. All app code lives under [app/src/main/java/com/hsr/railfocus/](E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/), organized by Clean Architecture layers:

- `data/` — Room database, graph algorithms (`data/graph/RailGraph.kt`), repositories, location services
- `domain/` — models, repository/service interfaces, use cases
- `ui/` — Compose screens grouped by feature (`focus/`, `home/`, `history/`, `settings/`, `navigation/`, `theme/`)
- `di/` — Hilt modules; `service/` — foreground services; `util/` — utilities

Unit tests are in `app/src/test/java/`, instrumented tests in `app/src/androidTest/java/`. The prebuilt Room database is `app/src/main/assets/databases/rail_focus.db`; Python data tools are in [scripts/](E:/work/Android Project/RailFocus/scripts/).

## Build, Test, and Development Commands

```bash
./gradlew build                  # Build the app
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Instrumented tests (needs device/emulator)
./gradlew test --tests "com.hsr.railfocus.data.graph.RailGraphTest"  # Single test class
./gradlew installDebug           # Install debug APK
./gradlew clean                  # Clean build
python scripts/rebuild_db.py     # Regenerate the prebuilt database after schema changes
python scripts/check_edges.py    # Validate edge data integrity
```

## Coding Style & Naming Conventions

Kotlin with the official code style (set in `gradle.properties`: `kotlin.code.style=official`), 4-space indentation, Java 17 target. Follow existing patterns: classes in `PascalCase` files, Compose screens as `<ScreenName>Screen.kt` with a matching `<ScreenName>ViewModel.kt` annotated `@HiltViewModel`. Keep layer separation — UI talks to ViewModels, ViewModels to use cases, use cases to repositories. New screens require a route in `ui/navigation/RailFocusNavGraph.kt`.

## Testing Guidelines

Tests use JUnit 4, MockK for mocking, and `kotlinx-coroutines-test`. Name tests after the class under test with a `Test` suffix (e.g., `TrainSpeedModelTest.kt`). Graph algorithms, repositories, and use cases are the highest testing priority. Run everything with `./gradlew test`.

## Database Changes

When modifying Room entities: update the entity, the domain model, the mapper in `EntityMappers.kt`, increment the version in `RailDatabase.kt` with a migration, then regenerate the database via `python scripts/rebuild_db.py`.

## Commit & Pull Request Guidelines

There is no Git history in this workspace to derive conventions from; use short, imperative commit messages describing the change (e.g., "Add journey progress tracking"). Pull requests should describe the change, reference the related issue, and include screenshots for any UI changes, plus notes on how the change was tested.
