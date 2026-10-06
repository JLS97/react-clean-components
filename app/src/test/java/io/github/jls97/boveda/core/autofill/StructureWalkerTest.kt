package io.github.jls97.boveda.core.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El recorrido del árbol de vistas con árboles sintéticos: iframes, páginas http, campos ocultos y árboles hostiles. */
class StructureWalkerTest {

    private class FakeNode(
        override val autofillId: String? = null,
        override val isTextField: Boolean = autofillId != null,
        override val isVisible: Boolean = true,
        override val width: Int = 200,
        override val height: Int = 48,
        override val alpha: Float? = null,
        override val webDomain: String? = null,
        override val webScheme: String? = null,
        override val signals: FieldSignals = FieldSignals(),
        override val text: String? = null,
        override val children: List<AutofillNode<String>> = emptyList(),
    ) : AutofillNode<String>

    private fun userField(id: String, width: Int = 200, height: Int = 48, alpha: Float? = null) =
        FakeNode(id, width = width, height = height, alpha = alpha, signals = FieldSignals(inputKind = InputKind.EMAIL))

    private fun passwordField(id: String, width: Int = 200, height: Int = 48, alpha: Float? = null) =
        FakeNode(id, width = width, height = height, alpha = alpha, signals = FieldSignals(inputKind = InputKind.PASSWORD))

    private fun otpField(id: String) = FakeNode(id, signals = FieldSignals(autofillHints = listOf("smsOTPCode")))

    private fun walk(vararg roots: AutofillNode<String>) = StructureWalker.walk(roots.toList())

    private fun login(walked: WalkedStructure<String>) = FieldSelection.select(walked.fields.map { it.field }, walked.mainWebDomain)

    /** Dominio de los campos elegidos, como lo calcula ParsedStructure. */
    private fun domainOf(walked: WalkedStructure<String>, login: LoginFields<String>): String? =
        walked.fields.filter { it.field.id in login.allIds }.firstNotNullOfOrNull { it.field.webDomain }

    @Test
    fun fieldsInheritDomainAndSchemeFromNearestAncestor() {
        val tree = FakeNode(
            webDomain = "banco.es",
            webScheme = "https",
            children = listOf(
                FakeNode(children = listOf(userField("user"), passwordField("pass"))),
                FakeNode(webDomain = "evil.com", webScheme = "http", children = listOf(passwordField("evilPass"))),
            ),
        )
        val walked = walk(tree)!!
        assertEquals("banco.es", walked.mainWebDomain)
        assertEquals("https", walked.mainWebScheme)
        val byId = walked.fields.associate { it.field.id to it.field }
        assertEquals("banco.es", byId.getValue("user").webDomain)
        assertEquals("https", byId.getValue("pass").webScheme)
        assertEquals("evil.com", byId.getValue("evilPass").webDomain)
        assertEquals("http", byId.getValue("evilPass").webScheme)
    }

    @Test
    fun passwordFieldInsideForeignIframeIsNeverFilled() {
        // Página de banco.es con un iframe de un tercero que pinta su propio formulario antes del real.
        val tree = FakeNode(
            webDomain = "banco.es",
            children = listOf(
                FakeNode(webDomain = "evil.com", children = listOf(userField("evilUser"), passwordField("evilPass"))),
                userField("user"),
                passwordField("pass"),
            ),
        )
        val walked = walk(tree)!!
        val login = login(walked)!!
        assertEquals(LoginFields("user", "pass", emptyList()), login)
        assertEquals("banco.es", domainOf(walked, login))

        // Si el único formulario está en el iframe, no se rellena nada.
        val onlyIframe = FakeNode(
            webDomain = "banco.es",
            children = listOf(FakeNode(webDomain = "evil.com", children = listOf(userField("evilUser"), passwordField("evilPass")))),
        )
        assertNull(login(walk(onlyIframe)!!))
    }

    @Test
    fun otpFieldInsideForeignIframeIsDropped() {
        val tree = FakeNode(
            webDomain = "banco.es",
            children = listOf(
                userField("user"),
                passwordField("pass"),
                FakeNode(webDomain = "evil.com", children = listOf(otpField("evilOtp"))),
            ),
        )
        val login = login(walk(tree)!!)!!
        assertEquals(LoginFields("user", "pass", emptyList(), otp = null), login)
    }

    @Test
    fun evilMainPageEmbeddingTheBankGetsItsOwnDomainOnly() {
        val tree = FakeNode(
            webDomain = "evil.com",
            children = listOf(
                userField("user"),
                passwordField("pass"),
                FakeNode(webDomain = "banco.es", children = listOf(passwordField("bankPass"))),
            ),
        )
        val walked = walk(tree)!!
        val login = login(walked)!!
        assertEquals(LoginFields("user", "pass", emptyList()), login)
        assertEquals("evil.com", domainOf(walked, login))
        assertFalse("bankPass" in login.allIds)
    }

    @Test
    fun browserAddressBarIsNotTakenAsUsername() {
        // La barra de direcciones es un campo nativo sin dominio delante del documento.
        val tree = FakeNode(
            children = listOf(
                FakeNode("urlBar", signals = FieldSignals(inputKind = InputKind.TEXT)),
                FakeNode(webDomain = "banco.es", children = listOf(passwordField("pass"))),
            ),
        )
        val walked = walk(tree)!!
        assertEquals(LoginFields(null, "pass", emptyList()), login(walked))
        assertEquals("banco.es", walked.mainWebDomain)
    }

    @Test
    fun singleOriginKeepsEveryField() {
        // Una app nativa: ningún dominio, y se rellena como siempre.
        val app = FakeNode(children = listOf(userField("user"), passwordField("pass")))
        val walked = walk(app)!!
        val login = login(walked)!!
        assertEquals(LoginFields("user", "pass", emptyList()), login)
        assertNull(domainOf(walked, login))
        assertNull(walked.mainWebDomain)

        // Varias ventanas de la misma web.
        val windows = walk(
            FakeNode(webDomain = "www.banco.es", children = listOf(userField("user"))),
            FakeNode(webDomain = "banco.es", children = listOf(passwordField("pass"))),
        )!!
        assertEquals(LoginFields("user", "pass", emptyList()), login(windows))
    }

    @Test
    fun selectionRequiresOneHost() {
        fun f(id: String, kind: FieldKind, domain: String?) = DetectedField(id, kind, domain)

        // Sin dominio principal que decida, orígenes distintos se descartan todos.
        assertNull(FieldSelection.select(listOf(f("user", FieldKind.USERNAME, "a.com"), f("pass", FieldKind.PASSWORD, "b.com"))))
        assertEquals(
            LoginFields("user", null, emptyList()),
            FieldSelection.select(listOf(f("user", FieldKind.USERNAME, "a.com"), f("pass", FieldKind.PASSWORD, "b.com")), "a.com"),
        )
        // Subdominios distintos son hosts distintos: no comparten credenciales por accidente.
        assertEquals(
            LoginFields(null, "pass", emptyList()),
            FieldSelection.select(listOf(f("user", FieldKind.USERNAME, "ads.banco.es"), f("pass", FieldKind.PASSWORD, "banco.es")), "banco.es"),
        )
    }

    @Test
    fun httpPageIsDegradedToAClaim() {
        val chromeCertificate = TrustedBrowsers.certificatesOf("com.android.chrome").single()
        val chrome = AppCertificates(current = chromeCertificate, accepted = setOf(chromeCertificate))

        val http = TargetResolver.resolve("com.android.chrome", chrome, "banco.es", "http")
        assertNull(http.host)
        assertEquals("banco.es", http.claimedWebDomain)
        assertTrue(http.unencrypted)
        assertNull(http.key)

        for (scheme in listOf("https", "HTTPS", "https:", null)) {
            val target = TargetResolver.resolve("com.android.chrome", chrome, "banco.es", scheme)
            assertEquals(scheme, "banco.es", target.host)
            assertFalse(scheme, target.unencrypted)
        }
        assertTrue(TargetResolver.isUnencrypted("http"))
        assertTrue(TargetResolver.isUnencrypted("file"))
        assertFalse(TargetResolver.isUnencrypted(null))
        assertFalse(TargetResolver.isUnencrypted(" "))
        // Sin dominio no hay página que degradar.
        assertFalse(TargetResolver.resolve("com.android.chrome", chrome, null, "http").unencrypted)
    }

    @Test
    fun zeroSizedOrTransparentFieldsAreDropped() {
        val tree = FakeNode(
            children = listOf(
                userField("user"),
                passwordField("flat", height = 0),
                passwordField("thin", width = 0),
                passwordField("ghost", alpha = 0.05f),
                FakeNode("hidden", isVisible = false, signals = FieldSignals(inputKind = InputKind.PASSWORD)),
            ),
        )
        assertEquals(LoginFields("user", null, emptyList()), login(walk(tree)!!))

        // Un campo con opacidad informada y suficiente, o sin informar, sí cuenta.
        val visible = FakeNode(children = listOf(passwordField("dim", alpha = 0.5f), passwordField("plain")))
        assertEquals(listOf("dim", "plain"), walk(visible)!!.fields.map { it.field.id })
    }

    @Test
    fun hugeTreesGiveNullWithoutCrashing() {
        // Una cadena de 10 000 niveles: con recursión reventaría la pila.
        var chain: AutofillNode<String> = passwordField("deep")
        repeat(9_999) { chain = FakeNode(children = listOf(chain)) }
        assertNull(walk(chain))

        // 10 000 hermanos.
        assertNull(walk(FakeNode(children = List(10_000) { passwordField("p$it") })))

        // Justo en el límite todavía se responde; un nodo más, ya no.
        assertNotNull(walk(FakeNode(children = List(StructureWalker.MAX_NODES - 1) { FakeNode() })))
        assertNull(walk(FakeNode(children = List(StructureWalker.MAX_NODES) { FakeNode() })))
        var limit: AutofillNode<String> = FakeNode()
        repeat(StructureWalker.MAX_DEPTH) { limit = FakeNode(children = listOf(limit)) }
        assertNotNull(walk(limit))
        assertNull(walk(FakeNode(children = listOf(limit))))
    }

    @Test
    fun textsUsedToClassifyAreTruncated() {
        val padding = "a".repeat(StructureWalker.MAX_TEXT_LENGTH)
        val field = FakeNode("f", signals = FieldSignals(inputKind = InputKind.TEXT, texts = listOf(padding + " password")))
        // La palabra queda fuera del recorte, así que el campo ya no se toma por contraseña.
        assertEquals(FieldKind.OTHER_TEXT, walk(FakeNode(children = listOf(field)))!!.fields.single().field.kind)
        val short = FakeNode("f", signals = FieldSignals(inputKind = InputKind.TEXT, texts = listOf("password")))
        assertEquals(FieldKind.PASSWORD, walk(FakeNode(children = listOf(short)))!!.fields.single().field.kind)
    }

    @Test
    fun keepsFieldTextForSaveRequests() {
        val tree = FakeNode(children = listOf(passwordField("pass").let { FakeNode(it.autofillId, signals = it.signals, text = "s3cret") }))
        assertEquals("s3cret", walk(tree)!!.fields.single().text)
    }
}
