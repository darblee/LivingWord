# Navigation Bounce-Back Debug Guide

## Issue Description
Intermittent bug where clicking "Verses" from VerseDetailScreen briefly shows AllVersesScreen before bouncing back to VerseDetailScreen.

## Log Tags to Monitor
When the bug occurs, filter logcat for these tags to trace the issue:

### Key Log Tags:
- `AppScaffold` - Bottom navigation clicks and navigation commands
- `AllVersesScreen` - Screen initialization and LaunchedEffect triggers  
- `NewVerseViewModel` - State changes and resets
- `NavigationStack` - Complete navigation flow and back stack changes

## Expected Normal Flow (No Bug):
1. `AppScaffold`: "=== BOTTOM NAV VERSES CLICKED ===" with VerseDetailScreen as current
2. `AppScaffold`: "Navigation command issued to AllVersesScreen"
3. `NavigationStack`: Shows navigation to AllVersesScreen
4. `AllVersesScreen`: "=== ALLVERSESSCREEN INITIALIZATION ===" with newVerseJson=null
5. `AllVersesScreen`: "Arrived via bottom navigation - resetting NewVerseViewModel state"
6. `NewVerseViewModel`: "=== RESET NAVIGATION STATE CALLED ===" with newlySavedVerseId=null after reset
7. `AllVersesScreen`: "Navigation conditions not met - staying on AllVersesScreen"

## Bug Pattern to Look For:
1. Normal flow starts (steps 1-6 above)
2. `AllVersesScreen`: "=== NAVIGATION LAUNCHEDEFFECT TRIGGERED ===" with newlySavedVerseId != null
3. `AllVersesScreen`: "!!! BOUNCE-BACK NAVIGATION TRIGGERED !!!"
4. `NavigationStack`: Shows navigation back to VerseDetailScreen

## Key Questions for Log Analysis:
1. **State Reset Timing**: Did `NewVerseViewModel.resetNavigationState()` get called before the navigation LaunchedEffect?
2. **State Persistence**: What was the `newlySavedVerseId` value when AllVersesScreen initialized?
3. **Race Condition**: Are there multiple LaunchedEffects running simultaneously?
4. **Parameter Check**: Was `newVerseJson` null when arriving via bottom navigation?

## Debug Commands:
```bash
# Filter for all navigation-related logs
adb logcat | grep -E "(AppScaffold|AllVersesScreen|NewVerseViewModel|NavigationStack)"

# Focus on the critical bounce-back trigger
adb logcat | grep -E "(BOUNCE-BACK NAVIGATION|RESET NAVIGATION STATE|NAVIGATION LAUNCHEDEFFECT)"
```

## Potential Root Causes:
1. **Race Condition**: Reset happening after LaunchedEffect evaluation
2. **State Leakage**: NewVerseViewModel state not properly cleared
3. **Timing Issue**: Multiple rapid navigation commands
4. **Lifecycle Issue**: LaunchedEffect restarting with stale state