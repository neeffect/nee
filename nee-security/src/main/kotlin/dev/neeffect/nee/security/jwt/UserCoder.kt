package dev.neeffect.nee.security.jwt

import io.fusionauth.jwt.domain.JWT
import io.vavr.collection.Map
import io.vavr.control.Option
import io.vavr.kotlin.toVavrMap

interface UserCoder<USER, ROLE> {
    fun userToIdAndMapAnd(u: USER): Pair<String, Map<String, String>>
    fun mapToUser(id:String, m: Map<String, String>): Option<USER>
    fun hasRole(r: ROLE, m: Map<String, String>): Boolean
}

class JwtUsersCoder<USER, ROLE>(val config: JwtConfigurationModule, val coder: UserCoder<USER, ROLE>) {
    fun encodeUser(user: USER): JWT = coder.userToIdAndMapAnd(user).let {
            (id,mapClaims) ->
        config.jwtCoder.createJwt(id, mapClaims)
    }

    fun decodeUser(jwt: JWT) = coder.mapToUser(jwt.subject, jwt.allClaims.toVavrMap().mapValues { it.toString() })
}
