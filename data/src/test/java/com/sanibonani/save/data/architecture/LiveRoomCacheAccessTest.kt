package com.sanibonani.save.data.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the rule `SupabaseConnectionManager` exists for: a Room cache is only safe to hold while
 * it is the live one, and the manager replaces the cache whenever the tier flips.
 *
 * The DAOs and the database handed out by the DI graph are resolved once, at the first
 * construction that asks for them (`app` `AppModule` `provideDatabase` reads whichever instance
 * the manager held at that moment). A class that keeps such a reference in a `private val`
 * therefore keeps reading the closed database after a tier switch - the failure `#80` describes.
 *
 * So a class may take the database as `Provider<SanibonaniDatabase>` and resolve it per access,
 * which is what the repositories that were already fixed do. This scan fails on the frozen form.
 */
class LiveRoomCacheAccessTest {

    private val roots: List<File> = listOf(File("src/main/java"), File("../app/src/main/java"))
        .filter(File::isDirectory)

    @Test
    fun `no class holds the room cache itself`() {
        val offenders = ktFiles().filter { file ->
            sanitizedLines(file).any { DATABASE_FIELD.matches(it) }
        }
        assertTrue(
            buildMessage(
                "Take the cache as Provider<SanibonaniDatabase> and resolve it per access, " +
                    "so a tier switch cannot leave a reader on a closed database",
                offenders
            ),
            offenders.isEmpty()
        )
    }

    @Test
    fun `no class holds a dao from one cache`() {
        val offenders = ktFiles().filter { file ->
            sanitizedLines(file).any { DAO_FIELD.matches(it) }
        }
        assertTrue(
            buildMessage(
                "A DAO is bound to the database that produced it. Resolve it from the live cache " +
                    "with dbProvider.get().groupDao() instead of injecting it",
                offenders
            ),
            offenders.isEmpty()
        )
    }

    @Test
    fun `the di graph hands out no singleton daos`() {
        val module = File("../app/src/main/java/com/sanibonani/save/di/AppModule.kt")
        assertTrue("Expected the app DI module at ${module.absolutePath}", module.isFile)
        val providers = sanitizedLines(module).filter { DAO_PROVIDER.containsMatchIn(it) }.toList()
        assertTrue(
            buildString {
                appendLine("A @Singleton DAO provider freezes the cache that produced it.")
                providers.forEach { appendLine("  - ${it.trim()}") }
            },
            providers.isEmpty()
        )
    }

    private fun ktFiles(): List<File> {
        assertTrue("Expected at least one scanned source root. Run :data:test.", roots.isNotEmpty())
        return roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" } }
    }

    /** Strips comments and string literals so prose in a KDoc cannot trigger a false positive. */
    private fun sanitizedLines(file: File): Sequence<String> {
        val withoutBlockComments = BLOCK_COMMENT.replace(file.readText(), "")
        val withoutLineComments = LINE_COMMENT.replace(withoutBlockComments, "")
        val withoutRawStrings = RAW_STRING.replace(withoutLineComments, "")
        return STRING_LITERAL.replace(withoutRawStrings, "").lineSequence()
    }

    private fun buildMessage(header: String, offenders: List<File>): String =
        buildString {
            appendLine(header)
            appendLine("Offending files:")
            offenders.forEach { appendLine("  - ${it.path.replace('\\', '/')}") }
        }

    private companion object {
        /** `val db: SanibonaniDatabase` as a constructor parameter or a backing property. */
        val DATABASE_FIELD = Regex("""\b(?:private\s+)?(?:protected\s+)?val\s+\w+\s*:\s*SanibonaniDatabase\s*[,)]""")
        val DAO_FIELD = Regex("""\b(?:private\s+)?(?:protected\s+)?val\s+\w*[Dd]ao\w*\s*:\s*[\w.]*Dao\s*[,)]""")
        val DAO_PROVIDER = Regex("""fun\s+provide\w*Dao\s*\(""")

        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
        val STRING_LITERAL = Regex(""""(\\.|[^"\\])*"""")
        val RAW_STRING = Regex(""""{3}[\s\S]*?"{3}""")
    }
}
