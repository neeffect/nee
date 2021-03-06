package dev.neeffect.nee.security.oauth

import io.vavr.control.Option
import io.vavr.kotlin.option

enum class OauthProviderName(val providerName: String) {
    Google("google"),
    Github("github");

    fun getByName(name: String): Option<OauthProviderName> = OauthProviderName.values().find {
        name == it.providerName
    }.option()
}
