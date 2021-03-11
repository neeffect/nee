package dev.neeffect.nee.security.oauth

import dev.neeffect.nee.effects.time.HasteTimeProvider
import dev.neeffect.nee.effects.time.TimeProvider
import dev.neeffect.nee.security.User
import dev.neeffect.nee.security.UserRole
import dev.neeffect.nee.security.jwt.JwtConfig
import dev.neeffect.nee.security.jwt.JwtConfigurationModule
import dev.neeffect.nee.security.jwt.SimpleUserCoder
import dev.neeffect.nee.security.jwt.UserCoder
import dev.neeffect.nee.security.state.ServerVerifier
import io.ktor.client.HttpClient
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.vavr.collection.Seq
import io.vavr.kotlin.list
import java.security.KeyPair
import java.security.SecureRandom
import java.util.Random
import java.util.UUID

abstract class OauthConfigModule<USER, ROLE>(
    val config: OauthConfig,
    val jwtConfig: JwtConfig
) {

    open val randomGenerator: Random by lazy {
        SecureRandom()
    }
    open val baseTimeProvider: TimeProvider by lazy {
        HasteTimeProvider()
    }

    open val keyPair: KeyPair by lazy {
        ServerVerifier.generateKeyPair()
    }

    open val serverVerifier: ServerVerifier by lazy {
        ServerVerifier(rng = this.randomGenerator, keyPair = keyPair)
    }
    open val httpClient by lazy {
        HttpClient() {
            install(JsonFeature) { // TODO - move it so that it is tested (it was not)
                serializer = JacksonSerializer()
            }
        }
    }

    open val jwtConfigModule: JwtConfigurationModule<USER, ROLE> by lazy {

        object : JwtConfigurationModule<USER, ROLE>(jwtConfig, baseTimeProvider) {
            override val userCoder: UserCoder<USER, ROLE> = this@OauthConfigModule.userCoder
        }
    }

    abstract val userEncoder: (OauthProviderName, OauthResponse) -> USER

    abstract val userRoles: (OauthProviderName, OauthResponse) -> Seq<ROLE>

    abstract val userCoder: UserCoder<USER, ROLE>
}

open class SimpleOauthConfigModule(
    config: OauthConfig,
    jwtConfig: JwtConfig
) : OauthConfigModule<User, UserRole>(config, jwtConfig) {
    override val userCoder: UserCoder<User, UserRole> = SimpleUserCoder()
    override val userEncoder: (OauthProviderName, OauthResponse) -> User = { provider, oauthResponse ->
        val uuid = UUID(randomGenerator.nextLong(), randomGenerator.nextLong())
        val roles = userRoles(provider, oauthResponse)

        User(
            uuid,
            "${provider.providerName}:${oauthResponse.subject}",
            roles.toList(),
            oauthResponse.displayName.getOrElse(oauthResponse.subject)
        )
    }
    override val userRoles: (OauthProviderName, OauthResponse) -> Seq<UserRole> = { _, _ ->
        list(oauthUser)
    }

    val oauthUser = UserRole("oauthUser")
}
