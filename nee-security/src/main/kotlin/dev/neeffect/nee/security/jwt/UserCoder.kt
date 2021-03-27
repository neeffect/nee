package dev.neeffect.nee.security.jwt

import io.fusionauth.jwt.domain.JWT
import io.vavr.collection.Map
import io.vavr.control.Option
import io.vavr.kotlin.toVavrMap

interface UserCoder<USER, ROLE> {
    fun userToIdAndMapAnd(u: USER): Pair<String, Map<String, String>>
    fun mapToUser(id: String, m: Map<String, String>): Option<USER>
    fun hasRole(u: USER, r: ROLE): Boolean
}

class JwtUsersCoder<USER, ROLE>(val jwtCoder: JwtCoder, val coder: UserCoder<USER, ROLE>) {
    fun encodeUser(user: USER): JWT = coder.userToIdAndMapAnd(user).let { (id, mapClaims) ->
        jwtCoder.createJwt(id, mapClaims)
    }

    @Suppress("MutableCollections")
    fun decodeUser(jwt: JWT): Option<USER> =
        coder.mapToUser(jwt.subject, jwt.allClaims.toVavrMap().mapValues { it.toString() })

    fun hasRole(u: USER, r: ROLE): Boolean = coder.hasRole(u, r)
}
