package dev.neeffect.nee.security.oauth

import dev.neeffect.nee.Nee
import dev.neeffect.nee.NoEffect
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.security.SecurityErrorType
import dev.neeffect.nee.security.oauth.config.GithubOAuth
import io.vavr.control.Either
import io.vavr.control.Option
import io.vavr.kotlin.some
import io.vavr.kotlin.toVavrMap

class OauthService<USER, ROLE>(private val oauthConfig: OauthConfigModule<USER, ROLE>) {

    private val googleOpenId = GoogleOpenId(oauthConfig)
    private val githubOAuth = GithubOAuth(oauthConfig)

    fun login(
        code: String,
        state: String,
        redirectUri: String,
        oauthProvider: OauthProviderName
    ): Nee<Any, SecurityErrorType, LoginResult> =
        findOauthProvider(oauthProvider).map { provider ->
            if (oauthConfig.serverVerifier.verifySignedText(state)) {
                provider.verifyOauthToken(code, redirectUri, state).map { oauthResponse ->
                    println("validate idToken $oauthResponse") // TODO
                    val user = oauthConfig.userEncoder(oauthProvider, oauthResponse)
                    val jwt = oauthConfig.jwtConfigModule.jwtUsersCoder.encodeUser(user)
                    val signedJwt = oauthConfig.jwtConfigModule.jwtCoder.signJwt(jwt)
                    LoginResult(signedJwt, oauthResponse.displayName, oauthResponse.subject)
                }
            } else {
                Nee.constWithError(NoEffect<Any, SecurityErrorType>()) { _ ->
                    Out.left(SecurityErrorType.MalformedCredentials("state unrecognized: $state"))
                }
            }
        }.getOrElse {
            Nee.constWithError(NoEffect()) { _ ->
                Out.left(SecurityErrorType.NoSecurityCtx)
            }
        }

    fun generateApiCall(oauthProvider: OauthProviderName, redirectUrl: String): Option<String> =
        findOauthProvider(oauthProvider).map {
            it.generateApiCall(redirectUrl)
        }

    private fun findOauthProvider(oauthProvider: OauthProviderName): Option<OauthProvider> = when (oauthProvider) {
        OauthProviderName.Google -> some(googleOpenId)
        OauthProviderName.Github -> some(githubOAuth)
    }

    fun decodeUser(jwtToken: String): Either<SecurityErrorType, USER> =
        oauthConfig.jwtConfigModule.jwtCoder.decodeJwt(jwtToken).mapLeft<SecurityErrorType> {
            SecurityErrorType.MalformedCredentials(it.toString())
        }.flatMap { jwt ->

            oauthConfig.userCoder.mapToUser(jwt.subject, jwt.allClaims.toVavrMap().mapValues { it.toString() })
                .toEither {
                    SecurityErrorType.UnknownUser
                }
        }
}

data class LoginResult(
    val encodedToken: String,
    val displayName: Option<String>,
    val subject: String
)
