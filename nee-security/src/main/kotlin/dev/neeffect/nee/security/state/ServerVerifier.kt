package dev.neeffect.nee.security.state

import io.vavr.control.Option
import io.vavr.control.Try
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.util.Base64
import java.util.Random

/**
 * Utility to use - for CSRF and similar
 */
class ServerVerifier(
    private val rng: Random = SecureRandom(),
    private val keyPair: KeyPair = generateKeyPair()
) {

    fun generateRandomSignedState(): String =
        ByteArray(randomStateContentLength).let { rndArray ->
            rng.nextBytes(rndArray)
            val encoded = Base64.getEncoder().encodeToString(rndArray)
            val signature = signArray(rndArray)
            encoded + "@" + signature
        }

    fun verifySignedText(text: String) =
        text.split("@").let { splitted ->
            when (splitted.size) {
                2 -> verifyText(splitted[0], splitted[1]).getOrElse(false)
                else -> false
            }
        }

    private fun signArray(data: ByteArray): String =
        Signature.getInstance("SHA1WithRSA").let { sig ->
            sig.initSign(keyPair.private)
            sig.update(data)
            Base64.getEncoder().encodeToString(sig.sign())
        }

    private fun verifyText(base64Text: String, signature: String): Try<Boolean> = Try.of {
        val sig: Signature = Signature.getInstance("SHA1WithRSA")
        sig.initVerify(keyPair.public)
        val data = Base64.getDecoder().decode(base64Text)
        sig.update(data)
        val signatureBytes = Base64.getDecoder().decode(signature)
        sig.verify(signatureBytes)
    }

    companion object {
        const val randomStateContentLength = 16
        private const val keySize = 1024
        fun generateKeyPair(): KeyPair = with(
            KeyPairGenerator.getInstance("RSA").apply {
                initialize(keySize)
            }) {
            genKeyPair()
        }

        fun loadKeyPair(path: Path): Option<KeyPair> = Try.of {
            Files.newInputStream(path).use {
                loadKeyPair(it)
            }
        }.toOption().flatMap { it }

        fun loadKeyPair(inputStream: InputStream): Option<KeyPair> = Try.of {
            ObjectInputStream(inputStream).use { objectStream ->
                objectStream.readObject() as KeyPair
            }
        }.toOption()
    }
}
