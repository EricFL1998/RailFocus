# Implementation Plan - Optimize Scale Calculation and Fix Map Flickering

Address the lag and map blanking when scrolling through long routes on the journey selection scale.

## Proposed Changes

### Domain Logic

#### [RailGraph.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/data/graph/RailGraph.kt)

- **[NEW] `findAllReachablePathsWithinDuration`**: Implement a new method that performs a single Dijkstra pass and returns not just the times, but the complete `PathResult` for all reachable stations. This eliminates the $O(N \times Dijkstra)$ bottleneck where $N$ is the number of reachable stations.

#### [DestinationCalculator.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/domain/service/DestinationCalculator.kt)

- **Refactor `calculateDestinations`**: Update to use the new `findAllReachablePathsWithinDuration` method. This will significantly reduce the time spent calculating routes for long-duration selections where many stations are reachable.

---

### UI Components

#### [TimeSelectionViewModel.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/ui/timeselection/TimeSelectionViewModel.kt)

- **Debounce Calculation**: Add a 150ms delay before starting the calculation in `onDurationSelected`. This prevents rapid calculation triggers while the user is still actively sliding the scale.
- **Smooth State Updates**:
    - Avoid clearing `destinations` and `selectedDestination` immediately when `isCalculating` becomes true. Instead, keep the previous data visible until the new calculation completes.
    - Only update `isCalculating` to true after the debounce delay.
- **Loading State Indicator**: Add a subtle loading state that doesn't clear the map, perhaps a small progress bar or overlay.

---

## Verification Plan

### Manual Verification
1. **Scale Scrolling Test**:
    - Rapidly scroll the duration scale from 15 to 300 minutes.
    - Verify the map does not go blank during the scroll.
    - Verify the destinations update smoothly once the scrolling stops.
2. **Long Duration Test**:
    - Select a 300-minute duration.
    - Verify that even with many reachable stations, the calculation finishes quickly (under 500ms).
3. **Map Stability Test**:
    - Ensure the map doesn't "jump" or flicker between old and new routes during the calculation.
