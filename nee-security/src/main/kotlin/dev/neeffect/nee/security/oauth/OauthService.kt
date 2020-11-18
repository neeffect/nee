package dev.neeffect.nee.security.oauth

import dev.neeffect.nee.Nee
import dev.neeffect.nee.NoEffect
import dev.neeffect.nee.UNee
import dev.neeffect.nee.effects.Out
import dev.neeffect.nee.effects.security.SecurityErrorType
import dev.neeffect.nee.security.jwt.JWTError
import io.vavr.control.Either
import io.vavr.control.Option
import io.vavr.kotlin.some
import io.vavr.kotlin.toVavrMap

class OauthService<USER,ROLE>(private val oauthConfig: OauthConfigModule<USER,ROLE>)  {

    private val googleOpenId = GoogleOpenId(oauthConfig)

    fun login(code:String, state:String, oauthProvider: OauthProviders) : UNee<Any, SecurityErrorType, LoginResult> =
            findOauthProvider(oauthProvider).map {
                provider ->
                    println("$code + $state + $provider")
                provider.verifyOauthToken(code).map { oauthResponse->
                    println("validate idToken ${oauthResponse}")//TODO
                    val user = oauthConfig.userEncoder(oauthProvider,oauthResponse)
                    val jwt = oauthConfig.jwtUsersCoder.encodeUser(user)
                    val signedJwt = oauthConfig.jwtCoder.signJwt(jwt)
                    LoginResult(signedJwt, oauthResponse.displayName, oauthResponse.subject)
                }
            }.getOrElse {
                Nee.constWithError(NoEffect<Any,SecurityErrorType>() ) { _ ->
                    Out.left<SecurityErrorType, LoginResult>(SecurityErrorType.NoSecurityCtx)}
            }


    private fun findOauthProvider(oauthProvider: OauthProviders) = when (oauthProvider) {
        OauthProviders.Google -> some(googleOpenId)
    }

    fun decodeUser(jwtToken:String): Either<SecurityErrorType, USER> = oauthConfig.jwtCoder.decodeJwt(jwtToken).
        mapLeft<SecurityErrorType>{
           SecurityErrorType.MalformedCredentials(it.toString())
        }.flatMap {
        jwt ->

        oauthConfig.userCoder.mapToUser(jwt.subject, jwt.allClaims.toVavrMap().mapValues { it.toString() }).toEither {
            SecurityErrorType.UnknownUser
        }
    }

}

data class LoginResult(
    val encodedToken: String,
    val displayName:Option<String>,
    val subject: String)


