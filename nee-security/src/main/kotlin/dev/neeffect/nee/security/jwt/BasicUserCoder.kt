package dev.neeffect.nee.security.jwt

import dev.neeffect.nee.security.User
import dev.neeffect.nee.security.UserRole
import io.vavr.collection.Map
import io.vavr.control.Option
import io.vavr.control.Try
import io.vavr.kotlin.hashMap
import io.vavr.kotlin.toVavrList
import java.util.*

class BasicUserCoder : UserCoder<User, UserRole>{
    override fun userToIdAndMapAnd(u: User): Pair<String, Map<String, String>> =
       u.id.toString() to  hashMap(
           loginKey to u.login,
           "id" to u.id.toString(),
            rolesKey to u.roles.map { it.roleName }.joinToString(",")  )

    override fun mapToUser(id: String, m: Map<String, String>): Option<User> =
        stringToUUID(id).flatMap {uuid ->
            m[loginKey].flatMap {login ->
                m[rolesKey].map { rolesString ->
                    val roles =
                        rolesString.split(",").toVavrList().map(::UserRole)
                    User(uuid,login, roles)
                }
            }
        }

    override fun hasRole(r: UserRole, m: Map<String, String>): Boolean {
        TODO("Not yet implemented")
    }

    fun stringToUUID(id:String) = Try.of {UUID.fromString(id)}.toOption()
    companion object {
        const val loginKey = "login"
        const val rolesKey  = "roles"
    }
}
