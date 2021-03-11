package dev.neeffect.nee.security.jwt

import dev.neeffect.nee.security.User
import dev.neeffect.nee.security.UserRole
import io.vavr.collection.Map
import io.vavr.control.Option
import io.vavr.control.Try
import io.vavr.kotlin.hashMap
import io.vavr.kotlin.toVavrList
import java.util.UUID

class SimpleUserCoder : UserCoder<User, UserRole> {
    override fun userToIdAndMapAnd(u: User): Pair<String, Map<String, String>> =
        u.id.toString() to hashMap(
            loginKey to u.login,
            "id" to u.id.toString(),
            displayNameKey to u.displayName,
            rolesKey to u.roles.map { it.roleName }.joinToString(",")
        )

    override fun mapToUser(id: String, m: Map<String, String>): Option<User> =
        stringToUUID(id).flatMap { uuid ->
            m[loginKey].flatMap { login ->
                m[displayNameKey].flatMap { displayName ->
                    m[rolesKey].map { rolesString ->
                        val roles =
                            rolesString.split(",").toVavrList().map(::UserRole)
                        User(uuid, login, roles, displayName)
                    }
                }
            }
        }

    override fun hasRole(u: User, r: UserRole): Boolean = u.roles.contains(r)

    fun stringToUUID(id: String) = Try.of { UUID.fromString(id) }.toOption()

    companion object {
        const val loginKey = "login"
        const val rolesKey = "roles"
        const val displayNameKey = "displayName"
    }
}
