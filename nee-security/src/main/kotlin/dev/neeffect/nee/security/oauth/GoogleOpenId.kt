package dev.neeffect.nee.security.oauth

import dev.neeffect.nee.security.state.ServerVerifier
import java.security.SecureRandom
import java.util.*

class GoogleOpenId(
    private val serverVerifier: ServerVerifier,
    private val config: OauthConfig,
    private val rng: Random = SecureRandom()
) {


    fun generateApiCall(redirect: String) =
        apiUrlTemplate(
            config.clientId,
            redirect,
            serverVerifier.generateRandomSignedState(),
            rng.nextFloat().toString()
        )

    companion object {
        fun apiUrlTemplate(clientId: String, redirect: String, state: String, nonce: String) =
            """
        https://accounts.google.com/o/oauth2/v2/auth?
        response_type=code&
        client_id=${clientId}&
        scope=openid&
        redirect_uri=${redirect}&
        state=${state}&
        login_hint=jsmith@example.com&
        nonce=${nonce}""".trimIndent().replace("\n", "")
    }
}


data class OauthUser(val id: String)
