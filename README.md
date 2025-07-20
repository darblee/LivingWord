# LivingWord

LivingWord is a native Android application designed to help users engage with the Bible in a meaningful way. The app allows users to read, save, and categorize Bible verses, as well as get AI-powered insights and explanations.

## Features

*   **Read the Bible:** Browse and read the entire Bible.
*   **Verse of the Day:** Get a daily Bible verse to inspire you.
*   **Save and Organize Verses:** Save your favorite verses and organize them by topic.
*   **AI-Powered Insights:** Get explanations and insights on Bible verses using the Gemini AI SDK.
*   **Text-to-Speech:** Listen to Bible verses read aloud.
*   **Google Drive Backup:** Back up and restore your data using Google Drive.
*   **Search:** Search for verses by description.

## Tech Stack

*   **Language:** [Kotlin](https://kotlinlang.org/)
*   **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with [Material Design 3](https://m3.material.io/)
*   **Architecture:** Clean Architecture with MVVM
*   **Asynchronous Programming:** [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
*   **Database:** [Room](https://developer.android.com/training/data-storage/room)
*   **Data Storage:** [Preferences Datastore](https://developer.android.com/topic/libraries/architecture/datastore)
*   **Navigation:** [Jetpack Navigation for Compose](https://developer.android.com/jetpack/compose/navigation)
*   **AI:** [Google Generative AI SDK](https://ai.google.dev/docs)
*   **APIs:**
    *   [ESV Bible API](https://api.esv.org/)
    *   [Google Drive API](https://developers.google.com/drive)

## Project Structure

The project follows a clean architecture pattern, with the following main packages:

*   **`data`:** Contains the data sources for the application, such as the Room database and the logic for fetching data from the Bible API.
*   **`domain`:** Contains the core business logic of the application, such as use cases and repository interfaces.
*   **`ui`:** Contains all the Jetpack Compose UI components, including screens, view models, and navigation.

## Setup

To set up and run the project, follow these steps:

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

## Build

To build the application, you can use the following Gradle command:

```bash
./gradlew assembleDebug
```

This will create an APK file in the `app/build/outputs/apk/debug` directory.

## Screenshots

*(Add screenshots of the app here to showcase the UI and features.)*

## Contributing

Contributions are welcome! If you have any ideas, suggestions, or bug reports, please open an issue or submit a pull request.

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.