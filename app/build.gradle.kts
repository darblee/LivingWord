import java.text.SimpleDateFormat
import java.time.Instant

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.darblee.livingword"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.darblee.livingword"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Generate the Build Time.
            // In the app, you can retrieve build time info.
            // e.g.     Log.i(TAG, "Build time is " + BuildConfig.BUILD_TIME)
            //          Log.i(TAG, "Build time is " + getString(R.string.build_time))
            val instant = Instant.now()
            val sdf = SimpleDateFormat("MM/dd/yyyy, HH:mm:ss")
            val buildTime = sdf.format(instant.epochSecond * 1000L)
            buildConfigField("String", "BUILD_TIME", "\"${buildTime}\"")
            resValue("string", "build_time", "\"${buildTime}\"")
            signingConfig = signingConfigs.getByName("debug")
        }

        debug {

            // Generate the Build Time.
            // In the app, you can retrieve build time info.
            // e.g.     Log.i(TAG, "Build time is " + BuildConfig.BUILD_TIME)
            //          Log.i(TAG, "Build time is " + getString(R.string.build_time))
            val instant = Instant.now()
            val sdf = SimpleDateFormat("MM/dd/yyyy, HH:mm:ss")
            val buildTime = sdf.format(instant.epochSecond * 1000L)
            buildConfigField("String", "BUILD_TIME", "\"${buildTime}\"")
            resValue("string", "build_time", "\"${buildTime}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        // Common exclusions for Google API clients to prevent conflicts
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "google/protobuf/*.proto"
            excludes += "com/google/thirdparty/publicsuffix/**"
        }
    }
}

secrets {
    // To add your Maps API key to this project:
    // 1. If the secrets.properties file does not exist, create it in the same folder as the local.properties file.
    // 2. Add this line, where YOUR_API_KEY is your API key:
    //        MAPS_API_KEY=YOUR_API_KEY
    propertiesFileName = "secrets.properties"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.googleid)

    // Core testing dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Compose UI Testing
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Navigation Testing
    androidTestImplementation(libs.androidx.navigation.testing)

    // Coroutines testing
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    // Mockk for mocking in tests
    testImplementation(libs.mockk)
    androidTestImplementation(libs.mockk.android)

    // Ktor client mock for testing
    testImplementation(libs.ktor.client.mock)

    implementation(libs.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.foundation)  // Need to handle combinedClickable. See https://composables.com/foundation/combinedclickable

    // Serialization. This is needed for navigation-compose as well.
    implementation(libs.kotlinx.serialization.json)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Google AI Generative AI SDK (Added)
    implementation(libs.google.ai.generativeai)

    implementation(libs.androidx.material.icons.core) // Use the latest version

    // Preference datastore
    implementation (libs.androidx.datastore.preferences.rxjava2)
    implementation (libs.androidx.datastore.preferences.rxjava3)

    // Room database
    // Room uses KSP (Kotlin Symbol Processing) to generate code that interacts with the database
    // When you define your database schema using Room annotations (like @Entity, @Dao, @Database), the Room processor
    // analyzes these annotations and generates the necessary Kotlin code to implement your database queries, manage
    // transactions, and convert data between your Kotlin objects and database tables.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)

    implementation(libs.play.services.auth)

    // Google Drive API
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    implementation(libs.google.http.client.gson)

    // HTTP client for Ollama Reformed Bible AI
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)

    // implementation(libs.openai.client)
}