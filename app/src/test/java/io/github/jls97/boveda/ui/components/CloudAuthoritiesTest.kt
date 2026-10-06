package io.github.jls97.boveda.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El selector solo recibe una pista (EXTRA_LOCAL_ONLY); la app rechaza la nube conocida (B-42). */
class CloudAuthoritiesTest {

    @Test
    fun knownCloudProvidersAreRejected() {
        for (authority in listOf(
            "com.google.android.apps.docs.storage",
            "com.google.android.apps.docs.storage.legacy",
            "com.dropbox.android.FileCache",
            "com.dropbox.product.android.dbapp.document_provider.documents",
            "com.microsoft.skydrive.content.StorageAccessProvider",
            "com.box.android.documents",
            "mega.privacy.android.app.provider",
            "com.nextcloud.client.documents",
        )) {
            assertTrue(authority, CloudAuthorities.isCloud(authority))
        }
    }

    @Test
    fun matchingIgnoresCaseAndSurroundingSpace() {
        assertTrue(CloudAuthorities.isCloud(" COM.GOOGLE.ANDROID.APPS.DOCS.STORAGE "))
    }

    @Test
    fun localProvidersAreAllowed() {
        for (authority in listOf(
            "com.android.externalstorage.documents",
            "com.android.providers.downloads.documents",
            "com.android.providers.media.documents",
            "com.google.android.apps.docsx.fake", // solo coincide el prefijo exacto o seguido de punto
            "com.dropboxfake.documents",
        )) {
            assertFalse(authority, CloudAuthorities.isCloud(authority))
        }
    }

    @Test
    fun missingAuthorityIsNotCloud() {
        assertFalse(CloudAuthorities.isCloud(null))
        assertFalse(CloudAuthorities.isCloud(""))
        assertFalse(CloudAuthorities.isCloud("   "))
    }
}
