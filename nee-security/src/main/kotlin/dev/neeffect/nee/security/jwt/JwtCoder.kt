package dev.neeffect.nee.security.jwt

import dev.neeffect.nee.effects.time.HasteTimeProvider
import dev.neeffect.nee.effects.time.TimeProvider
import dev.neeffect.nee.security.User
import io.fusionauth.jwt.JWTDecoder
import io.fusionauth.jwt.Signer
import io.fusionauth.jwt.Verifier
import io.fusionauth.jwt.domain.JWT
import io.fusionauth.jwt.hmac.HMACSigner
import io.fusionauth.jwt.hmac.HMACVerifier
import java.time.Clock
import io.vavr.collection.Map
import io.vavr.collection.HashMap
import io.vavr.kotlin.toVavrMap

data class JwtConfig(
    val expirationInSeconds: Long,
    val issuer: String,
    val signerSecret: String
)


open class JwtCoderConfigurationModule(cfg: JwtConfig) {

    open val config: JwtConfig = cfg

    open val signer: Signer by lazy {
        HMACSigner.newSHA256Signer(config.signerSecret)
    }

    open val verifier: Verifier by lazy {
        HMACVerifier.newVerifier(config.signerSecret)
    }

    open val timeProvider: TimeProvider by lazy {
        HasteTimeProvider()
    }
}

open class JwtConfigurationModule(cfg: JwtConfig) : JwtCoderConfigurationModule(cfg) {
    open val jwtCoder: JwtCoder by lazy {
        JwtCoder(this)
    }
}

class JwtCoder(private val jwtBaseConfig: JwtCoderConfigurationModule) {
    fun createJwt(subject: String, claims: Map<String, String> = HashMap.empty()): JWT =
        jwtBaseConfig.timeProvider.getTimeSource().now().let { now ->
            JWT().setIssuer(jwtBaseConfig.config.issuer)
                .setIssuedAt(now)
                .setSubject(subject)
                .setExpiration(now.plusSeconds(jwtBaseConfig.config.expirationInSeconds))
                .also { jwt ->
                    claims.forEach { name, value -> jwt.addClaim(name, value) }
                }
        }

    fun signJwt(jwt: JWT) = JWT.getEncoder().encode(jwt, jwtBaseConfig.signer)

    fun decodeJwt(signed: String): JWT = jwtBaseConfig.timeProvider.getTimeSource().now().let { time ->
        val decoder = JWTDecoder(Clock.fixed(time.toInstant(), time.zone))
        decoder.decode(signed, jwtBaseConfig.verifier)
    }
}


