package pl.setblack.nee.security

import pl.setblack.nee.security.PBKDF2Hasher.HashParams.algorithm
import pl.setblack.nee.security.PBKDF2Hasher.HashParams.algorithmName
import pl.setblack.nee.security.PBKDF2Hasher.HashParams.iterationCount
import pl.setblack.nee.security.PBKDF2Hasher.HashParams.keyLength
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

typealias Salt = ByteArray

class PBKDF2Hasher {

    fun hashPassword(password: CharArray, salt: Salt): ByteArray =
        PBEKeySpec(password, salt, iterationCount, keyLength).let { keySpec ->
            algorithm.generateSecret(keySpec).encoded
        }

    object HashParams {
        const val algorithmName = "PBKDF2WithHmacSHA1"
        const val iterationCount = 32761
        const val keyLength = 128
        val algorithm = SecretKeyFactory.getInstance(algorithmName)
    }
}
