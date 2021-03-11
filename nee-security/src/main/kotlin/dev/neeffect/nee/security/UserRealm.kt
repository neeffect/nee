package dev.neeffect.nee.security

import io.vavr.collection.HashMap
import io.vavr.collection.HashSet
import io.vavr.collection.Set
import io.vavr.control.Option

interface UserRealm<USERID, ROLE> {
    fun loginUser(userLogin: String, password: CharArray): Option<USERID>

    fun hasRole(user: USERID, role: ROLE): Boolean
}

data class InMemoryUserRealm<USERID, ROLE>(
    val rolesMap: HashMap<String, Set<ROLE>> = HashMap.empty(),
    val passwords: HashMap<USERID, CharArray> = HashMap.empty(),
    val userIdMapper: (USERID) -> String = { it.toString() }
) : UserRealm<USERID, ROLE> {
    fun withRole(userid: USERID, role: ROLE) =
        this.copy(rolesMap = rolesMap.put(userIdMapper(userid), HashSet.of(role)) { _, preVal ->
            preVal.add(role)
        })

    fun withPassword(userid: USERID, password: CharArray) =
        this.copy(passwords = passwords.put(userid, password))

    override fun loginUser(userLogin: String, password: CharArray): Option<USERID> =
        passwords.iterator().find {
            userIdMapper(it._1) == userLogin
        }.filter {
            it._2.contentEquals(password)
        }.map {
            it._1
        }

    override fun hasRole(user: USERID, role: ROLE): Boolean =
        rolesMap[userIdMapper(user)].map { roles ->
            roles.contains(role)
        }.getOrElse(false)
}
