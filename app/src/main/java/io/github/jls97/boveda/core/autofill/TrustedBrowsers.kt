package io.github.jls97.boveda.core.autofill

/**
 * Apps allowed to say which web site they are showing: browsers whose signing certificate
 * matches. Any other app that reports a web domain is not believed, because any app can put a
 * domain in its screen structure, and an app hosting a WebView can read what gets filled into it.
 *
 * Source: Google's list of privileged apps for Credential Manager (apps trusted to act for web
 * origins), as copied in Bitwarden's `fido2_privileged_google.json` on 2026-09-28. Only
 * production ("release") certificates are kept; "userdebug" ones are test keys. Fingerprints are
 * SHA-256 of the signing certificate, lowercase hex.
 */
object TrustedBrowsers {
    private val BROWSERS: Map<String, Set<String>> = mapOf(
        "ai.perplexity.comet" to setOf("8958a405401f69f5b0fb544424746c40dec30c091f401f951f613c4835c3e5ec"),
        "app.vanadium.browser" to setOf("c6adb8b83c6d4c17d292afde56fd488a51d316ff8f2c11c5410223bff8a7dbb3"),
        "com.amazon.cloud9" to setOf(
            "2f19adeb284eb36f7f07786152b9a1d14b21653203ad0b04ebbf9c73ab6d7625",
            "70d568ec6ae6f338bc1a6399a6537ee06908ca1d72fb8ff04874ab95433b250e",
        ),
        "com.android.browser" to setOf("c9009d01ebf9f5d0302bc71b2fe9aa9a47a432bba17308a3111b75d7b2149025"),
        "com.android.chrome" to setOf("f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83"),
        "com.brave.browser" to setOf("9c2db70513515fdbfbbc585b3edf3d7123d4dc67c94ffd306361c1d79bbf18ac"),
        "com.brave.browser_beta" to setOf("9c2db70513515fdbfbbc585b3edf3d7123d4dc67c94ffd306361c1d79bbf18ac"),
        "com.brave.browser_nightly" to setOf("9c2db70513515fdbfbbc585b3edf3d7123d4dc67c94ffd306361c1d79bbf18ac"),
        "com.chrome.beta" to setOf(
            "3d7a1223019aa39d9ea0e3436ab7c0896bfb4fb679f4de5fe7c23f326c8f994a",
            "da633d34b69e63ae2103b49d53ce052fc5f7f3c53aab94fdc2a208bdfd14249c",
        ),
        "com.chrome.canary" to setOf("2019dfa1fb23efbf70c5bcd1443c5beab04f3f2ff4366e9ac1e3457639a24cfc"),
        "com.chrome.dev" to setOf(
            "3d7a1223019aa39d9ea0e3436ab7c0896bfb4fb679f4de5fe7c23f326c8f994a",
            "9044ee5fee4bbc5e21dd44665431c4eb1f1f71a32716a0bc927bcbb39233cabf",
        ),
        "com.citrix.Receiver" to setOf(
            "3dd112671069ab364ef9be739ab7b5ee15e1cde9d8757b1bf064f50c55689a49",
            "aad0d457e633c3782577305bc1b2d9e38141c721df0daa6e29072fc41d34f0ab",
            "ceb223d77709f2b6bc0b3a7836f5a5af4ce1d355f4a72886f79df80dc9d6122e",
        ),
        "com.duckduckgo.mobile.android" to setOf("bb7bb31c573c46a1da7fc5c528a6acf432108456feec50810c7f33694eb3d2d4"),
        "com.duckduckgo.mobile.android.debug" to setOf("c4f09e2bd725adf5ad920ba2802766ac164ac153b3ea9e0848b0579837f76a29"),
        "com.fido.fido2client" to setOf("fc98dae63ad39626c8c67fbe83f2f06f74932a9cd146b92cecfc6a047a904386"),
        "com.google.android.gms" to setOf(
            "1975b2f17177bc89a5dff31f9e64a6cae281a53dc1d1d59b1d147fe1c82afa00",
            "7ce83c1b71f3d572fed04c8d40c5cb10ff75e6d87d9df6fbd53f0468c2905053",
            "d22cc500299fb22873a01a010de1c82fbe4d061119b94814dd301dab50cb7678",
            "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83",
        ),
        "com.heytap.browser" to setOf(
            "a8fea4cafb9332da26b8e6810817c1da90a5030e35a60a79e06c9097aac6a442",
            "aff8a749cf0e7d754465d0fbfa7b8d0c645e225c10c6e232ada0d9748836b8e5",
            "b29aa0bbdc9fd9def55dc56ea7d74576d5846cbcf5e5abd305e2d9319e4f42ae",
        ),
        "com.microsoft.emmx" to setOf("01e1999710a82c2749b4d50c445dc85d670b6136089d0a766a73827c82a1eac9"),
        "com.microsoft.emmx.beta" to setOf("01e1999710a82c2749b4d50c445dc85d670b6136089d0a766a73827c82a1eac9"),
        "com.microsoft.emmx.canary" to setOf("01e1999710a82c2749b4d50c445dc85d670b6136089d0a766a73827c82a1eac9"),
        "com.microsoft.emmx.dev" to setOf("01e1999710a82c2749b4d50c445dc85d670b6136089d0a766a73827c82a1eac9"),
        "com.naver.whale" to setOf("0b8b8523bb4aeffa346e4bdd4fbf7d193450569aa14aaad4adfd94a3f7b227bb"),
        "com.opera.browser" to setOf("5d6afbf87f652af04647ada0df634cf22370900b164b09d50bd23aa2cb5285b8"),
        "com.opera.browser.beta" to setOf("5d6afbf87f652af04647ada0df634cf22370900b164b09d50bd23aa2cb5285b8"),
        "com.opera.mini.native" to setOf("57acbc525f1b2ebd19196cd6f014397cc910fd18841e0ae850febc3e1e593ff2"),
        "com.opera.mini.native.beta" to setOf("57acbc525f1b2ebd19196cd6f014397cc910fd18841e0ae850febc3e1e593ff2"),
        "com.oplus.credential" to setOf("e49802409584ce53152a9000820a51e4fa8a723b7bcc263e335240acf100bf9e"),
        "com.sec.android.app.sbrowser" to setOf(
            "34df0e7a9f1cf1892e45c056b4973cd81ccf148a4050d11aea4ac5a65f900a42",
            "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8",
        ),
        "com.sec.android.app.sbrowser.beta" to setOf(
            "34df0e7a9f1cf1892e45c056b4973cd81ccf148a4050d11aea4ac5a65f900a42",
            "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8",
        ),
        "com.talonsec.intune.talon" to setOf("090c4ce620659b0400358fc83fdc9f02d2ab77c37f4c8014308eff96ba544955"),
        "com.talonsec.talon" to setOf(
            "a3660344a6f6afca818cbf4396a23ccfd5ed7a781bb4a3d1850301e2f46d2383",
            "e2a56474ea237b0667b6f52cdce9045e24883baed082599aa2df0b603acf6a3b",
        ),
        "com.talonsec.talon_beta" to setOf(
            "9aa125d5e55e3fb0de9672d9a95d04653f494a1ec3ee761e94c44e5d2f658e2f",
            "f586627a32c89fe67e006db18c34319e017fb3b2bed69d0101b7f943e77c48ae",
        ),
        "com.vivaldi.browser" to setOf("e8a78544655ba8c09817f732768f5689b1662ec4b2bc5a0bc0ec138d33ca3d1e"),
        "com.vivaldi.browser.snapshot" to setOf("e8a78544655ba8c09817f732768f5689b1662ec4b2bc5a0bc0ec138d33ca3d1e"),
        "com.vivaldi.browser.sopranos" to setOf("e8a78544655ba8c09817f732768f5689b1662ec4b2bc5a0bc0ec138d33ca3d1e"),
        "com.wsapp.browser" to setOf("f5219530fc8f87993b3cf50e838a9675ab3b2dc579a2a8520d99872a26b15c5f"),
        "com.yandex.browser" to setOf("aca405ded8b25cb2e8c6da69425d2b4307d087c1276fc06ad5942731ccc51dba"),
        "com.yandex.browser.alpha" to setOf("aca405ded8b25cb2e8c6da69425d2b4307d087c1276fc06ad5942731ccc51dba"),
        "com.yandex.browser.beta" to setOf("aca405ded8b25cb2e8c6da69425d2b4307d087c1276fc06ad5942731ccc51dba"),
        "com.yandex.browser.broteam" to setOf("1da9cbae2dccc6a58d6c947be94cdbb733d65da4d1770fa14a5364cb4a28eb49"),
        "com.yandex.browser.canary" to setOf("1da9cbae2dccc6a58d6c947be94cdbb733d65da4d1770fa14a5364cb4a28eb49"),
        "com.yandex.browser.corp" to setOf("aca405ded8b25cb2e8c6da69425d2b4307d087c1276fc06ad5942731ccc51dba"),
        "com.zoho.primeum.stable" to setOf("a9d6d0a2afdb15849b8cd31d51fe73b8e1b170baa570c2f8f2a3f8652829cbbd"),
        "cz.seznam.sbrowser" to setOf("db9540661078836e4eb166f69ef407309e8dae3334685ec8f6fa2f1381b9acf6"),
        "io.island.Island" to setOf("d9c339ac9c3aeee1751d858c35d9bac5cc87b3ce763093f0f51064f5a2f69b04"),
        "io.island.IslandBeta" to setOf("3531831a9e2b211de6aac3694b45836e5609b9d7d004c31b218740fb771738d1"),
        "io.island.IslandCanary" to setOf("90171323456e6f39cbfdcfb256be1dcff3bc1c598a159330e49773d04cb9c905"),
        "io.island.island.beta.intune" to setOf("d25eadf61ce6366ca423a47fc4db9b8c9c8a35b4b019e8d982fbd08ad9db495a"),
        "io.island.island.canary.intune" to setOf("1e1674bb79ea09fb37cf9f1b071b1d518d46030ed3eef2c14ead939ec6ee3a4c"),
        "io.island.island.intune" to setOf("c23824154120a08fc39542acd82ae9249478801e47fd6c662b181c28ca7e594e"),
        "net.quetta.browser" to setOf("befee731126aa56e7efdaeaf5ef3faea441c19cce0caec426b65bbf82c594680"),
        "net.waterfox.android.release" to setOf("2939997a2d8f07303ceb37ad6810afef0bda710be2116476e3525a7379ec2e1a"),
        "org.chromium.chrome" to setOf("c6adb8b83c6d4c17d292afde56fd488a51d316ff8f2c11c5410223bff8a7dbb3"),
        "org.mozilla.fenix" to setOf("5004779088e7f988d5bc5cc5f8798febf4f8cd084a1b2a46efd4c8ee4aeaf211"),
        "org.mozilla.fennec_aurora" to setOf("bc0488838d06f4ca6bf32386daab0dd8ebcf3e7730787459f62fb3cd14a1baaa"),
        "org.mozilla.firefox" to setOf("a78b62a5165b4494b2fead9e76a280d22d937fee6251aece599446b2ea319b04"),
        "org.mozilla.firefox_beta" to setOf("a78b62a5165b4494b2fead9e76a280d22d937fee6251aece599446b2ea319b04"),
        "org.mozilla.focus" to setOf("6203a473be36d64ee37f87fa500edbc79eab930610ab9b9fa4ca7d5c1f1b4ffc"),
        "org.mozilla.focus.beta" to setOf("6203a473be36d64ee37f87fa500edbc79eab930610ab9b9fa4ca7d5c1f1b4ffc"),
        "org.mozilla.focus.nightly" to setOf("6203a473be36d64ee37f87fa500edbc79eab930610ab9b9fa4ca7d5c1f1b4ffc"),
        "org.mozilla.klar" to setOf("6203a473be36d64ee37f87fa500edbc79eab930610ab9b9fa4ca7d5c1f1b4ffc"),
        "org.mozilla.reference.browser" to setOf("b00990e30f9d815d2ebc7b9bb221ce47e5c9d517aac70e7fd595b1e53e9a4b14"),
        "org.mozilla.rocket" to setOf("863a46f0973932b7d0199b549112741c2d2731ac72ea11b7523aa90a11bf5691"),
    )

    /** Signing certificates accepted for [packageName], empty if it isn't a trusted browser. */
    fun certificatesOf(packageName: String): Set<String> = BROWSERS[packageName].orEmpty()

    /**
     * True if [packageName] is a trusted browser and one of its verified certificates is known.
     * A multi-signer token ("a,b") counts if any of its signers is known: faking one of them
     * would require that browser's private key.
     */
    fun isTrusted(packageName: String, certificates: AppCertificates): Boolean {
        val known = BROWSERS[packageName] ?: return false
        return certificates.accepted.any { token -> token.split(',').any { it in known } }
    }
}
