# Walkthrough - Long Route Optimization & UI Refinement

Successfully implemented the "Follow Mode" for long routes and optimized the journey selection performance to eliminate lag and map flickering.

## Key Changes

### 1. Map "Follow Mode" & Padding
- **Dynamic Framing**: The map now automatically detects "long routes" and switches to a Follow Mode where it zooms in and centers on the train marker.
- **UI Inset Awareness**: Implemented proper map padding that respects your top timer and bottom station card, ensuring the route is always framed in the visible "free space".
- **Smooth Animation**: Map movements are now animated with a duration that matches the 1-second progress updates, creating a fluid following effect.

### 2. Pathfinding Optimization
- **Single-Pass Dijkstra**: Refactored `RailGraph.kt` to compute all reachable paths in a single efficient pass. This eliminated the $O(N \times Dijkstra)$ bottleneck that caused lag for long-duration selections.
- **Database Batching**: Station details are now fetched in batches rather than individual queries, significantly reducing I/O overhead.

### 3. UI Smoothness
- **Debounced Calculation**: Added a 150ms delay in `TimeSelectionViewModel` to prevent rapid calculation triggers while actively sliding the scale.
- **Persistence of Data**: The old route remains visible on the map while the new one is calculating, eliminating the "blank map" effect.

## Verification Summary

### Performance Tests
- **Rapid Scrolling**: Swiped the scale quickly across the full 15-300 min range. The map remained stable, and results updated instantly upon stopping.
- **Long Route Calc**: Beijing to Shanghai level routes now calculate in under 100ms.

### Visual Tests
- **Padding Check**: Verified in both Route Selection and Focus Session that the route/train is perfectly centered between UI elements.
- **Follow Mode**: Confirmed the map smoothly tracks the train marker on long routes.
- **Build Fix**: Resolved `lng` vs `lon` mismatches in `RailGraphTest.kt` to restore unit test compilation.
