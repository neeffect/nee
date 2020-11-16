package dev.neeffect.nee.security.oauth

import dev.neeffect.nee.security.state.ServerVerifier
import io.ktor.client.HttpClient
import java.security.KeyPair
import java.security.SecureRandom
import java.util.*

open class OauthConfigModule(val config: OauthConfig) {

    open val rng: Random by lazy {
        SecureRandom()
    }

    open val keyPair: KeyPair by lazy {
        ServerVerifier.generateKeyPair()
    }

    open  val serverVerifier: ServerVerifier by lazy {
        ServerVerifier(rng = rng, keyPair = keyPair)
    }
    open val httpClient by lazy {
        HttpClient()
    }
}
