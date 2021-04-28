package dev.neeffect.nee.security.jwt

import io.fusionauth.jwt.InvalidJWTSignatureException
import io.fusionauth.jwt.Verifier
import io.fusionauth.jwt.domain.Algorithm
import io.vavr.collection.Seq

class MultiVerifier(private val verifiers: Seq<Verifier>) : Verifier {
    override fun canVerify(algorithm: Algorithm?): Boolean = verifiers.any { it.canVerify(algorithm) }

    @Suppress("ReturnUnit")
    override fun verify(algorithm: Algorithm?, message: ByteArray?, signature: ByteArray?): Unit =
        verifiers.filter { it.canVerify(algorithm) }.find { verifier ->
            try {
                verifier.verify(algorithm, message, signature)
                true
            } catch (e: InvalidJWTSignatureException) {
                false
            }
        }.map { Unit }.getOrElseThrow { InvalidJWTSignatureException() }
}
