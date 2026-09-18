# Implementation Plan - Improve Time Picker Precision

The goal is to make it easier for users to select precise minute values (e.g., 21 min) in the `TimeDurationPicker`.

## Analysis of the Issue

The current `TimeDurationPicker` uses a `HorizontalPager` to simulate a physical ruler/scale. The difficulty in precision comes from:
1.  **Tight Tick Spacing**: 14dp is quite narrow. A small movement of the thumb spans multiple minutes.
2.  **Snap Sensitivity**: The current `snapPositionalThreshold` is 0.5f, which means it requires a significant offset to commit to the next minute, but since the items are narrow, it feels jumpy.
3.  **Visual Feedback**: While there is haptic feedback, the physical "hit area" for a single minute is small.

## Proposed Changes

### UI Component

#### [TimeDurationPicker.kt](file:///E:/work/Android Project/RailFocus/app/src/main/java/com/hsr/railfocus/ui/timeselection/components/TimeDurationPicker.kt)

- **Increase `tickSpacing`**: From `14.dp` to `20.dp`. This makes each minute "wider" on the screen, providing more physical space for precision.
- **Tweak Snap Behavior**: Reduce `snapPositionalThreshold` to `0.3f`. This makes the "snap" happen earlier, making it feel more responsive to intentional micro-adjustments.
- **Optimize Performance**: Reduce `beyondViewportPageCount` from `10` to `5` since ticks are now wider and fewer are needed off-screen.

```kotlin
    val tickSpacing = 20.dp // INCREASED from 14.dp
    // ...
    val snapFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapPositionalThreshold = 0.3f, // REDUCED from 0.5f
    )
    // ...
    HorizontalPager(
        // ...
        beyondViewportPageCount = 5, // REDUCED from 10
        // ...
    )
```

## Verification Plan

### Manual Verification
- **Precision Test**: Open the journey selection. Try to slide from 20 to 21. Verify if it is physically easier to "land" on 21 without jumping over to 22.
- **Fling Test**: Perform a quick swipe. Verify the "momentum" still feels natural and it snaps correctly to a whole minute.
- **Haptic Test**: Ensure the "click" feeling still matches the visual scale.
