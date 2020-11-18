package dev.neeffect.nee.security.oauth

import dev.neeffect.nee.Nee
import dev.neeffect.nee.NoEffect
import dev.neeffect.nee.UNee
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.security.SecurityErrorType
import io.vavr.control.Option
import io.vavr.kotlin.some

class OauthService(private val oauthConfig: OauthConfigModule)  {

    private val googleOpenId = GoogleOpenId(oauthConfig)

    fun login(code:String, state:String, oauthProvider: String) : UNee<Any, SecurityErrorType, LoginResult> =
            findOauthProvider(oauthProvider).map {
                provider ->
                    println("$code + $state + $provider")
                provider.verifyOauthToken(code).map { oauthResponse->
                    println("validate idToken ${oauthResponse}")
                    val jwt = oauthConfig.jwtCoder.createJwt(oauthResponse.subject)
                    val signedJwt = oauthConfig.jwtCoder.signJwt(jwt)
                    LoginResult(signedJwt, oauthResponse.displayName, "todo")
                }
            }.getOrElse {
                Nee.constWithError(NoEffect<Any,SecurityErrorType>() ) { _ ->
                    Out.left<SecurityErrorType, LoginResult>(SecurityErrorType.NoSecurityCtx)}
            }


    private fun findOauthProvider(oauthProvider: String) = when (oauthProvider) {
        OauthProviders.Google.providerName -> some(googleOpenId)
        else -> Option.none()
    }

}

data class LoginResult(
    val encodedToken: String,
    val displayName:Option<String>,
    val subject: String)
