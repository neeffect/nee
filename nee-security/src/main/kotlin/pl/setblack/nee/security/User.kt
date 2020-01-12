package pl.setblack.nee.security

import io.vavr.kotlin.toVavrList
import io.vavr.collection.List
import java.util.UUID

data class User(
    val id: UUID,
    val login: String,
    val roles: List<UserRole>
)

data class UserRole(val roleName: String) {
    companion object {
        fun roles(vararg names: String): List<UserRole> = names.toVavrList()
            .map { UserRole(it) }
    }
}
