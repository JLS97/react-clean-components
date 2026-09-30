package io.github.jls97.boveda.autofill

import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest

/**
 * Android's autofill entry point. Everything happens on the phone: the system hands over the
 * structure of the screen being filled, and Bóveda answers with a suggestion or a save offer.
 * It never reads the vault here; that only happens in [AutofillActivity] after unlocking.
 */
class BovedaAutofillService : AutofillService() {

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val response: FillResponse? = try {
            val structure = request.fillContexts.lastOrNull()?.structure
            val parsed = structure?.let { StructureParser.parse(it) }
            val login = parsed?.login
            if (parsed == null || login == null || parsed.packageName == packageName) {
                null
            } else {
                AutofillResponses.fillResponse(this, request.inlineSuggestionsRequest, parsed, login)
            }
        } catch (e: Exception) {
            // A screen we don't understand must never break the other app.
            null
        }
        callback.onSuccess(response)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val saveIntent = try {
            val structure = request.fillContexts.lastOrNull()?.structure
            val parsed = structure?.let { StructureParser.parse(it) }
            val login = parsed?.login
            val password = login?.let { fields ->
                parsed.textOf(fields.password) ?: fields.newPasswords.firstNotNullOfOrNull { parsed.textOf(it) }
            }
            if (parsed == null || login == null || password.isNullOrEmpty() || parsed.packageName == packageName) {
                null
            } else {
                val target = AppSigners.resolveTarget(this, parsed.packageName, parsed.reportedWebDomain)
                val token = PendingSaves.put(PendingSave(target, parsed.textOf(login.username).orEmpty(), password))
                AutofillActivity.saveIntentSender(this, token)
            }
        } catch (e: Exception) {
            null
        }
        if (saveIntent == null) callback.onSuccess() else callback.onSuccess(saveIntent)
    }
}
