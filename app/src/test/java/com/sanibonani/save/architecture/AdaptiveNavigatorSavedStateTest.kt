package com.sanibonani.save.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ratchet for what a `ListDetailPaneScaffold` navigator is allowed to carry.
 *
 * `rememberListDetailPaneScaffoldNavigator<T>()` stores every value handed to
 * `navigateTo(role, value)` in a `rememberSaveable` slot, so the navigator's
 * `saver` runs on the Activity's `onSaveInstanceState`. `Member` and `LedgerEntry`
 * are `@Serializable` domain models with no `@Parcelize` and no `Saver`, so saving
 * one throws `IllegalArgumentException: item at index N can't be saved` and kills
 * the process the moment the app is backgrounded with a row open in the detail pane.
 *
 * AGENTS.md already states the rule this guards: models are `@Serializable` only,
 * so navigation passes ids. The detail panes in these files render from their own
 * selection state and never read the navigator's content, so keying the navigator by
 * the row id costs nothing.
 */
class AdaptiveNavigatorSavedStateTest {

    private val featuresRoot: File by lazy { File("src/main/java/com/sanibonani/save/features") }

    private fun uiFiles(): List<File> {
        require(featuresRoot.isDirectory) {
            "Expected app feature sources at ${featuresRoot.absolutePath}. Run :app:test."
        }
        return featuresRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

    private fun File.relativePath(): String =
        relativeTo(featuresRoot).path.replace('\\', '/')

    /** Every `rememberListDetailPaneScaffoldNavigator<T>()` type argument in the UI layer. */
    private fun navigatorTypeArguments(): List<Pair<String, String>> =
        uiFiles().flatMap { file ->
            sanitizedLines(file)
                .mapNotNull { NAVIGATOR_DECLARATION.find(it.trim())?.groupValues?.get(1)?.trim() }
                .map { file.relativePath() to it }
        }

    @Test
    fun `adaptive navigators are keyed by a value the framework can save`() {
        val offenders = navigatorTypeArguments().filterNot { (_, typeArgument) ->
            typeArgument in SAVEABLE_TYPES
        }
        assertTrue(
            buildString {
                appendLine(
                    "Adaptive navigators carrying a type the saved-state framework cannot save. " +
                        "Backgrounding the app with such a row selected crashes it. " +
                        "Key the navigator by the row id and render the detail pane from state."
                )
                offenders.sortedBy { (path, _) -> path }.forEach { (path, typeArgument) ->
                    appendLine("  - $path -> rememberListDetailPaneScaffoldNavigator<$typeArgument>")
                }
            },
            offenders.isEmpty()
        )
    }

    @Test
    fun `the guard sees the navigators the app actually declares`() {
        val declared = navigatorTypeArguments().map { it.second }
        assertTrue(
            "Expected the scan to find the app's adaptive navigators; it found none, so it proves nothing.",
            declared.isNotEmpty()
        )
    }

    /** Strips comments and string literals so prose in KDoc cannot trigger a false positive. */
    private fun sanitizedLines(file: File): Sequence<String> {
        val text = file.readText()
        val withoutBlockComments = BLOCK_COMMENT.replace(text, "")
        val withoutLineComments = LINE_COMMENT.replace(withoutBlockComments, "")
        val withoutStrings = STRING_LITERAL.replace(withoutLineComments, "")
        val withoutRawStrings = RAW_STRING.replace(withoutStrings, "")
        return withoutRawStrings.lineSequence()
    }

    private companion object {
        /** The types `SaveableStateRegistry` accepts without a custom `Saver`. */
        val SAVEABLE_TYPES = setOf("String", "Int", "Long", "Double", "Boolean")

        val NAVIGATOR_DECLARATION =
            Regex("""rememberListDetailPaneScaffoldNavigator<(.+?)>\(\s*\)""")

        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
        val STRING_LITERAL = Regex(""""(\\.|[^"\\])*"""")
        val RAW_STRING = Regex(""""{3}[\s\S]*?"{3}""")
    }
}
