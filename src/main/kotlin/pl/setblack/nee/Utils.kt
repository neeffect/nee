package pl.setblack.nee

import io.vavr.control.Either

fun <T> Either<T, T>.merge() = getOrElseGet { it }