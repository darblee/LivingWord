package com.darblee.livingword

import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.darblee.livingword.domain.model.BibleVerseViewModel
import com.darblee.livingword.ui.screens.AddVerseByDescriptionScreen
import com.darblee.livingword.ui.screens.AddVerseByDescriptionViewModel
import com.darblee.livingword.ui.screens.AllVersesScreen
import com.darblee.livingword.ui.screens.GetBookScreen
import com.darblee.livingword.ui.screens.GetChapterScreen
import com.darblee.livingword.ui.screens.GetEndVerseNumberScreen
import com.darblee.livingword.ui.screens.GetStartVerseNumberScreen
import com.darblee.livingword.ui.screens.GoogleDriveOpsScreen
import com.darblee.livingword.ui.screens.HomeScreen
import com.darblee.livingword.ui.screens.MemorizeScreen
import com.darblee.livingword.ui.screens.TopicScreen
import com.darblee.livingword.ui.screens.TopicSelectionScreen
import com.darblee.livingword.ui.screens.VerseDetailScreen
import com.darblee.livingword.ui.theme.ColorThemeOption
import kotlinx.serialization.Serializable

@Serializable // Mark the sealed class as Serializable
sealed class Screen {
    @Serializable // Mark each destination object/class
    data object Home : Screen() // Use data object for screens without arguments

    @Serializable
    data object AllVersesScreen : Screen()

    @Serializable
    data object NewVerseScreen : Screen()

    @Serializable
    data object GetBookScreen : Screen()

    @Serializable
    data object AddVerseByDescriptionScreen : Screen()

    @Serializable
    data object TopicScreen : Screen()

    @Serializable
    data class TopicSelectionScreen(val selectedTopicsJson: String? = null) : Screen()

    /**
     * Screen for selecting the chapter within a specific book.
     * Uses 'data class' because it requires the 'book' name as an argument.
     * @param book The name of the book selected in the previous screen.
     */
    @Serializable
    data class GetChapterScreen(val book: String) : Screen()

    /**
     * Screen for selecting the starting verse number within a specific book and chapter.
     * Uses 'data class' as it requires 'book' and 'chapter' arguments.
     * @param book The name of the selected book.
     * @param chapter The selected chapter number.
     */
    @Serializable
    data class GetStartVerseNumberScreen(val book: String, val chapter: Int) : Screen()

    /**
     * Screen for selecting the ending verse number within a specific book and chapter.
     * Uses 'data class' as it requires 'book' and 'chapter' arguments.
     * @param book The name of the selected book.
     * @param chapter The selected chapter number.
     * @param startVerse The selected starting verse number.
     *
     */
    @Serializable
    data class GetEndVerseNumberScreen(val book: String, val chapter: Int, val startVerse: Int) : Screen()

    /**
     * Screen for showing details for Verse Item. It also provide ability to edit it
     * Uses 'data class' as it requires 'Verse Identifier' argument.
     * @param verseID The id of the BibleVerse entity in "BibleVerse_Item" Database
     *
     */
    @Serializable
    data class VerseDetailScreen(val verseID: Long, val editMode: Boolean) : Screen()

    @Serializable
    data class MemorizeScreen(val verseID: Long) : Screen()  // New screen

    @Serializable
    data object GoogleDriveOpsScreen : Screen()
}


@Composable
fun SetUpNavGraph(
    bibleViewModel: BibleVerseViewModel,
    navController: NavHostController,
    onColorThemeUpdated: (ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption,
) {
    // NavHost defines the navigation graph
    NavHost(
        navController = navController,
        startDestination = Screen.Home // The first screen to show (using the sealed class object)
    ) {
        // Define the Home screen destination using the Screen type
        composable<Screen.Home> { // Use composable<Type> for type safety
            HomeScreen(navController = navController,
                onColorThemeUpdated = onColorThemeUpdated,
                currentTheme = currentTheme)
        }
        // Define the Topic Screen destination
        composable<Screen.AllVersesScreen> {
            AllVersesScreen(navController = navController,
                bibleViewModel = bibleViewModel,
                onColorThemeUpdated = onColorThemeUpdated,
                currentTheme = currentTheme
            )
        }
        // Define the Prayer Screen destination.
        composable<Screen.TopicScreen> {
            TopicScreen(navController = navController, bibleViewModel = bibleViewModel,
                onColorThemeUpdated = onColorThemeUpdated,
                currentTheme = currentTheme)
        }

        composable<Screen.GetBookScreen> {
            GetBookScreen(navController = navController)
        }

        composable<Screen.AddVerseByDescriptionScreen> {
            val viewModel: AddVerseByDescriptionViewModel = viewModel()
            AddVerseByDescriptionScreen(
                navController = navController,
                viewModel = viewModel,
                onColorThemeUpdated = onColorThemeUpdated,
                currentTheme = currentTheme
            )
        }

        composable<Screen.GetChapterScreen> { backStackEntry ->
            val screenRouteParams = backStackEntry.toRoute<Screen.GetChapterScreen>()
            GetChapterScreen(navController = navController, book = screenRouteParams.book)
        }

        composable<Screen.GetStartVerseNumberScreen> { backStackEntry ->
            val screenRouteParams = backStackEntry.toRoute<Screen.GetStartVerseNumberScreen>()
            GetStartVerseNumberScreen(navController = navController,
                book = screenRouteParams.book,
                chapter =  screenRouteParams.chapter)
        }

        composable<Screen.GetEndVerseNumberScreen> { backStackEntry ->
            val screenRouteParams = backStackEntry.toRoute<Screen.GetEndVerseNumberScreen>()
            GetEndVerseNumberScreen(navController = navController,
                book = screenRouteParams.book,
                chapter =  screenRouteParams.chapter,
                startVerse = screenRouteParams.startVerse)
        }

        // --- Topic Selection Screen ---
        composable<Screen.TopicSelectionScreen> { backStackEntry ->
            val screenRouteParams = backStackEntry.toRoute<Screen.TopicSelectionScreen>()
            TopicSelectionScreen(
                navController = navController,
                bibleViewModel = bibleViewModel,
                selectedTopicsJson = screenRouteParams.selectedTopicsJson,
                onColorThemeUpdated = onColorThemeUpdated,
                currentTheme = currentTheme
            )
        }

        // --- Verse Detail Screen with editable capability ---
        composable<Screen.VerseDetailScreen> { backStackEntry ->
            val screenRouteParams = backStackEntry.toRoute<Screen.VerseDetailScreen>()
            VerseDetailScreen(navController = navController,
                bibleViewModel = bibleViewModel,
                verseID = screenRouteParams.verseID,
                onColorThemeUpdated = onColorThemeUpdated,
                currentTheme = currentTheme,
                editMode = screenRouteParams.editMode
            )
        }

        composable<Screen.MemorizeScreen> { backStackEntry ->
            val screenRouteParams = backStackEntry.toRoute<Screen.MemorizeScreen>()
            MemorizeScreen(
                navController = navController,
                bibleViewModel = bibleViewModel,
                verseID = screenRouteParams.verseID,
                onColorThemeUpdated = onColorThemeUpdated,
                currentTheme = currentTheme
            )
        }

        composable<Screen.GoogleDriveOpsScreen> { // Use composable<Type> for type safety
            GoogleDriveOpsScreen(navController = navController,
                onColorThemeUpdated = onColorThemeUpdated,
                currentTheme = currentTheme)
        }
    }

    ObserveNavigationStack(navController = navController)
}

const val TAG = "NavigationStack"

@Composable
fun ObserveNavigationStack(navController: NavHostController) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(navController, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            navController.currentBackStackEntryFlow.collect { entry ->
                // Log the entire back stack
                val backStack = navController.currentBackStack.value
                Log.d(TAG, "Current Back Stack:")
                backStack.forEach {
                    Log.d(TAG, "  Entry: ${it.destination.route}")
                }
            }
        }
    }
}


/**
 * BackPressHandler is used to intercept back press
 *
 * When doing back press on main screen, need to confirm with the user whether
 * it should exit the app or not. It uses the [BackPressHandler] function.
 *
 * We created [OnBackPressedCallback] and add it to the onBackPressDispatcher
 * that controls dispatching system back presses. We enable the callback whenever
 * our Composable is recomposed, which disables other internal callbacks responsible
 * for back press handling. The callback is added on any lifecycle owner change and removed
 * on dispose.
 */
@Composable
fun BackPressHandler(
    backPressedDispatcher: OnBackPressedDispatcher? =
        LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher,
    onBackPressed: () -> Unit
) {
    val currentOnBackPressed by rememberUpdatedState(newValue = onBackPressed)

    val backCallback = remember {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                currentOnBackPressed()
            }
        }
    }

    DisposableEffect(key1 = backPressedDispatcher) {
        backPressedDispatcher?.addCallback(backCallback)

        onDispose {
            backCallback.remove()
        }
    }
}
