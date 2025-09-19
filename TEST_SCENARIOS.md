# EngageScreen AI Error Handling Test Scenarios

This document outlines the manual test scenarios to validate the improved error handling for the Score/Feedback dialog's "Regenerate" button functionality.

## Test Scenario 1: Regenerate Failure WITH Cached Data

### Setup:
1. Open the app and navigate to an existing verse in EngageScreen
2. Enter some user input (memorized quote and application)
3. Click "Score/Feedback" to generate initial AI response (ensure this succeeds)
4. Close the dialog and simulate a network failure or AI service error

### Test Steps:
1. Open the Score/Feedback dialog (should show previous AI results)
2. Click the "Regenerate" button
3. Simulate AI service failure (network issue, rate limit, etc.)

### Expected Results:
- ✅ Error message displays: "Failed to regenerate AI response: [reason]. Reverted to cached answer."
- ✅ Error is shown in a styled card with "Regenerate Failed" title
- ✅ Previous AI score and feedback content is displayed below the error
- ✅ Additional text shows: "✓ Displaying previous results below"
- ✅ User can still see and interact with their previous AI feedback
- ✅ "Save" button remains functional with cached data
- ✅ **NEW:** When user clicks "OK", error clears and dialog stays open showing cached results
- ✅ **NEW:** User can continue using the dialog with cached data without re-opening

## Test Scenario 2: Regenerate Failure WITHOUT Cached Data

### Setup:
1. Open the app and navigate to EngageScreen with a verse that has no previous AI feedback
2. Enter some user input (memorized quote and application)
3. Ensure AI service is failing (network issue, invalid API key, etc.)

### Test Steps:
1. Click "Score/Feedback" button to open dialog
2. Click "Generate" button (this should fail)
3. Try clicking "Regenerate" button

### Expected Results:
- ✅ Error message displays: "Failed to generate AI response: [reason]. Please try hitting 'Generate' again or check your AI service settings and network connection."
- ✅ Error is shown in a styled card with "AI Error" title
- ✅ No content is displayed below the error (since no cached data exists)
- ✅ User gets clear guidance on what to do next
- ✅ "Save" button is disabled/not functional since no valid AI data exists
- ✅ **NEW:** When user clicks "OK", dialog closes (no cached data to preserve)

## Test Scenario 3: Successful Regenerate

### Setup:
1. Open the app with an existing verse that has previous AI feedback
2. Ensure AI service is working properly

### Test Steps:
1. Open Score/Feedback dialog (shows previous results)
2. Click "Regenerate" button
3. Wait for new AI response

### Expected Results:
- ✅ New AI content replaces previous content
- ✅ No error messages are shown
- ✅ "Save" button is enabled and functional
- ✅ Dialog shows fresh AI feedback

## Technical Implementation Details

### EngageVerseViewModel Changes:
- Added `hasCachedData: Boolean` - tracks if cached AI response exists
- Added `isRegenerateRequest: Boolean` - tracks if current request is from "Regenerate" button
- Added `clearErrorState()` method - clears error without affecting cached data
- Enhanced error handling in `performAICall()`:
  - **Regenerate with cached data:** Reverts to previous AI response and shows revert message
  - **Regenerate without cached data:** Shows retry message with guidance
  - **Regular failures:** Uses standard error handling

### EngageScreen UI Changes:
- **Enhanced error display:** Shows errors in styled Material Design card
- **Contextual error titles:** "Regenerate Failed" vs "AI Error"
- **Smart content display:** Shows both error AND cached content when appropriate
- **Clear user guidance:** Different messages based on data availability
- **Smart dialog behavior:**
  - With cached data: OK button clears error and keeps dialog open
  - Without cached data: OK button closes dialog
  - onDismissRequest (clicking outside) follows same logic as OK button

### Error Message Examples:
- **With cached data:** `"Failed to regenerate AI response: Network connection failed. Reverted to cached answer."`
- **Without cached data:** `"Failed to generate AI response: API rate limit exceeded. Please try hitting 'Generate' again or check your AI service settings and network connection."`

## Manual Testing Instructions

To manually test these scenarios:

1. **Force AI failures** by:
   - Disconnecting internet during regenerate
   - Using invalid API keys in settings
   - Calling regenerate rapidly to hit rate limits

2. **Verify cached data behavior** by:
   - Generating successful AI feedback first
   - Then forcing failures on regenerate
   - Confirming cached data is preserved and displayed

3. **Test UI responsiveness** by:
   - Checking error message styling
   - Verifying button states (enabled/disabled)
   - Confirming content display logic

The implementation provides a robust user experience that preserves user data and provides clear guidance during AI service failures.