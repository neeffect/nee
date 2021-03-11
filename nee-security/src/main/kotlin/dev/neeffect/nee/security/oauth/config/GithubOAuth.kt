package dev.neeffect.nee.security.oauth.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import dev.neeffect.nee.Nee
import dev.neeffect.nee.NoEffect
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.async.InPlaceExecutor
import dev.neeffect.nee.effects.security.SecurityErrorType
import dev.neeffect.nee.effects.utils.Logging
import dev.neeffect.nee.effects.utils.logger
import dev.neeffect.nee.security.oauth.OauthConfigModule
import dev.neeffect.nee.security.oauth.OauthProvider
import dev.neeffect.nee.security.oauth.OauthProviderName
import dev.neeffect.nee.security.oauth.OauthResponse
import dev.neeffect.nee.security.oauth.OauthTokens
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.vavr.concurrent.Future
import io.vavr.control.Either
import io.vavr.kotlin.option
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture

class GithubOAuth<USER, ROLE>(
    private val oauthConfigModule: OauthConfigModule<USER, ROLE>
) : OauthProvider, Logging {

    override fun generateApiCall(redirect: String) =
        apiUrlTemplate(
            oauthConfigModule.config.getClientId(OauthProviderName.Github),
            redirect,
            oauthConfigModule.serverVerifier.generateRandomSignedState()
        )

    override fun verifyOauthToken(code: String, redirectUri: String, state: String) =
        Nee.constWithError(NoEffect<Any, SecurityErrorType>()) { _ ->

            Out.Companion.fromFuture(
                Future.fromCompletableFuture(InPlaceExecutor, getTokens(code, redirectUri, state)).flatMap { result ->
                    val accessToken = result.accessToken
                    val userData: Future<GithubUserData> =
                        Future.fromCompletableFuture(InPlaceExecutor, getUserData(accessToken))
                    userData.map { gitubUser ->
                        Either.right(
                            OauthResponse(
                                result,
                                gitubUser.id.toString(),
                                gitubUser.name.option().orElse(gitubUser.login.option()),
                                gitubUser.email.option()
                            )
                        )
                    }
                }
            )
        }

    private fun getUserData(accessToken: String) = GlobalScope.future {
        val result: GithubUserData = oauthConfigModule.httpClient.get<GithubUserData>(
            scheme = "https",
            host = "api.github.com",
            path = "/user"
        ) {
            this.header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        result
    }

    @SuppressWarnings("TooGenericExceptionCaught")
    private fun getTokens(code: String, redirectUri: String, state: String): CompletableFuture<OauthTokens> =
        GlobalScope.future {
            try {
                val result: OauthTokens = oauthConfigModule.httpClient.submitForm<OauthTokens>(
                    url = "https://github.com/login/oauth/access_token",

                    formParameters = Parameters.build {
                        append("code", code)
                        append("client_id", oauthConfigModule.config.getClientId(OauthProviderName.Github))
                        append("client_secret", oauthConfigModule.config.getClientSecret(OauthProviderName.Github))
                        append("redirect_uri", redirectUri)
                        append("state", state)
                    },
                    encodeInQuery = false,

                    ) {
                    header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }
                result
            } catch (e: Exception) {
                logger().warn(e.message, e)
                throw e
            }
        }

    companion object {
        fun apiUrlTemplate(clientId: String, redirect: String, state: String) =
            """
       https://github.com/login/oauth/authorize?
       client_id=$clientId&
       redirect_uri=$redirect&
       state=$state&
       allow_signup=true""".trimIndent().replace("\n", "")
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubUserData(
    val login: String,
    val id: Long,
    val email: String,
    val name: String? = null,
    val company: String? = null
)
