package io.github.jls97.boveda.autofill

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import io.github.jls97.boveda.core.autofill.AppCertificates
import io.github.jls97.boveda.core.autofill.AutofillTarget
import io.github.jls97.boveda.core.autofill.TargetResolver
import java.security.MessageDigest

internal object AppSigners {
    /**
     * Signing certificates of an installed app, as verified by Android, or null if they can't be
     * read. Needs QUERY_ALL_PACKAGES: the app being filled isn't visible to Bóveda otherwise.
     */
    fun certificatesOf(context: Context, packageName: String): AppCertificates? {
        val signingInfo = try {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            ).signingInfo
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } ?: return null

        return if (signingInfo.hasMultipleSigners()) {
            val token = signingInfo.apkContentsSigners.orEmpty().map(::fingerprint).sorted().joinToString(",")
            if (token.isEmpty()) null else AppCertificates(current = token, accepted = setOf(token))
        } else {
            // Oldest certificate first, current one last.
            val history = signingInfo.signingCertificateHistory.orEmpty().map(::fingerprint)
            if (history.isEmpty()) null else AppCertificates(current = history.last(), accepted = history.toSet())
        }
    }

    /** Who is asking, with the web domain trusted only if the app is a verified browser. */
    fun resolveTarget(context: Context, packageName: String, reportedWebDomain: String?): AutofillTarget =
        TargetResolver.resolve(packageName, certificatesOf(context, packageName), reportedWebDomain)

    private fun fingerprint(signature: Signature): String =
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
}
