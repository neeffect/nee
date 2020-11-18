package dev.neeffect.nee.security.oauth

import dev.neeffect.nee.security.User
import dev.neeffect.nee.security.UserRole
import dev.neeffect.nee.security.jwt.SimpleUserCoder
import dev.neeffect.nee.security.jwt.JwtCoder
import dev.neeffect.nee.security.jwt.JwtConfig
import dev.neeffect.nee.security.jwt.JwtConfigurationModule
import dev.neeffect.nee.security.jwt.JwtUsersCoder
import dev.neeffect.nee.security.jwt.UserCoder
import dev.neeffect.nee.security.state.ServerVerifier
import io.ktor.client.HttpClient
import io.vavr.collection.Seq
import io.vavr.kotlin.list
import java.security.KeyPair
import java.security.SecureRandom
import java.util.*

abstract class OauthConfigModule<USER, ROLE>(
    val config: OauthConfig,
    val jwtConfig: JwtConfig
) {

    open val rng: Random by lazy {
        SecureRandom()
    }

    open val keyPair: KeyPair by lazy {
        ServerVerifier.generateKeyPair()
    }

    open val serverVerifier: ServerVerifier by lazy {
        ServerVerifier(rng = rng, keyPair = keyPair)
    }
    open val httpClient by lazy {
        HttpClient()
    }

    open val jwtConfigModule: JwtConfigurationModule by lazy {
        JwtConfigurationModule(jwtConfig)
    }

    open val jwtCoder: JwtCoder by lazy {
        jwtConfigModule.jwtCoder
    }

    open val jwtUsersCoder : JwtUsersCoder<USER, ROLE> by lazy {
        JwtUsersCoder(jwtConfigModule, userCoder)
    }

    abstract val userCoder: UserCoder<USER, ROLE>

    abstract val userEncoder:(OauthProviders, OauthResponse) -> USER

    abstract val userRoles: (OauthProviders, OauthResponse) -> Seq<ROLE>

}


open class SimpleOauthConfigModule(
    config: OauthConfig,
    jwtConfig: JwtConfig
) : OauthConfigModule<User, UserRole>(config, jwtConfig) {
    override val userCoder: UserCoder<User, UserRole> = SimpleUserCoder()
    override val userEncoder: (OauthProviders, OauthResponse) -> User = {
        provider, oauthResponse ->
        val uuid = UUID(rng.nextLong(), rng.nextLong())
        val roles = userRoles(provider, oauthResponse)

        User(uuid,
            "${provider.providerName}:${oauthResponse.subject}",
            roles.toList(),
            oauthResponse.displayName.getOrElse(oauthResponse.subject))
    }
    override val userRoles: (OauthProviders, OauthResponse) -> Seq<UserRole> = {_,_->
        list(oauthUser)
    }

     val oauthUser = UserRole("oauthUser")
}
