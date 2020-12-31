package dev.neeffect.nee.effects.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Marker interface for diagnostic log support.
 */
interface Logging

/**
 * Use it to log using slf4j.
 */
inline fun <reified T : Logging> T.logger(): Logger =
    LoggerFactory.getLogger(T::class.java)


