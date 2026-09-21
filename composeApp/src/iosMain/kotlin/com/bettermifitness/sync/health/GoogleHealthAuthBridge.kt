package com.bettermifitness.sync.health

/**
 * Filled from Swift (`GoogleHealthAuth`) at app launch so the Kotlin OAuth flow can
 * present `ASWebAuthenticationSession` without importing AuthenticationServices types.
 *
 * Uses only simple closure/method signatures that map cleanly across Kotlin/Native
 * (no nested function types): Kotlin calls [presenter] with the auth URL, Swift
 * presents the session and reports back via [complete].
 */
object GoogleHealthAuthBridge {
    /** iOS OAuth client id from `credentials.plist`. */
    var clientId: String? = null

    /** Reversed client id used as the OAuth redirect URL scheme. */
    var reversedClientId: String? = null

    /** Swift presents the given URL; calls back via [complete] when done. */
    var presenter: ((url: String, callbackScheme: String) -> Unit)? = null

    private var pending: ((String?) -> Unit)? = null

    fun setConfig(clientId: String?, reversedClientId: String?) {
        this.clientId = clientId
        this.reversedClientId = reversedClientId
    }

    /** Called by the Kotlin OAuth flow to register the completion before presenting. */
    fun setPending(callback: ((String?) -> Unit)?) {
        pending = callback
    }

    /** Called by Swift with the full redirect URL, or null on cancel/error. */
    fun complete(redirectUrl: String?) {
        val callback = pending
        pending = null
        callback?.invoke(redirectUrl)
    }
}
