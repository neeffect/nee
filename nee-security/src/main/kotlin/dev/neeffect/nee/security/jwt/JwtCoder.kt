package dev.neeffect.nee.security.jwt

import dev.neeffect.nee.effects.time.HasteTimeProvider
import dev.neeffect.nee.effects.time.TimeProvider
import io.fusionauth.jwt.Signer
import io.fusionauth.jwt.Verifier
import io.fusionauth.jwt.domain.JWT
import io.fusionauth.jwt.hmac.HMACSigner
import io.fusionauth.jwt.hmac.HMACVerifier
import io.vavr.collection.HashMap
import io.vavr.collection.Map
import io.vavr.control.Either
import io.vavr.control.Try

data class JwtConfig(
    val expirationInSeconds: Long = 1000,
    val issuer: String,
    val signerSecret: String
)

open class JwtCoderConfigurationModule(
    cfg: JwtConfig,
    val timeProvider: TimeProvider = HasteTimeProvider()
) {

    open val config: JwtConfig = cfg

    open val signer: Signer by lazy {
        HMACSigner.newSHA256Signer(config.signerSecret)
    }

    open val verifier: Verifier by lazy {
        HMACVerifier.newVerifier(config.signerSecret)
    }
}

abstract class JwtConfigurationModule<USER, ROLE>(
    cfg: JwtConfig,
    timeProvider: TimeProvider
) : JwtCoderConfigurationModule(cfg, timeProvider) {
    open val jwtCoder: JwtCoder by lazy {
        JwtCoder(this)
    }
    open val jwtUsersCoder: JwtUsersCoder<USER, ROLE> by lazy {
        JwtUsersCoder(jwtCoder, userCoder)
    }

    abstract val userCoder: UserCoder<USER, ROLE>
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

    fun decodeJwt(signed: String): Either<JWTError, JWT> = Try.of {
        jwtBaseConfig.timeProvider.getTimeSource().now().let { time ->
            val decoder = JWT.getTimeMachineDecoder(time)
            decoder.decode(signed, jwtBaseConfig.verifier)
        }
    }.toEither().mapLeft { e -> JWTError.WrongJWT(e) }
}

sealed class JWTError {
    class WrongJWT(val cause: Throwable) : JWTError()
}
