package io.keystudio.kb.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.keystudio.core.model.BackgroundSpec
import io.keystudio.core.model.KeyboardProfile
import io.keystudio.core.store.ProfileStore
import io.keystudio.core.view.KeyboardView
import io.keystudio.kb.ui.editor.Group
import io.keystudio.kb.ui.editor.NavRow
import io.keystudio.kb.ui.editor.PrimaryButton
import io.keystudio.kb.ui.editor.RowDivider
import io.keystudio.kb.ui.editor.SectionHeader
import io.keystudio.kb.ui.editor.toBrush

/**
 * The Studio's front door, modelled on the Keys Cafe main menu: get set up,
 * pick a keyboard, then choose what to make. The preview at the bottom is the
 * real [KeyboardView], not a mock-up.
 */
@Composable
fun HomeScreen(
    store: ProfileStore,
    onStyle: (String) -> Unit,
    onLayout: (String) -> Unit,
    onTyping: () -> Unit,
) {
    val context = LocalContext.current
    val c = Studio.colors

    var profiles by remember { mutableStateOf(store.listProfiles()) }
    var selected by remember { mutableStateOf(store.activeProfile()) }
    var enabled by remember { mutableStateOf(isImeEnabled(context)) }
    var isDefault by remember { mutableStateOf(isImeDefault(context)) }
    var error by remember { mutableStateOf<String?>(null) }

    // Setup state changes in Settings, outside this activity, so re-check on resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = isImeEnabled(context)
                isDefault = isImeDefault(context)
                profiles = store.listProfiles()
                selected = store.activeProfile()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text == null) {
            error = "That file could not be read."
            return@rememberLauncherForActivityResult
        }
        runCatching { store.importProfile(text) }
            .onSuccess { imported ->
                val saved = imported.copy(id = store.newIdFor(imported.name))
                store.saveProfile(saved)
                profiles = store.listProfiles()
                onStyle(saved.id)
            }
            .onFailure { error = "That does not look like a KeyStudio design." }
    }

    // While the real keyboard is up there is no point drawing a second one: it
    // doubles the GPU work in this process and puts two keyboards on screen.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text("KeyStudio", style = StudioType.display, color = c.text, modifier = Modifier.padding(start = 4.dp))
        Text(
            "Design the keyboard you type on.",
            style = StudioType.body,
            color = c.textSecondary,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )

        Spacer(Modifier.height(20.dp))
        if (enabled && isDefault) {
            ReadyLine(onSwitch = { showPicker(context) })
        } else {
            SetupCard(
                enabled = enabled,
                isDefault = isDefault,
                onEnable = {
                    context.startActivity(
                        Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                onChoose = { showPicker(context) },
            )
        }

        SectionHeader("Keyboards") {
            Text(
                "New",
                style = StudioType.label,
                color = c.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        val copy = selected.copy(
                            id = store.newIdFor("${selected.name} copy"),
                            name = "${selected.name} copy",
                        )
                        store.saveProfile(copy)
                        store.setActiveProfileId(copy.id)
                        profiles = store.listProfiles()
                        selected = copy
                        onStyle(copy.id)
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        ThemeGrid(
            profiles = profiles,
            selectedId = selected.id,
            onSelect = {
                // Tapping the one already in use is how you get into it -- the
                // same gesture that would otherwise do nothing at all.
                if (it.id == selected.id) {
                    onStyle(it.id)
                } else {
                    store.setActiveProfileId(it.id)
                    selected = it
                }
            },
        )

        SectionHeader("Make it yours")
        Group {
            NavRow(
                "Style your own keyboard",
                subtitle = "Colours, shape, effects and sound",
                icon = StudioIcons.Palette,
                onClick = { onStyle(selected.id) },
            )
            RowDivider(50.dp)
            NavRow(
                "Make your own keyboard",
                subtitle = "Keys, sizes and long-press sets",
                icon = StudioIcons.Layout,
                onClick = { onLayout(selected.layoutId) },
            )
            RowDivider(50.dp)
            NavRow(
                "Typing",
                subtitle = "Swipe, suggestions and autocorrect",
                icon = StudioIcons.Keyboard,
                onClick = onTyping,
            )
            RowDivider(50.dp)
            NavRow(
                "Import a design",
                subtitle = "A .json shared from KeyStudio",
                icon = StudioIcons.Import,
                onClick = { importer.launch("*/*") },
            )
        }
        error?.let {
            Text(it, style = StudioType.caption, color = c.danger, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        }

        SectionHeader("Try it")
        TestField()

        if (!imeVisible) {
            Spacer(Modifier.height(16.dp))
            KeyboardPreview(profile = selected, store = store)
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun showPicker(context: Context) {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showInputMethodPicker()
}

/** Once everything is set up, setup shrinks to one line and gets out of the way. */
@Composable
private fun ReadyLine(onSwitch: () -> Unit) {
    val c = Studio.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.fill)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(c.success, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text("KeyStudio is your keyboard", style = StudioType.label, color = c.text, modifier = Modifier.weight(1f))
        Text(
            "Switch",
            style = StudioType.label,
            color = c.textSecondary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSwitch)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SetupCard(
    enabled: Boolean,
    isDefault: Boolean,
    onEnable: () -> Unit,
    onChoose: () -> Unit,
) {
    val c = Studio.colors
    Group {
        Column(Modifier.padding(16.dp)) {
            Text("Two steps to start", style = StudioType.headline, color = c.text)
            Spacer(Modifier.height(12.dp))
            SetupStep(1, "Turn on KeyStudio in system settings", done = enabled)
            Spacer(Modifier.height(10.dp))
            SetupStep(2, "Make it the keyboard you type with", done = isDefault)
            Spacer(Modifier.height(16.dp))
            if (!enabled) {
                PrimaryButton("Open settings", onClick = onEnable, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryButton("Choose KeyStudio", onClick = onChoose, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SetupStep(number: Int, label: String, done: Boolean) {
    val c = Studio.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(22.dp)
                .background(if (done) c.success else c.fill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                StudioIcon(StudioIcons.Check, tint = Color.White, size = 14.dp)
            } else {
                Text("$number", style = StudioType.value, color = c.textSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = StudioType.body,
            color = if (done) c.textSecondary else c.text,
        )
    }
}

@Composable
private fun ThemeGrid(
    profiles: List<KeyboardProfile>,
    selectedId: String,
    onSelect: (KeyboardProfile) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        profiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { profile ->
                    ThemeCard(
                        profile = profile,
                        selected = profile.id == selectedId,
                        onClick = { onSelect(profile) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * A miniature of the real thing: the theme's own backdrop, keys and action
 * key laid out like a keyboard, rather than a swatch that tells you nothing
 * about what typing on it will look like.
 */
@Composable
private fun ThemeCard(
    profile: KeyboardProfile,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Studio.colors
    val theme = profile.themeFor(isSystemInDarkTheme())
    val ring by animateColorAsState(if (selected) c.accent else c.hairline, label = "ring")
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(if (selected) 2.dp else 1.dp, ring, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        MiniKeyboard(theme, Modifier.fillMaxWidth().aspectRatio(1.55f))
        Row(
            Modifier.padding(start = 4.dp, end = 2.dp, top = 9.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                profile.name,
                style = StudioType.label,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Text("Edit", style = StudioType.caption, color = c.accent)
            }
        }
    }
}

@Composable
private fun MiniKeyboard(theme: io.keystudio.core.model.Theme, modifier: Modifier) {
    val backdrop = theme.backdropBrush()
    val key = theme.keyFill.toBrush()
    val mod = theme.modifierFill.toBrush()
    val accent = theme.accentFill.toBrush()
    Canvas(modifier.clip(RoundedCornerShape(9.dp))) {
        drawRect(backdrop)
        val pad = size.width * 0.035f
        val gap = size.width * 0.012f
        val rows = 4
        val rowH = (size.height - pad * 2) / rows
        val unit = (size.width - pad * 2) / 10f
        val r = CornerRadius(unit * 0.18f)

        fun keyAt(x: Float, row: Int, w: Float, brush: Brush) {
            drawRoundRect(
                brush,
                topLeft = Offset(x + gap, pad + row * rowH + gap),
                size = Size(w - gap * 2, rowH - gap * 2),
                cornerRadius = r,
            )
        }
        for (i in 0 until 10) keyAt(pad + i * unit, 0, unit, key)
        for (i in 0 until 9) keyAt(pad + unit * 0.5f + i * unit, 1, unit, key)
        keyAt(pad, 2, unit * 1.5f, mod)
        for (i in 0 until 7) keyAt(pad + unit * 1.5f + i * unit, 2, unit, key)
        keyAt(pad + unit * 8.5f, 2, unit * 1.5f, mod)
        keyAt(pad, 3, unit * 1.5f, mod)
        keyAt(pad + unit * 1.5f, 3, unit, mod)
        keyAt(pad + unit * 2.5f, 3, unit * 6f, key)
        keyAt(pad + unit * 8.5f, 3, unit * 1.5f, accent)
    }
}

/** What to paint behind the miniature keys, for any of the three backdrop kinds. */
private fun io.keystudio.core.model.Theme.backdropBrush(): Brush = when (val bg = background) {
    is BackgroundSpec.Fill -> bg.color.toBrush()
    is BackgroundSpec.Animated -> Brush.linearGradient(
        bg.colors.map { Color(it) }.ifEmpty { listOf(Color.Black, Color.Black) },
    )
    is BackgroundSpec.Image -> Brush.linearGradient(listOf(Color(0xFF5B5B63), Color(0xFF26262B)))
}

@Composable
private fun TestField() {
    val c = Studio.colors
    var text by remember { mutableStateOf("") }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(io.keystudio.kb.ui.editor.GroupShape)
            .background(c.surface)
            .border(1.dp, c.hairline, io.keystudio.kb.ui.editor.GroupShape)
            .padding(16.dp),
    ) {
        if (text.isEmpty()) {
            Text("Tap here and start typing…", color = c.textTertiary, style = StudioType.body)
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = StudioType.body.copy(color = c.text),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Embeds the production keyboard view in preview mode. Taps animate and emit
 * effects but never produce text.
 */
@Composable
private fun KeyboardPreview(profile: KeyboardProfile, store: ProfileStore) {
    AndroidView(
        factory = { ctx ->
            KeyboardView(ctx).apply {
                previewMode = true
                this.profile = profile
                layout = store.loadLayout(profile.layoutId)
            }
        },
        update = { view ->
            if (view.profile != profile) {
                view.profile = profile
                view.layout = store.loadLayout(profile.layoutId)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
    )
}

private fun isImeEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

/**
 * The framework stores the default IME as a component string, but abbreviates
 * the class relative to the package ("pkg/.ime.Foo"). Matching on the package
 * half is both sufficient and immune to that spelling.
 */
private fun isImeDefault(context: Context): Boolean {
    val current = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD,
    ).orEmpty()
    return current.substringBefore('/') == context.packageName
}

