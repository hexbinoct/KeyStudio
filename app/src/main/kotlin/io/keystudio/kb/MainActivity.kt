package io.keystudio.kb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import io.keystudio.core.store.ProfileStore
import io.keystudio.kb.ui.HomeScreen
import io.keystudio.kb.ui.StudioTheme
import io.keystudio.kb.ui.TypingScreen
import io.keystudio.kb.ui.editor.EditorScreen
import io.keystudio.kb.ui.layout.LayoutEditorScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge to edge, with bar icons that follow the light/dark page. Also
        // required for WindowInsets.ime to report a real height; the Studio
        // uses it to stand down its preview while the real keyboard is up.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(0x00000000, 0x00000000),
            navigationBarStyle = SystemBarStyle.auto(0x00000000, 0x00000000),
        )
        super.onCreate(savedInstanceState)
        setContent {
            StudioTheme {
                Studio()
            }
        }
    }
}

/**
 * Four screens is not worth a navigation graph, but it is worth surviving a
 * rotation: the route is a saveable string, so you stay where you were rather
 * than being thrown back to the menu mid-design.
 *
 * Routes: `home`, `style:<profileId>`, `layout:<layoutId>`, `typing`.
 */
@Composable
private fun Studio() {
    val context = LocalContext.current
    val store = remember { ProfileStore(context) }
    var route by rememberSaveable { mutableStateOf("home") }

    AnimatedContent(
        targetState = route,
        transitionSpec = {
            val forward = initialState == "home"
            (slideInHorizontally(tween(260)) { if (forward) it / 6 else -it / 6 } + fadeIn(tween(220)))
                .togetherWith(fadeOut(tween(120)))
        },
        label = "route",
    ) { current ->
        when {
            current.startsWith("style:") -> EditorScreen(
                profileId = current.removePrefix("style:"),
                store = store,
                onClose = { route = "home" },
            )
            current.startsWith("layout:") -> LayoutEditorScreen(
                layoutId = current.removePrefix("layout:"),
                store = store,
                onClose = { route = "home" },
            )
            current == "typing" -> TypingScreen(store = store, onBack = { route = "home" })
            else -> HomeScreen(
                store = store,
                onStyle = { route = "style:$it" },
                onLayout = { route = "layout:$it" },
                onTyping = { route = "typing" },
            )
        }
    }
}
