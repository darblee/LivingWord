# LivingWord Project Overview

This document provides a comprehensive overview of the LivingWord Android application, intended to be used as a context for future AI-assisted development.

## 1. Project Summary

LivingWord is a native Android application designed to help users engage with the Bible in a meaningful way. The app allows users to read, save, and categorize Bible verses, as well as get AI-powered insights and explanations.

**Key Features:**

*   **Bible Reading:** Browse and read the entire Bible.
*   **Verse Management:** Save, organize, and search for Bible verses.
*   **AI-Powered Insights:** Get explanations and insights on Bible verses using the Google Generative AI SDK.
*   **Text-to-Speech:** Listen to Bible verses read aloud.
*   **Data Backup:** Back up and restore user data using Google Drive.

## 2. Tech Stack and Architecture

*   **Language:** Kotlin
*   **UI:** Jetpack Compose with Material Design 3
*   **Architecture:** Clean Architecture with MVVM
*   **Asynchronous Programming:** Kotlin Coroutines
*   **Database:** Room
*   **Data Storage:** Preferences Datastore
*   **Navigation:** Jetpack Navigation for Compose
*   **AI:** Google Generative AI SDK
*   **APIs:**
    *   ESV Bible API
    *   Google Drive API

The project follows a single-module structure, with the main application logic contained within the `app` module. The code is organized into three main packages: `data`, `domain`, and `ui`, reflecting the clean architecture pattern.

## 3. Building and Running

### 3.1. Setup

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-username/LivingWord-2.git
    ```
2.  **Open the project in Android Studio.**
3.  **Create a `secrets.properties` file in the `app` directory.**
4.  **Add your API keys to the `secrets.properties` file:**
    ```properties
    GEMINI_API_KEY=YOUR_GEMINI_API_KEY
    ESV_BIBLE_API_KEY=YOUR_ESV_BIBLE_API_KEY
    ```
5.  **Sync the project with Gradle.**
6.  **Run the app on an emulator or a real device.**

### 3.2. Build Commands

*   **Build a debug APK:**
    ```bash
    ./gradlew assembleDebug
    ```
*   **Run unit tests:**
    ```bash
    ./gradlew test
    ```
*   **Run instrumented tests:**
    ```bash
    ./gradlew connectedAndroidTest
    ```

## 4. Development Conventions

*   **UI:** The UI is built entirely with Jetpack Compose, following Material Design 3 guidelines.
*   **State Management:** The app uses ViewModels to manage the state of the UI, and Kotlin Coroutines for asynchronous operations.
*   **Navigation:** Navigation is handled by Jetpack Navigation for Compose, with a sealed class `Screen` defining the different destinations.
*   **Testing:** The project includes both unit and instrumented tests, using JUnit, Espresso, and MockK.

## 5. Key Files

*   **`app/src/main/java/com/darblee/livingword/MainActivity.kt`:** The main entry point of the application.
*   **`app/src/main/java/com/darblee/livingword/NavGraph.kt`:** Defines the navigation structure of the application.
*   **`app/build.gradle.kts`:** The build script for the `app` module, containing all the dependencies.
*   **`README.md`:** The main documentation for the project.
