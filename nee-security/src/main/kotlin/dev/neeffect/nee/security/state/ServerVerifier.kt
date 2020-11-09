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
import java.util.*

/**
 * Utility to use - for CSRF and similar
 */
class ServerVerifier(
    private val rng: Random = SecureRandom(),
    private val keyPair: KeyPair = generateKeyPair()
) {

    //TODO should be cluster const


    fun generateRandomSignedState(): String = ByteArray(16).let { rndArray ->

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

    private fun signArray(data: ByteArray): String {
        val sig: Signature = Signature.getInstance("SHA1WithRSA")
        sig.initSign(keyPair.private)
        sig.update(data)
        val signatureBytes: ByteArray = sig.sign()
        return Base64.getEncoder().encodeToString(signatureBytes)
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
        fun generateKeyPair(): KeyPair {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(1024)
            return kpg.genKeyPair()
        }

        fun loadKeyPair(path: Path): Option<KeyPair> = Try.of {
            Files.newInputStream(path).use {
                loadKeyPair(it)
            }
        }.toOption().flatMap {it}

        fun loadKeyPair(inputStream: InputStream): Option<KeyPair> = Try.of {
                ObjectInputStream(inputStream).use { objectStream ->
                    objectStream.readObject() as KeyPair
                }
        }.toOption()
    }
}

fun main() {
    val keyPair = ServerVerifier.generateKeyPair()
    val testKeyPath = Paths.get("tmp/testServerKey.bin")
    Files.newOutputStream(testKeyPath).use {
        ObjectOutputStream(it).use { objectOut ->
            objectOut.writeObject(keyPair)
        }
    }
}

