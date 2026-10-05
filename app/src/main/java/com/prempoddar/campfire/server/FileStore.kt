package com.prempoddar.campfire.server

import com.prempoddar.campfire.core.Attachment
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyAndClose
import io.ktor.util.cio.writeChannel
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** Files people send. They live in a scratch folder that is emptied when the server starts and stops. */
class FileStore(private val dir: File) {

    class NoSpaceException : Exception("Not enough free space on the host phone")

    private val meta = ConcurrentHashMap<String, Attachment>()
    private val random = SecureRandom()
    private val idChars = "abcdefghijkmnpqrstuvwxyz23456789"
    private val idPattern = Regex("[a-z2-9]{20}")

    fun wipe() {
        meta.clear()
        dir.deleteRecursively()
        dir.mkdirs()
    }

    /** Streams an upload to disk. [expectedSize] (when the client sends it) is checked against free space. */
    suspend fun save(input: ByteReadChannel, rawName: String, mime: String, expectedSize: Long?): Attachment {
        if (expectedSize != null && expectedSize > dir.usableSpace - RESERVE_BYTES) throw NoSpaceException()
        val id = newId()
        val file = File(dir, id)
        try {
            input.copyAndClose(file.writeChannel())
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        return register(id, rawName, mime, file.length())
    }

    fun saveBytes(bytes: ByteArray, rawName: String, mime: String): Attachment {
        val id = newId()
        File(dir, id).writeBytes(bytes)
        return register(id, rawName, mime, bytes.size.toLong())
    }

    fun file(id: String): Pair<File, Attachment>? {
        if (!idPattern.matches(id)) return null
        val a = meta[id] ?: return null
        val f = File(dir, id)
        return if (f.isFile) f to a else null
    }

    private fun register(id: String, rawName: String, mime: String, size: Long): Attachment {
        val a = Attachment(id, safeName(rawName), size, mime.ifBlank { "application/octet-stream" }.take(100))
        meta[id] = a
        return a
    }

    private fun newId() = buildString { repeat(20) { append(idChars[random.nextInt(idChars.length)]) } }

    companion object {
        private const val RESERVE_BYTES = 64L * 1024 * 1024

        fun safeName(raw: String): String {
            val base = raw.substringAfterLast('/').substringAfterLast('\\')
                .filterNot { it.isISOControl() || it in "<>:\"|?*" }
                .trim().trimStart('.')
            return base.take(120).ifBlank { "file" }
        }
    }
}
