package dev.neeffect.nee.security.oauth

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import dev.neeffect.nee.Nee
import dev.neeffect.nee.NoEffect
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.async.InPlaceExecutor
import dev.neeffect.nee.effects.security.SecurityErrorType
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.vavr.concurrent.Future
import io.vavr.control.Either
import io.vavr.control.Option
import io.vavr.control.Try
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

interface OauthProvider {
    fun generateApiCall(redirect: String): String

    fun verifyOauthToken(code: String): Nee<Any, SecurityErrorType, Unit, OauthTokens>
}


class GoogleOpenId(
    private val oauthConfigModule: OauthConfigModule
) : OauthProvider {
    override fun generateApiCall(redirect: String) =
        apiUrlTemplate(
            oauthConfigModule.config.clientId,
            redirect,
            oauthConfigModule.serverVerifier.generateRandomSignedState(),
            oauthConfigModule.rng.nextFloat().toString()
        )

    override fun verifyOauthToken(code: String) = Nee.constWithError(NoEffect<Any,SecurityErrorType>() ) {_->
        Out.Companion.fromFuture(
            Future.fromCompletableFuture(InPlaceExecutor, fut(code)).map {result ->
                    Either.right<SecurityErrorType, OauthTokens>(result)
            }.orElse {
                Future.successful(Either.left<SecurityErrorType, OauthTokens>(SecurityErrorType.NoSecurityCtx))
            }
        )
    }

    //TODO - what is this stupid GlobalScope here? - clean it or test it
    private fun fut (code: String) : CompletableFuture<OauthTokens> = GlobalScope.future(block = {

            val result: OauthTokens = oauthConfigModule.httpClient.submitForm<OauthTokens>(
                url = "https://oauth2.googleapis.com/token",
                formParameters = Parameters.build {
                    append("code", code)
                    append("client_id", oauthConfigModule.config.clientId)
                    append("client_secret", oauthConfigModule.config.clientSecret)
                    append("redirect_uri", "kpisz panie")
                    append("grant_type", "authorization_code")
                },
                encodeInQuery = false,

                )
            result


    })

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

data class OauthTokens(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("id_token")
    val idToken: String,
    val refreshToken: Option<String>
) {
    @JsonCreator
    constructor(
        @JsonProperty("access_token")
        accessToken: String,
        @JsonProperty("id_token")
        idToken: String,
        @JsonProperty("refresh_token")
        refreshToken: String
    ) : this(accessToken, idToken, Option.some(refreshToken))
}

