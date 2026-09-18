# Implementation Plan - Final Project Cleanup

The goal is to resolve all remaining IDE warnings, remove redundant files, and ensure a completely clean codebase.

## User Review Required

- **Redundant File Removal**: I will remove `domain/service/LocationManager.kt` as its functionality is duplicated and unused, and `TrackJourneyProgressUseCase.kt` which is currently not being used by any ViewModel.
- **Unused Entity Removal**: I will remove `VisitRecordEntity.kt` as it is unused and superseded by `VisitedStationRecordEntity.kt`.

## Proposed Changes

### Data Layer
- **[DELETE] [VisitRecordEntity.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/data/local/entity/VisitRecordEntity.kt)**: Unused.
- **[EntityMappers.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/data/local/entity/EntityMappers.kt)**: Add missing trailing comma.
- **[LocationManager.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/data/location/LocationManager.kt)**: Remove unused import and function, add parentheses.
- **[UserPreferencesRepository.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/data/preferences/UserPreferencesRepository.kt)**: Add parentheses and trailing comma.
- **[JourneyRepository.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/data/repository/JourneyRepository.kt)**: Remove unused imports and functions, optimize collection processing.
- **[FocusTypeRepository.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/data/repository/FocusTypeRepository.kt)**: Add trailing comma.
- **[PermissionRepositoryImpl.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/data/repository/PermissionRepositoryImpl.kt)**: Add trailing comma.
- **[RecommendationRepository.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/data/repository/RecommendationRepository.kt)**: Add parentheses and trailing comma.

### Domain Layer
- **[DELETE] [LocationManager.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/domain/service/LocationManager.kt)**: Redundant.
- **[DELETE] [TrackJourneyProgressUseCase.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/domain/usecase/TrackJourneyProgressUseCase.kt)**: Unused.
- **[JourneyProgressTracker.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/domain/model/journey/JourneyProgressTracker.kt)**: Add parentheses and trailing comma.
- **[StationArrivalDetector.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/domain/model/journey/StationArrivalDetector.kt)**: Add parentheses.
- **[TrainSpeedModel.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/domain/model/journey/TrainSpeedModel.kt)**: Remove unused import and code, add parentheses and trailing commas.
- **[Station.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/domain/model/Station.kt)**: Add trailing comma.
- **[JourneyRecord.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/domain/model/JourneyRecord.kt)**: Add trailing comma.
- **[Recommendation.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/domain/model/Recommendation.kt)**: Add trailing comma.
- **[PermissionState.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/domain/model/PermissionState.kt)**: Add trailing comma.
- **Usecases**: Add missing trailing commas in `PauseJourneyUseCase`, `CancelJourneyUseCase`, `CheckPermissionsUseCase`, `CompleteJourneyUseCase`, `FindReachableStationsUseCase`, `RequestPermissionUseCase`, `ResumeJourneyUseCase`, and `ScoreRecommendationsUseCase`.

### UI Layer
- **[HomeScreen.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/ui/home/HomeScreen.kt)**: Remove unused code, update `hiltViewModel` import, and remove redundant qualifiers.
- **[FocusSessionViewModel.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/ui/focus/FocusSessionViewModel.kt)**: Remove redundant qualifier and add trailing comma.
- **[HistoryViewModel.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/ui/history/HistoryViewModel.kt)**: Add parentheses and optimize collection processing.
- **[TimeSelectionViewModel.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/ui/timeselection/TimeSelectionViewModel.kt)**: Update `delay` to use `Duration` and add trailing comma.
- **[SettingsViewModel.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/ui/settings/SettingsViewModel.kt)**: Add trailing comma.
- **[FocusTimerService.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/service/FocusTimerService.kt)**: Add trailing comma.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure no syntax errors.
- Run `./gradlew app:compileDebugKotlin` to verify zero warnings.

### Manual Verification
- Deploy to device and verify core app flows are still working correctly.
