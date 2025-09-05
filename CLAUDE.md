# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

- **Build Debug APK**: `./gradlew assembleDebug`
- **Run Tests**: `./gradlew test` (unit tests) and `./gradlew connectedAndroidTest` (instrumented tests)
- **Clean Build**: `./gradlew clean`
- **Build and Install**: `./gradlew installDebug`

## Architecture Overview

This is a native Android Bible study application built with:

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material Design 3
- **Architecture**: Clean Architecture with MVVM pattern
- **Database**: Room with SQLite
- **Async**: Kotlin Coroutines and Flow
- **Navigation**: Jetpack Navigation Compose with type-safe routing
- **AI Integration**: Google Generative AI SDK (Gemini)
- **External APIs**: ESV Bible API, Google Drive API

## Key Architecture Components

### Data Layer
- **Database**: `AppDatabase` (Room) with entities `BibleVerse`, `Topic`, `CrossRefBibleVerseTopics`
- **Repository**: `BibleVerseRepository` handles all data operations and business logic
- **DAO**: `BibleVerseDao` provides database access with complex queries and transactions
- **Remote Services**: `GeminiAIService`, `ESVBibleLookupService`, `GoogleDriveService`

### UI Layer
- **ViewModels**: Primary is `BibleVerseViewModel` - handles all verse-related operations, topics, AI feedback
- **Screens**: Compose screens for different app functions (Home, VerseDetail, Engage, Topics, etc.)
- **Navigation**: Type-safe navigation with sealed class `Screen` and route parameters
- **Theme**: Dynamic theming support with `ColorThemeOption`

### Key Data Flow
- Room database stores verses with topics, AI feedback, and user memorization data
- Repository pattern provides clean separation between data and UI layers
- StateFlow and Flow used for reactive UI updates
- Coroutines handle all async operations (database, network, AI)

## Important Implementation Details

### Database Schema
- Current schema version 1 includes AI feedback fields: `aiContextExplanationText`, `applicationFeedback`
- Complex many-to-many relationship between verses and topics via `CrossRefBibleVerseTopics`
- Uses `fallbackToDestructiveMigration(true)` for schema changes

### Navigation System
- Uses new type-safe navigation with serializable route objects
- Custom back press handling for main screen exit confirmation
- Navigation stack logging for debugging

### AI Integration
- Gemini AI provides verse explanations, scoring, and feedback
- Cached AI responses to avoid redundant API calls
- Error handling with `AiServiceResult` sealed class

### API Keys Management
- Requires `secrets.properties` file with `GEMINI_API_KEY` and `ESV_BIBLE_API_KEY`
- Uses Gradle secrets plugin to inject keys safely during build

## Testing
- Unit tests in `app/src/test/`
- Instrumented tests in `app/src/androidTest/` including UI tests with Compose Testing
- Uses MockK for mocking, Coroutines Test for async testing

## Development Notes
- App forces portrait orientation
- Requires microphone permission for text-to-speech functionality
- Google Drive integration for backup/restore
- Preference datastore for settings persistence
- Edge-to-edge display support