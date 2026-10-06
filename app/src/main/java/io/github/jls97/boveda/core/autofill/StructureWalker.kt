package io.github.jls97.boveda.core.autofill

/**
 * Vista mínima de un nodo del árbol de vistas que recibe el servicio de autorrelleno. En Android la
 * implementa un adaptador sobre `AssistStructure.ViewNode`; en los tests, árboles sintéticos. Así el
 * recorrido ([StructureWalker]) es lógica pura y comprobable en la JVM. [T] es el id de plataforma.
 */
interface AutofillNode<T> {
    val autofillId: T?

    /** El nodo admite texto (`AUTOFILL_TYPE_TEXT`). */
    val isTextField: Boolean

    /** `visibility == VISIBLE`. */
    val isVisible: Boolean
    val width: Int
    val height: Int

    /** Opacidad informada por la app, o null si no se conoce. */
    val alpha: Float?

    /** Dominio y esquema del documento web declarados en este nodo (null si no los declara). */
    val webDomain: String?
    val webScheme: String?

    /** Señales con las que [FieldClassifier] decide qué es el campo. */
    val signals: FieldSignals

    /** Texto actual del campo, si la plataforma lo entrega (solo en peticiones de guardado). */
    val text: String?
    val children: List<AutofillNode<T>>
}

/** Un campo clasificado junto con el texto que contenía. */
class WalkedField<T>(val field: DetectedField<T>, val text: String?)

/**
 * Resultado del recorrido: los campos útiles con el dominio y esquema efectivos de cada uno, y el
 * dominio y esquema de la ventana principal (el primer nodo del árbol que los declara).
 */
class WalkedStructure<T>(
    val mainWebDomain: String?,
    val mainWebScheme: String?,
    val fields: List<WalkedField<T>>,
)

/**
 * Recorre el árbol de vistas de forma iterativa y acotada y clasifica cada campo de texto. Cada campo
 * hereda el `webDomain` y `webScheme` del ancestro más cercano que los declare, para que luego se exija
 * que todo lo que se rellena pertenezca al mismo origen (un iframe de otro dominio no puede recibir
 * las credenciales de la página que lo incrusta).
 */
object StructureWalker {
    /** Más nodos que esto no es una pantalla normal: se deja de responder. */
    const val MAX_NODES = 5_000
    const val MAX_DEPTH = 256

    /** Los textos de la app solo sirven para clasificar; más allá de esto no aportan nada. */
    const val MAX_TEXT_LENGTH = 256

    /** Un campo prácticamente transparente no se considera visible. */
    private const val MIN_ALPHA = 0.1f

    private class Pending<T>(val node: AutofillNode<T>, val depth: Int, val webDomain: String?, val webScheme: String?)

    /** Los campos de [roots] (una raíz por ventana), o null si el árbol excede las cotas. */
    fun <T> walk(roots: List<AutofillNode<T>>): WalkedStructure<T>? {
        val fields = ArrayList<WalkedField<T>>()
        var mainWebDomain: String? = null
        var mainWebScheme: String? = null
        var visited = 0

        val stack = ArrayDeque<Pending<T>>()
        // En orden inverso para que el recorrido conserve el orden en pantalla.
        for (root in roots.asReversed()) stack.addLast(Pending(root, 0, null, null))

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (++visited > MAX_NODES || current.depth > MAX_DEPTH) return null
            val node = current.node

            val webDomain = node.webDomain?.takeIf { it.isNotBlank() }?.trim() ?: current.webDomain
            val webScheme = node.webScheme?.takeIf { it.isNotBlank() }?.trim() ?: current.webScheme
            if (mainWebDomain == null && webDomain != null) {
                mainWebDomain = webDomain
                mainWebScheme = webScheme
            }

            val id = node.autofillId
            if (id != null && node.isTextField && isShown(node)) {
                val kind = FieldClassifier.classify(truncated(node.signals))
                if (kind != FieldKind.IGNORED) {
                    fields += WalkedField(DetectedField(id, kind, webDomain, webScheme), node.text)
                }
            }

            val children = node.children
            for (index in children.indices.reversed()) {
                stack.addLast(Pending(children[index], current.depth + 1, webDomain, webScheme))
            }
        }
        return WalkedStructure(mainWebDomain, mainWebScheme, fields)
    }

    /** Visible de verdad: no basta con `VISIBLE` si mide 0 px o es transparente. */
    private fun isShown(node: AutofillNode<*>): Boolean {
        if (!node.isVisible || node.width <= 0 || node.height <= 0) return false
        val alpha = node.alpha
        return alpha == null || alpha >= MIN_ALPHA
    }

    private fun truncated(signals: FieldSignals): FieldSignals = FieldSignals(
        autofillHints = signals.autofillHints.map { it.take(MAX_TEXT_LENGTH) },
        inputKind = signals.inputKind,
        htmlTag = signals.htmlTag?.take(MAX_TEXT_LENGTH),
        htmlAttributes = signals.htmlAttributes.entries.associate { (name, value) -> name.take(MAX_TEXT_LENGTH) to value.take(MAX_TEXT_LENGTH) },
        texts = signals.texts.map { it.take(MAX_TEXT_LENGTH) },
    )
}
