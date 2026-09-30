package io.github.jls97.boveda.autofill

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.Presentations
import android.service.autofill.SaveInfo
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import io.github.jls97.boveda.MainActivity
import io.github.jls97.boveda.R
import io.github.jls97.boveda.core.autofill.LoginFields

/**
 * Builds what Bóveda answers to the system. The answer never contains secrets: it is a single
 * "Bóveda" suggestion that opens [AutofillActivity], where the user unlocks and picks an entry.
 * Only that choice travels back, straight into the fields of the other app.
 */
internal object AutofillResponses {
    private const val TITLE = "Bóveda"
    private const val SUBTITLE = "Toca para elegir cuenta"

    fun fillResponse(
        context: Context,
        inlineRequest: InlineSuggestionsRequest?,
        parsed: ParsedStructure,
        login: LoginFields<AutofillId>,
    ): FillResponse? {
        val builder = FillResponse.Builder()
        var hasContent = false

        if (login.fillIds.isNotEmpty()) {
            val dataset = Dataset.Builder(presentations(context, inlineRequest, TITLE, SUBTITLE))
            // No values yet: they arrive after authentication, from AutofillActivity.
            login.fillIds.forEach { dataset.setField(it, null) }
            dataset.setAuthentication(
                AutofillActivity.fillIntentSender(context, parsed.packageName, parsed.reportedWebDomain, login.username, login.password),
            )
            builder.addDataset(dataset.build())
            hasContent = true
        }

        saveInfo(login)?.let {
            builder.setSaveInfo(it)
            hasContent = true
        }
        return if (hasContent) builder.build() else null
    }

    /** The dataset returned after the user picked an entry: it is filled in right away. */
    fun filledDataset(
        context: Context,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
        username: String,
        password: String,
    ): Dataset? {
        val dataset = Dataset.Builder(
            Presentations.Builder().setMenuPresentation(menuPresentation(context, TITLE, "Rellenado")).build(),
        )
        var fieldCount = 0
        if (usernameId != null && username.isNotEmpty()) {
            dataset.setField(usernameId, Field.Builder().setValue(AutofillValue.forText(username)).build())
            fieldCount++
        }
        if (passwordId != null && password.isNotEmpty()) {
            dataset.setField(passwordId, Field.Builder().setValue(AutofillValue.forText(password)).build())
            fieldCount++
        }
        return if (fieldCount > 0) dataset.build() else null
    }

    /** Asks Android to offer "Save to Bóveda" when a login or sign-up form is submitted. */
    private fun saveInfo(login: LoginFields<AutofillId>): SaveInfo? {
        val passwordIds = listOfNotNull(login.password) + login.newPasswords
        if (passwordIds.isEmpty()) return null
        val type = if (login.username != null) {
            SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
        } else {
            SaveInfo.SAVE_DATA_TYPE_PASSWORD
        }
        val saveInfo = SaveInfo.Builder(type, passwordIds.toTypedArray())
        login.username?.let { saveInfo.setOptionalIds(arrayOf(it)) }
        saveInfo.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
        return saveInfo.build()
    }

    private fun presentations(
        context: Context,
        inlineRequest: InlineSuggestionsRequest?,
        title: String,
        subtitle: String,
    ): Presentations {
        val presentations = Presentations.Builder().setMenuPresentation(menuPresentation(context, title, subtitle))
        inlinePresentation(context, inlineRequest, title, subtitle)?.let { presentations.setInlinePresentation(it) }
        return presentations.build()
    }

    /** Drop-down under the field, used when the keyboard can't show suggestions itself. */
    private fun menuPresentation(context: Context, title: String, subtitle: String): RemoteViews =
        RemoteViews(context.packageName, R.layout.autofill_suggestion).apply {
            setTextViewText(R.id.autofill_title, title)
            setTextViewText(R.id.autofill_subtitle, subtitle)
        }

    /**
     * Chip in the keyboard's suggestion strip (Gboard and other keyboards that support it).
     *
     * `getSlice()` is marked as restricted to the androidx.autofill library, yet it is how the
     * library's own documentation builds an InlinePresentation, and there is no public alternative.
     */
    @SuppressLint("RestrictedApi")
    private fun inlinePresentation(
        context: Context,
        inlineRequest: InlineSuggestionsRequest?,
        title: String,
        subtitle: String,
    ): InlinePresentation? {
        if (inlineRequest == null || inlineRequest.maxSuggestionCount < 1) return null
        val spec = inlineRequest.inlinePresentationSpecs.firstOrNull() ?: return null
        if (UiVersions.INLINE_UI_VERSION_1 !in UiVersions.getVersions(spec.style)) return null
        // Long-pressing the chip opens Bóveda itself.
        val attribution = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val slice = InlineSuggestionUi.newContentBuilder(attribution)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setStartIcon(Icon.createWithResource(context, R.drawable.ic_autofill))
            .setContentDescription("$title. $subtitle")
            .build()
            .slice
        return InlinePresentation(slice, spec, false)
    }
}
