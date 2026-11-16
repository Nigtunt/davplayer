package com.davplayer.app.ios.data

import kotlin.random.Random

/**
 * 隐私暗码：只保存加盐 SHA-256 摘要，不在设置页/代码文案中保存明文。
 * 未设置过暗码的老用户继续兼容旧默认暗码（只比较摘要）。
 */
object PrivacyPin {
    private const val ITERATIONS = 4096
    private const val HEX = "0123456789abcdef"
    private const val LEGACY_SALT = "davplayer-legacy-pin-v1"
    private const val LEGACY_HASH =
        "7bbbb5eeffc6a1c7769089b9657a0e70efc300be4b3bd3d0ab8e99299f3ab130"
    private const val OLD_LEGACY_HASH =
        "d929db26592328c6c9a581f6149b27dadc3dd5649b802049b8e1b89213dbb907"

    fun hasPin(): Boolean =
        !readSetting("privacyPinHash").isNullOrBlank()

    fun setPin(pin: String): Boolean {
        if (!pin.matches(Regex("^[1-9]\\d{5,11}$"))) return false
        val saltBytes = ByteArray(16).also { Random.nextBytes(it) }
        val salt = saltBytes.joinToString("") { byteHex(it) }
        writeSetting("privacyPinSalt", salt)
        writeSetting("privacyPinHash", derive(pin, salt, ITERATIONS))
        return true
    }

    fun verify(pin: String): Boolean {
        if (!pin.matches(Regex("^[1-9]\\d{5,11}$"))) return false
        val stored = readSetting("privacyPinHash")
        if (!stored.isNullOrBlank()) {
            val salt = readSetting("privacyPinSalt") ?: return false
            return constantTimeEquals(stored, derive(pin, salt, ITERATIONS))
        }
        val derived = derive(pin, LEGACY_SALT, ITERATIONS)
        return constantTimeEquals(LEGACY_HASH, derived) ||
            constantTimeEquals(OLD_LEGACY_HASH, derived)
    }

    private fun derive(pin: String, salt: String, iterations: Int): String {
        var digest = sha256("$salt:$pin".encodeToByteArray())
        repeat(iterations - 1) {
            digest = sha256(digest)
        }
        return digest.joinToString("") { byteHex(it) }
    }

    private fun byteHex(value: Byte): String {
        val v = value.toInt() and 0xFF
        return HEX[v ushr 4].toString() + HEX[v and 0x0F]
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private val K = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b,
        0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039,
        -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d,
        -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8,
        -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e
    )

    private fun sha256(input: ByteArray): ByteArray {
        val h = intArrayOf(
            0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6,
            0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19
        )
        val bitLength = input.size.toLong() * 8L
        val paddedSize = ((input.size + 9 + 63) / 64) * 64
        val msg = ByteArray(paddedSize)
        input.copyInto(msg)
        msg[input.size] = 0x80.toByte()
        for (i in 0 until 8) {
            msg[msg.size - 1 - i] = ((bitLength ushr (8 * i)) and 0xFF).toByte()
        }

        val w = IntArray(64)
        var offset = 0
        while (offset < msg.size) {
            for (i in 0 until 16) {
                val j = offset + i * 4
                w[i] = ((msg[j].toInt() and 0xFF) shl 24) or
                    ((msg[j + 1].toInt() and 0xFF) shl 16) or
                    ((msg[j + 2].toInt() and 0xFF) shl 8) or
                    (msg[j + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val s0 = rotr(w[i - 15], 7) xor rotr(w[i - 15], 18) xor (w[i - 15] ushr 3)
                val s1 = rotr(w[i - 2], 17) xor rotr(w[i - 2], 19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }

            var a = h[0]
            var b = h[1]
            var c = h[2]
            var d = h[3]
            var e = h[4]
            var f = h[5]
            var g = h[6]
            var hh = h[7]

            for (i in 0 until 64) {
                val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = hh + s1 + ch + K[i] + w[i]
                val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj
                hh = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }

            h[0] += a
            h[1] += b
            h[2] += c
            h[3] += d
            h[4] += e
            h[5] += f
            h[6] += g
            h[7] += hh
            offset += 64
        }

        val out = ByteArray(32)
        for (i in 0 until 8) {
            out[i * 4] = (h[i] ushr 24).toByte()
            out[i * 4 + 1] = (h[i] ushr 16).toByte()
            out[i * 4 + 2] = (h[i] ushr 8).toByte()
            out[i * 4 + 3] = h[i].toByte()
        }
        return out
    }

    private fun rotr(x: Int, n: Int): Int = (x ushr n) or (x shl (32 - n))
}
