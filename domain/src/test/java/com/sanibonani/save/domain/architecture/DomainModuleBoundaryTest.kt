package com.sanibonani.save.domain.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the `:domain` module boundary described in AGENTS.md:
 *
 * > `:domain` (Models, Interfaces, UseCases, Services, Validation)
 * > depends on nothing (no Android framework imports, no Supabase SDK)
 *
 * `:domain` is currently built as an Android library, so nothing prevents new
 * `android.*` references from creeping in. These tests scan domain sources for
 * both `import` statements and fully-qualified usages, and fail the build when
 * they appear, rather than letting the rule erode silently.
 *
 * Note on `android.os.Parcelable`: domain models implement `Parcelable` so they
 * can cross Compose navigation boundaries. That coupling is a consequence of
 * `:domain` being an Android library today and is tolerated; eliminating it
 * requires converting `:domain` to a JVM module and is tracked separately.
 */
class DomainModuleBoundaryTest {

    private val domainRoot: File by lazy { File("src/main/java") }

    private fun ktFiles(): List<File> {
        require(domainRoot.isDirectory) {
            "Expected domain source root at ${domainRoot.absolutePath}. Run :domain:test."
        }
        return domainRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun `domain sources do not reference the Supabase SDK`() {
        val offenders = ktFiles().filter { file ->
            sanitizedLines(file).any { it.contains(SUPABASE_PKG) }
        }
        assertTrue(
            buildMessage("Supabase SDK references are only allowed in :data and :app", offenders),
            offenders.isEmpty()
        )
    }

    @Test
    fun `domain sources do not reference Android framework classes other than Parcelable`() {
        val offenders = ktFiles().filter { file ->
            sanitizedLines(file).any { line -> line.containsAndroidReferenceNotParcelable() }
        }
        assertTrue(
            buildMessage(
                "Android framework references are only allowed in :data and :app " +
                    "(android.os.Parcelable is tolerated for Compose navigation)",
                offenders
            ),
            offenders.isEmpty()
        )
    }

    /**
     * Strips comments and string literals so that prose ("...android.util.Log..." in
     * a log message or KDoc) cannot trigger a false positive.
     */
    private fun sanitizedLines(file: File): Sequence<String> {
        val text = file.readText()
        val withoutBlockComments = BLOCK_COMMENT.replace(text, "")
        val withoutLineComments = LINE_COMMENT.replace(withoutBlockComments, "")
        val withoutStrings = STRING_LITERAL.replace(withoutLineComments, "")
        val withoutRawStrings = RAW_STRING.replace(withoutStrings, "")
        return withoutRawStrings.lineSequence()
    }

    private fun String.containsAndroidReferenceNotParcelable(): Boolean =
        ANDROID_REFERENCE.findAll(this).any { match ->
            match.value.removePrefix("android.") != "os.Parcelable"
        }

    private fun buildMessage(header: String, offenders: List<File>): String =
        buildString {
            appendLine(header)
            appendLine("Offending files:")
            offenders.forEach { appendLine("  - ${it.path.replace('\\', '/')}") }
        }

    private companion object {
        const val SUPABASE_PKG = "io.github.jan.supabase"

        /** Matches `android.<Identifier>` including fully-qualified usages. */
        val ANDROID_REFERENCE = Regex("""\bandroid\.[A-Za-z][A-Za-z0-9_.]*""")

        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
        val STRING_LITERAL = Regex(""""(\\.|[^"\\])*"""")
        val RAW_STRING = Regex(""""{3}[\s\S]*?"{3}""")
    }
}
