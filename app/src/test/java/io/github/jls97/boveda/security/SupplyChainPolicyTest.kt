package io.github.jls97.boveda.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Política de cadena de suministro de la build (R06-1, R06-3, R06-4): las acciones de la CI van
 * fijadas por SHA de commit, Dependabot vigila acciones y dependencias Gradle, la verificación de
 * dependencias sigue activa y la documentación remite al mismo archivo de credenciales que lee
 * `app/build.gradle.kts`. Los tests JVM de Gradle se ejecutan con el módulo `app` como directorio
 * de trabajo; si no es así, se busca desde la raíz del repositorio.
 */
class SupplyChainPolicyTest {

    private fun repoFile(relativePath: String): File {
        val candidates = listOf(File("../$relativePath"), File(relativePath))
        return candidates.firstOrNull { it.isFile }
            ?: error("No se encuentra $relativePath; directorio de trabajo: ${File(".").absolutePath}")
    }

    private val usesLine = Regex("""^\s*-?\s*uses:\s*(\S+)@([0-9a-f]{40})\s+#\s*v\d+\.\d+\.\d+\s*$""")

    @Test
    fun `cada accion de la CI va fijada al SHA completo con el tag como comentario`() {
        val lines = repoFile(".github/workflows/ci.yml").readLines()
        val uses = lines.filter { it.trimStart().startsWith("uses:") || it.contains(" uses: ") }
        assertTrue("ci.yml debe usar acciones", uses.isNotEmpty())
        for (line in uses) {
            assertTrue("Acción sin fijar por SHA: '$line'", usesLine.matches(line))
        }
    }

    @Test
    fun `la CI no concede mas que lectura del repositorio`() {
        val text = repoFile(".github/workflows/ci.yml").readText()
        assertTrue(text.contains("permissions:\n  contents: read"))
        assertFalse(text.contains("contents: write"))
    }

    @Test
    fun `dependabot vigila las acciones y las dependencias gradle cada semana`() {
        val text = repoFile(".github/dependabot.yml").readText()
        assertTrue(text.contains("version: 2"))
        val ecosystems = Regex("""package-ecosystem:\s*(\S+)""").findAll(text).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("github-actions", "gradle"), ecosystems)
        assertEquals(2, Regex("""interval:\s*weekly""").findAll(text).count())
        assertTrue(
            "Las PR de gradle requieren regenerar la verificación de dependencias",
            text.contains("--write-verification-metadata sha256"),
        )
    }

    @Test
    fun `la verificacion de dependencias sigue activa y en modo estricto`() {
        val metadata = repoFile("gradle/verification-metadata.xml").readText()
        assertTrue(metadata.contains("<verify-metadata>true</verify-metadata>"))
        assertTrue(metadata.contains("<sha256 value=\""))
        val properties = repoFile("gradle.properties").readText()
        assertTrue(properties.contains("org.gradle.dependency.verification=strict"))
    }

    @Test
    fun `la documentacion remite al mismo archivo de credenciales que lee el build`() {
        val build = repoFile("app/build.gradle.kts").readText()
        val security = repoFile("SECURITY.md").readText()
        val release = repoFile("docs/RELEASE.md").readText()
        assertTrue(build.contains("rootProject.file(\"local.properties\")"))
        assertFalse(build.contains("keystore.properties"))
        assertFalse("SECURITY.md remite a un archivo que el build no lee", security.contains("keystore.properties"))
        assertTrue(security.contains("`local.properties`"))
        assertTrue(release.contains("`local.properties`"))
    }

    @Test
    fun `el build permite exigir la firma y documenta la bandera`() {
        val build = repoFile("app/build.gradle.kts").readText()
        val release = repoFile("docs/RELEASE.md").readText()
        assertTrue(build.contains("signingSetting(\"BOVEDA_REQUIRE_SIGNING\") == \"1\""))
        assertTrue(build.contains("throw GradleException("))
        assertTrue(release.contains("export BOVEDA_REQUIRE_SIGNING=1"))
    }
}
