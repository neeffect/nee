package pl.setblack.nee.security

import io.vavr.control.Option
import io.vavr.control.Try
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream

interface UserRealm<USERID, ROLE> {
    fun loginUser(userLogin: String, password: CharArray): Option<USERID>

    fun hasRole(user: USERID, role: ROLE): Boolean
}


fun aTest1(elements: Array<Any>): Try<ByteArray> =
    Try.withResources { ByteArrayOutputStream() }
        .of { output ->
            Try.withResources { ObjectOutputStream(output) }
                .of { someOutput ->
                    someOutput.writeObject(elements)
                    output.toByteArray()
                }
        }.flatMap { it }

fun aTest2(elements: Array<Any>): Try<ByteArray> = Try.of {
    ByteArrayOutputStream().use { output ->
        ObjectOutputStream(output).use { someOutput ->
            someOutput.writeObject(elements)
            output.toByteArray()
        }
    }
}
