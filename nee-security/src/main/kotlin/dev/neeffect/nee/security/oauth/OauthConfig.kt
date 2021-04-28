package dev.neeffect.nee.security.oauth

import io.vavr.collection.HashMap
import io.vavr.collection.Map
import io.vavr.control.Option

data class OauthConfig(
    val providers: Map<String, ProviderConfig> = HashMap.empty()
) {
    fun getProviderConfig(ouathProviderName: OauthProviderName): Option<ProviderConfig> =
        providers[ouathProviderName.providerName]

    fun getClientId(ouathProviderName: OauthProviderName) =
        getProviderConfig(ouathProviderName).map { it.clientId }.getOrElseThrow {
            IllegalStateException("unconfigured provider: $ouathProviderName")
        }

    fun getClientSecret(ouathProviderName: OauthProviderName) =
        getProviderConfig(ouathProviderName).map { it.clientSecret }.getOrElseThrow {
            IllegalStateException("unconfigured provider: $ouathProviderName")
        }
}

data class ProviderConfig(
    val clientId: String,
    val clientSecret: String,
    val certificatesFile: Option<String> = Option.none()
)
