package io.keystudio.core.store

import android.content.Context
import io.keystudio.core.model.KeyboardLayout
import io.keystudio.core.model.KeyboardProfile
import io.keystudio.core.presets.Layouts
import io.keystudio.core.presets.Themes
import kotlinx.serialization.json.Json
import java.io.File

/**
 * File-backed storage for user profiles and layouts.
 *
 * Profiles are plain JSON on purpose: a keyboard the user designed should be a
 * file they can copy, back up and send to someone else.
 */
class ProfileStore(context: Context) {

    private val root = File(context.filesDir, "keystudio").apply { mkdirs() }
    private val profileDir = File(root, "profiles").apply { mkdirs() }
    private val layoutDir = File(root, "layouts").apply { mkdirs() }
    private val imageDir = File(root, "images").apply { mkdirs() }
    private val fontDir = File(root, "fonts").apply { mkdirs() }
    private val activeFile = File(root, "active.txt")
    private val settingsFile = File(root, "settings.json")

    /** Words the keyboard has learned. Written only by the keyboard process. */
    val learnedWordsFile: File get() = File(root, "learned.tsv")

    /**
     * Directories the keyboard watches for changes. The Studio and the IME run
     * in separate processes, so files are the channel between them.
     *
     * `FileObserver` is not recursive, so the profile and layout directories
     * have to be listed alongside the root: the root alone would catch a change
     * of *which* profile is active (`active.txt`) but not an edit to the active
     * profile's contents, which is every stroke of the theme editor.
     */
    val watchDirs: List<File> get() = listOf(root, profileDir, layoutDir)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    // -- profiles -------------------------------------------------------------

    fun listProfiles(): List<KeyboardProfile> {
        val user = profileDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { json.decodeFromString<KeyboardProfile>(it.readText()) }.getOrNull() }
            .orEmpty()
        // Presets first, then the user's own. An edited preset stays in its own
        // place rather than jumping to the end, so editing never reshuffles the grid.
        val byId = user.associateBy { it.id }
        val presetIds = Themes.all.map { it.id }.toSet()
        return Themes.all.map { byId[it.id] ?: it } + user.filterNot { it.id in presetIds }
    }

    fun loadProfile(id: String): KeyboardProfile {
        val file = File(profileDir, "$id.json")
        if (file.exists()) {
            runCatching { json.decodeFromString<KeyboardProfile>(file.readText()) }
                .onSuccess { return it }
        }
        return Themes.byId(id)
    }

    fun saveProfile(profile: KeyboardProfile) {
        writeAtomically(File(profileDir, "${profile.id}.json"), json.encodeToString(profile))
    }

    fun deleteProfile(id: String): Boolean = File(profileDir, "$id.json").delete()

    /**
     * Whether the user has their own version of this id. Presets are shadowed
     * rather than replaced, so a preset the user has edited has both a built-in
     * definition and a file -- and can be reverted by deleting the file.
     */
    fun hasUserCopy(id: String): Boolean = File(profileDir, "$id.json").exists()

    fun isPreset(id: String): Boolean = Themes.all.any { it.id == id }

    /** Drops the user's edits to a preset, restoring the built-in version. */
    fun resetProfile(id: String): KeyboardProfile {
        if (isPreset(id)) deleteProfile(id)
        return loadProfile(id)
    }

    /**
     * A fresh id derived from a name. Kept filesystem-safe because the id *is*
     * the filename, and unique because a duplicate would silently overwrite.
     */
    fun newIdFor(name: String): String {
        val base = name.lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
            .take(24)
            .ifEmpty { "keyboard" }
        var candidate = base
        var n = 2
        val taken = (listProfiles().map { it.id }).toSet()
        while (candidate in taken) {
            candidate = "$base-$n"
            n++
        }
        return candidate
    }

    fun exportProfile(profile: KeyboardProfile): String = json.encodeToString(profile)

    fun importProfile(text: String): KeyboardProfile =
        json.decodeFromString<KeyboardProfile>(text)

    // -- images ---------------------------------------------------------------

    /**
     * Copies a picked image into app storage and returns its path.
     *
     * The renderer decodes a plain file, not a `content://` URI, and it does so
     * from the keyboard process. Copying once here means the keyboard never
     * depends on a grant that can be revoked, on the gallery app still being
     * installed, or on the user not having deleted the photo.
     */
    fun importImage(context: Context, uri: android.net.Uri): String? =
        copyIn(context, uri, imageDir, "bg", "img")

    /** Same, for a user's own font file. */
    fun importFont(context: Context, uri: android.net.Uri): String? =
        copyIn(context, uri, fontDir, "font", "ttf")

    private fun copyIn(
        context: Context,
        uri: android.net.Uri,
        dir: File,
        prefix: String,
        extension: String,
    ): String? {
        val target = File(dir, "$prefix-${System.currentTimeMillis().toString(36)}.$extension")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.absolutePath
        }.getOrNull()
    }

    /** Removes images no saved profile refers to any more. */
    fun pruneImages() {
        val inUse = listProfiles()
            .flatMap { listOf(it.light.background, it.dark.background) }
            .filterIsInstance<io.keystudio.core.model.BackgroundSpec.Image>()
            .map { it.uri }
            .toSet()
        imageDir.listFiles()?.forEach { if (it.absolutePath !in inUse) it.delete() }
    }

    // -- active selection -----------------------------------------------------

    fun activeProfileId(): String =
        runCatching { activeFile.readText().trim() }.getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: Themes.default.id

    fun setActiveProfileId(id: String) {
        activeFile.writeText(id)
    }

    fun activeProfile(): KeyboardProfile = loadProfile(activeProfileId())

    // -- typing settings ------------------------------------------------------

    fun loadSettings(): io.keystudio.core.model.TypingSettings =
        runCatching {
            json.decodeFromString<io.keystudio.core.model.TypingSettings>(settingsFile.readText())
        }.getOrNull() ?: io.keystudio.core.model.TypingSettings()

    fun saveSettings(settings: io.keystudio.core.model.TypingSettings) {
        writeAtomically(settingsFile, json.encodeToString(settings))
    }

    // -- layouts --------------------------------------------------------------

    fun listLayouts(): List<KeyboardLayout> {
        val user = layoutDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { json.decodeFromString<KeyboardLayout>(it.readText()) }.getOrNull() }
            .orEmpty()
        val userIds = user.map { it.id }.toSet()
        return Layouts.all.filterNot { it.id in userIds } + user
    }

    fun loadLayout(id: String): KeyboardLayout {
        val file = File(layoutDir, "$id.json")
        if (file.exists()) {
            runCatching { json.decodeFromString<KeyboardLayout>(file.readText()) }
                .onSuccess { return it }
        }
        return Layouts.byId(id)
    }

    fun saveLayout(layout: KeyboardLayout) {
        writeAtomically(File(layoutDir, "${layout.id}.json"), json.encodeToString(layout))
    }

    // -- writing --------------------------------------------------------------

    /**
     * Write to a sibling temp file and rename over the target.
     *
     * The keyboard process reads these files the moment it is told they changed,
     * and the theme editor rewrites the active profile on every frame of a
     * slider drag. A plain `writeText` would regularly be caught mid-write and
     * parse as nothing, dropping the keyboard back to its built-in preset for a
     * frame. Rename is atomic, so a reader sees either the old file or the new.
     */
    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            // Same-directory rename should not fail; if it somehow does, a direct
            // write still beats losing the edit.
            target.writeText(text)
            tmp.delete()
        }
    }

    fun deleteLayout(id: String): Boolean = File(layoutDir, "$id.json").delete()

    /** Whether the user has edited this layout (a built-in one is shadowed, as presets are). */
    fun hasUserLayout(id: String): Boolean = File(layoutDir, "$id.json").exists()

    fun isBuiltInLayout(id: String): Boolean = io.keystudio.core.presets.Layouts.all.any { it.id == id }
}
