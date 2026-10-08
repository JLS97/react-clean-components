package io.github.jls97.boveda.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import io.github.jls97.boveda.core.autofill.AutofillNode
import io.github.jls97.boveda.core.autofill.FieldSignals
import io.github.jls97.boveda.core.autofill.InputKind
import io.github.jls97.boveda.core.autofill.ParsedStructure
import io.github.jls97.boveda.core.autofill.StructureWalker

/** Walks the view tree the system hands to autofill services and classifies every text field. */
internal object StructureParser {

    /** The parsed screen, or null when the tree is too big to be an honest screen. */
    fun parse(structure: AssistStructure): ParsedStructure<AutofillId>? {
        val roots = List(structure.windowNodeCount) { index -> ViewNodeAdapter(structure.getWindowNodeAt(index).rootViewNode) }
        val walked = StructureWalker.walk(roots) ?: return null
        return ParsedStructure(structure.activityComponent.packageName, walked)
    }

    /** [AutofillNode] over the platform's node, so the walk itself has no Android in it. */
    private class ViewNodeAdapter(private val node: AssistStructure.ViewNode) : AutofillNode<AutofillId> {
        override val autofillId: AutofillId? get() = node.autofillId
        override val isTextField: Boolean get() = node.autofillType == View.AUTOFILL_TYPE_TEXT
        override val isVisible: Boolean get() = node.visibility == View.VISIBLE
        override val width: Int get() = node.width
        override val height: Int get() = node.height
        override val alpha: Float get() = node.alpha
        override val webDomain: String? get() = node.webDomain
        override val webScheme: String? get() = node.webScheme
        override val signals: FieldSignals get() = signalsOf(node)
        override val text: String?
            get() {
                val value = node.autofillValue
                return if (value != null && value.isText) value.textValue.toString() else null
            }
        override val children: List<AutofillNode<AutofillId>>
            get() = List(node.childCount) { index -> ViewNodeAdapter(node.getChildAt(index)) }
    }

    private fun signalsOf(node: AssistStructure.ViewNode): FieldSignals {
        val html = node.htmlInfo
        val attributes = html?.attributes.orEmpty()
            .mapNotNull { attribute -> attribute.first?.let { name -> name.lowercase() to attribute.second.orEmpty() } }
            .toMap()
        return FieldSignals(
            autofillHints = node.autofillHints?.toList().orEmpty(),
            inputKind = inputKindOf(node.inputType),
            htmlTag = html?.tag,
            htmlAttributes = attributes,
            texts = listOfNotNull(node.hint, node.idEntry, node.contentDescription?.toString()),
        )
    }

    private fun inputKindOf(inputType: Int): InputKind {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> when (variation) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                -> InputKind.PASSWORD

                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                -> InputKind.EMAIL

                else -> InputKind.TEXT
            }
            InputType.TYPE_CLASS_NUMBER ->
                if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) InputKind.PASSWORD else InputKind.OTHER
            else -> InputKind.OTHER
        }
    }
}
