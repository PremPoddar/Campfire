package com.prempoddar.campfire.core

import java.security.SecureRandom

interface IdSource {
    fun memberId(): String
    fun token(): String
    fun roomId(): String
}

/** Random ids. Tokens are long enough to be unguessable; other ids only need to be unique. */
class RandomIds(private val random: SecureRandom = SecureRandom()) : IdSource {
    private val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
    private fun id(length: Int) = buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }

    override fun memberId() = id(10)
    override fun token() = id(32)
    override fun roomId() = id(8)
}
