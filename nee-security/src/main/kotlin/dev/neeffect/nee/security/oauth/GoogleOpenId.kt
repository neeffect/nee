package dev.neeffect.nee.security.oauth

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import dev.neeffect.nee.Nee
import dev.neeffect.nee.NoEffect
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.async.InPlaceExecutor
import dev.neeffect.nee.effects.security.SecurityErrorType
import dev.neeffect.nee.effects.utils.Logging
import dev.neeffect.nee.effects.utils.logger
import dev.neeffect.nee.security.jwt.MultiVerifier
import io.fusionauth.jwks.JSONWebKeySetHelper
import io.fusionauth.jwks.domain.JSONWebKey
import io.fusionauth.jwt.Verifier
import io.fusionauth.jwt.domain.JWT
import io.fusionauth.jwt.json.Mapper
import io.fusionauth.jwt.rsa.RSAVerifier
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.vavr.concurrent.Future
import io.vavr.control.Either
import io.vavr.control.Option
import io.vavr.control.Try
import io.vavr.kotlin.option
import io.vavr.kotlin.toVavrList
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.net.URL
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.CompletableFuture

interface OauthProvider {
    fun generateApiCall(redirect: String): String

    fun verifyOauthToken(code: String, redirectUri: String, state: String): Nee<Any, SecurityErrorType, OauthResponse>
}

class GoogleOpenId<USER, ROLE>(
    private val oauthConfigModule: OauthConfigModule<USER, ROLE>
) : OauthProvider, Logging {

    private val googleJwtDecoder = JWT.getTimeMachineDecoder(
        oauthConfigModule.jwtConfigModule.timeProvider.getTimeSource().now()
    )

    private val verifier = createVerifier()

    override fun generateApiCall(redirect: String) =
        apiUrlTemplate(
            oauthConfigModule.config.getClientId(OauthProviderName.Google),
            redirect,
            oauthConfigModule.serverVerifier.generateRandomSignedState(),
            oauthConfigModule.randomGenerator.nextFloat().toString()
        )

    override fun verifyOauthToken(code: String, redirectUri: String, state: String) =
        Nee.constWithError(NoEffect<Any, SecurityErrorType>()) { _ ->

            Out.Companion.fromFuture(
                Future.fromCompletableFuture(InPlaceExecutor, callGoogle(code, redirectUri)).map { result ->
                    result.idToken.toEither<SecurityErrorType>(
                        SecurityErrorType.MalformedCredentials("no idToken received")
                    ).flatMap { idToken ->
                        Try.of {
                            val decodedIDToken = googleJwtDecoder.decode(idToken, verifier)
                            val email = Option.of(decodedIDToken.getString("email"))
                            val name = Option.of(decodedIDToken.getString("name"))
                            OauthResponse(result, decodedIDToken.subject, name, email)
                        }.toEither().mapLeft<SecurityErrorType> {
                            SecurityErrorType.MalformedCredentials(it.localizedMessage)
                        }
                    }
                }.orElse {
                    Future.successful(Either.left<SecurityErrorType, OauthResponse>(SecurityErrorType.NoSecurityCtx))
                }
            )
        }

    private fun createVerifier() =
        oauthConfigModule.config.providers[OauthProviderName.Google.providerName]
            .flatMap {
                it.certificatesFile
            }
            .getOrElse("https://www.googleapis.com/oauth2/v3/certs") // TODO use discovery doc
            .let { jwkFile ->
                val verifiers = retrieveJsonKeys(jwkFile)
                    .toVavrList().map { jwk ->
                        JSONWebKey.parse(jwk)
                    }
                    .filter { (it is RSAPublicKey) }
                    .map { RSAVerifier.newVerifier(it as RSAPublicKey) as Verifier }
                MultiVerifier(verifiers)
            }

    private fun retrieveJsonKeys(url: String): List<JSONWebKey> = if (url.startsWith("file:/")) {
        val fileURL = URL(url)
        fileURL.openStream().use { jkeyInputStream ->
            Mapper.deserialize(jkeyInputStream, LocalJSONWebKeySetResponse::class.java).keys
        }
    } else {
        JSONWebKeySetHelper.retrieveKeysFromJWKS(url)
    }

    // TODO - what is this stupid GlobalScope here? - clean it or test it
    @SuppressWarnings("TooGenericExceptionCaught")
    private fun callGoogle(code: String, redirectUri: String): CompletableFuture<OauthTokens> = GlobalScope.future {
        try {
            val result: OauthTokens = oauthConfigModule.httpClient.submitForm<OauthTokens>(
                url = "https://oauth2.googleapis.com/token",

                formParameters = Parameters.build {
                    append("code", code)
                    append("client_id", oauthConfigModule.config.getClientId(OauthProviderName.Google))
                    append("client_secret", oauthConfigModule.config.getClientSecret(OauthProviderName.Google))
                    append("redirect_uri", redirectUri)
                    append("grant_type", "authorization_code")
                },
                encodeInQuery = false,

                )
            result
        } catch (e: Exception) {
            logger().warn(e.message, e)
            throw e
        }
    }

    companion object {
        fun apiUrlTemplate(clientId: String, redirect: String, state: String, nonce: String) =
            """
        https://accounts.google.com/o/oauth2/v2/auth?
        response_type=code&
        client_id=$clientId&
        scope=openid%20profile%20email%20https://www.googleapis.com/auth/user.organization.read&
        redirect_uri=$redirect&
        state=$state&
        login_hint=jsmith@example.com&
        nonce=$nonce""".trimIndent().replace("\n", "")
    }
}

data class OauthTokens(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("id_token")
    val idToken: Option<String> = Option.none(),
    val refreshToken: Option<String> = Option.none(),
    val expiresIn: String = "0", // TODO
    val scope: String = "",
    val tokenType: String = ""
) {
    @JsonCreator
    constructor(
        @JsonProperty("access_token")
        accessToken: String,
        @JsonProperty("id_token")
        idToken: String? = null,
        @JsonProperty("refresh_token")
        refreshToken: String? = null,
        @JsonProperty("expires_in")
        expiresIn: String = "0",
        @JsonProperty("scope")
        scope: String = "",
        @JsonProperty("token_type")
        tokenType: String = ""
    ) : this(accessToken, idToken.option(), refreshToken.option(), expiresIn, scope, tokenType)
}

data class OauthResponse(
    val tokens: OauthTokens,
    val subject: String,
    val displayName: Option<String>,
    val email: Option<String>
)

@Suppress("ImpureCode")
internal class LocalJSONWebKeySetResponse {
    val keys: List<JSONWebKey> = emptyList()
}
