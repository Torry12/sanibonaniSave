package com.sanibonani.save.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ratchet for the presentation layer described in AGENTS.md:
 *
 * > `:app` (UI, ViewModels, DI, Workers, Services, Analytics)
 * > depends on `:domain` + `:data`
 *
 * Presentation is allowed to depend on `:domain`, but reaching straight into a
 * repository port or a `:data` implementation keeps business rules in ViewModels.
 * Both lists below held the files that did when this test was introduced; every one
 * of them has since been routed through a use case or a domain port, so they are
 * empty and a new offender fails the build.
 */
class PresentationLayeringTest {

    private val featuresRoot: File by lazy { File("src/main/java/com/sanibonani/save/features") }

    private fun presentationFiles(): List<File> {
        require(featuresRoot.isDirectory) {
            "Expected app sources at ${featuresRoot.absolutePath}. Run :app:test."
        }
        return featuresRoot.walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.extension == "kt" &&
                    file.relativePath().contains("/presentation/")
            }
            .toList()
    }

    private fun File.relativePath(): String =
        relativeTo(featuresRoot).path.replace('\\', '/')

    private fun filesImporting(packagePrefix: String): Set<String> {
        val declaration = Regex("""^import\s+${Regex.escape(packagePrefix)}""")
        return presentationFiles()
            .filter { file -> sanitizedLines(file).any { declaration.containsMatchIn(it.trim()) } }
            .map { it.relativePath() }
            .toSet()
    }

    @Test
    fun `presentation does not grow its direct dependency on data implementations`() {
        val offenders = filesImporting("com.sanibonani.save.data.") - KNOWN_DATA_IMPORTS
        assertTrue(
            buildMessage(
                "New presentation-to-:data imports. Move the logic behind a :domain use case instead.",
                offenders
            ),
            offenders.isEmpty()
        )
    }

    @Test
    fun `presentation does not grow its direct dependency on repository ports`() {
        val offenders = filesImporting("com.sanibonani.save.domain.repository.") - KNOWN_REPOSITORY_IMPORTS
        assertTrue(
            buildMessage(
                "New presentation-to-repository imports. Extract the rule into a :domain use case instead.",
                offenders
            ),
            offenders.isEmpty()
        )
    }

    @Test
    fun `presentation never imports the Supabase SDK`() {
        val offenders = filesImporting("io.github.jan.supabase.")
        assertTrue(
            buildMessage("The Supabase SDK belongs in :data and :app services, not presentation.", offenders),
            offenders.isEmpty()
        )
    }

    private fun sanitizedLines(file: File): Sequence<String> {
        val text = file.readText()
        val withoutBlockComments = BLOCK_COMMENT.replace(text, "")
        val withoutLineComments = LINE_COMMENT.replace(withoutBlockComments, "")
        val withoutStrings = STRING_LITERAL.replace(withoutLineComments, "")
        val withoutRawStrings = RAW_STRING.replace(withoutStrings, "")
        return withoutRawStrings.lineSequence()
    }

    private fun buildMessage(header: String, offenders: Collection<String>): String =
        buildString {
            appendLine(header)
            offenders.sorted().forEach { appendLine("  - $it") }
        }

    private companion object {
        val KNOWN_DATA_IMPORTS = emptySet<String>()

        val KNOWN_REPOSITORY_IMPORTS = emptySet<String>()

        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
        val STRING_LITERAL = Regex(""""(\\.|[^"\\])*"""")
        val RAW_STRING = Regex(""""{3}[\s\S]*?"{3}""")
    }
}
