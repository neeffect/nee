package dev.neeffect.nee.security.oauth.config

import dev.neeffect.nee.security.UserRole
import dev.neeffect.nee.security.jwt.JwtConfig
import dev.neeffect.nee.security.oauth.OauthConfig
import dev.neeffect.nee.security.oauth.OauthProviderName
import dev.neeffect.nee.security.oauth.OauthResponse
import dev.neeffect.nee.security.oauth.OauthService
import dev.neeffect.nee.security.oauth.SimpleOauthConfigModule
import io.vavr.collection.Seq

class OauthModule(
    oathConfig: OauthConfig,
    jwtConfig: JwtConfig,
    private val rolesMapper: RolesMapper
) : SimpleOauthConfigModule(oathConfig, jwtConfig) {

    val oauthService by lazy {
        OauthService(this)
    }

    override val userRoles: (OauthProviderName, OauthResponse) -> Seq<UserRole> = rolesMapper
}
