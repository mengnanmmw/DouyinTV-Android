package com.douyintv.app.api

import java.security.MessageDigest

/**
 * Pure Kotlin X-Bogus signer for Douyin Web API requests (no WebView / JS engine needed).
 */
object XBogus {
    private const val B64_ALPHABET =
        "Dkdpgh4ZKsQB80/Mfvw36XI1R25-WUAlEi7NLboqYTOPuzmFjJnryx9HVGcaStCe"

    private val UA_BYTES = byteArrayOf(
        0x00, 0x01, 0x0e
    )

    fun sign(queryString: String, userAgent: String): String {
        val md5Query = md5(md5(queryString.toByteArray(Charsets.UTF_8)))
        val md5Ua = md5(rc4(UA_BYTES, userAgent.toByteArray(Charsets.UTF_8)).let {
            customBase64(it).toByteArray(Charsets.UTF_8)
        })
        val md5Empty = md5(md5(ByteArray(0)))

        val nowSec = (System.currentTimeMillis() / 1000L).toInt()
        val salt = 0x1E7B42

        val raw = ByteArray(19)
        raw[0] = 0x40
        raw[1] = UA_BYTES[0]
        raw[2] = UA_BYTES[1]
        raw[3] = UA_BYTES[2]
        raw[4] = md5Query[14]
        raw[5] = md5Query[15]
        raw[6] = md5Ua[14]
        raw[7] = md5Ua[15]
        raw[8] = md5Empty[14]
        raw[9] = md5Empty[15]
        raw[10] = ((nowSec ushr 24) and 0xFF).toByte()
        raw[11] = ((nowSec ushr 16) and 0xFF).toByte()
        raw[12] = ((nowSec ushr 8) and 0xFF).toByte()
        raw[13] = (nowSec and 0xFF).toByte()
        raw[14] = ((salt ushr 24) and 0xFF).toByte()
        raw[15] = ((salt ushr 16) and 0xFF).toByte()
        raw[16] = ((salt ushr 8) and 0xFF).toByte()
        raw[17] = (salt and 0xFF).toByte()

        var xorCheck = 0
        for (i in 0 until 18) {
            xorCheck = xorCheck xor (raw[i].toInt() and 0xFF)
        }
        raw[18] = (xorCheck and 0xFF).toByte()

        val encrypted = rc4(byteArrayOf(0xFF.toByte()), raw)
        val payload = ByteArray(21)
        payload[0] = 0x02
        payload[1] = 0xFF.toByte()
        System.arraycopy(encrypted, 0, payload, 2, 19)

        return customBase64(payload)
    }

    private fun md5(input: ByteArray): ByteArray {
        return MessageDigest.getInstance("MD5").digest(input)
    }

    private fun rc4(key: ByteArray, data: ByteArray): ByteArray {
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            val tmp = s[i]
            s[i] = s[j]
            s[j] = tmp
        }
        val out = ByteArray(data.size)
        var i = 0
        j = 0
        for (k in data.indices) {
            i = (i + 1) and 0xFF
            j = (j + s[i]) and 0xFF
            val tmp = s[i]
            s[i] = s[j]
            s[j] = tmp
            val t = (s[i] + s[j]) and 0xFF
            out[k] = ((data[k].toInt() and 0xFF) xor s[t]).toByte()
        }
        return out
    }

    private fun customBase64(data: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0

            val c0 = (b0 ushr 2) and 0x3F
            val c1 = ((b0 shl 4) or (b1 ushr 4)) and 0x3F
            val c2 = ((b1 shl 2) or (b2 ushr 6)) and 0x3F
            val c3 = b2 and 0x3F

            sb.append(B64_ALPHABET[c0])
            sb.append(B64_ALPHABET[c1])
            if (i + 1 < data.size) sb.append(B64_ALPHABET[c2]) else sb.append('=')
            if (i + 2 < data.size) sb.append(B64_ALPHABET[c3]) else sb.append('=')
            i += 3
        }
        return sb.toString()
    }
}
