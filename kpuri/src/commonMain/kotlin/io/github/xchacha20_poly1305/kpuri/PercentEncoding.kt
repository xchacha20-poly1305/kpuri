package io.github.xchacha20_poly1305.kpuri

/** A set of ASCII characters stored as a 128-bit mask. Non-ASCII characters are never members. */
internal class AsciiSet private constructor(private val low: Long, private val high: Long) {
    operator fun contains(c: Char): Boolean {
        val code = c.code
        return when {
            code < 64 -> (low ushr code) and 1L != 0L
            code < 128 -> (high ushr (code - 64)) and 1L != 0L
            else -> false
        }
    }

    operator fun plus(other: AsciiSet): AsciiSet = AsciiSet(low or other.low, high or other.high)

    operator fun plus(chars: String): AsciiSet = this + of(chars)

    operator fun minus(chars: String): AsciiSet {
        val other = of(chars)
        return AsciiSet(low and other.low.inv(), high and other.high.inv())
    }

    companion object {
        fun of(chars: String): AsciiSet {
            var low = 0L
            var high = 0L
            for (c in chars) {
                val code = c.code
                require(code < 128) { "non-ASCII character: $c" }
                if (code < 64) {
                    low = low or (1L shl code)
                } else {
                    high = high or (1L shl (code - 64))
                }
            }
            return AsciiSet(low, high)
        }

        fun range(first: Char, last: Char): AsciiSet = of((first..last).joinToString(""))
    }
}

/** Character classes from RFC 3986, plus the per-component sets this library encodes against. */
internal object UrlChars {
    val ALPHA = AsciiSet.range('a', 'z') + AsciiSet.range('A', 'Z')
    val DIGIT = AsciiSet.range('0', '9')
    val HEXDIG = DIGIT + AsciiSet.range('a', 'f') + AsciiSet.range('A', 'F')
    val UNRESERVED = ALPHA + DIGIT + "-._~"
    val SUB_DELIMS = AsciiSet.of("!$&'()*+,;=")
    val SCHEME = ALPHA + DIGIT + "+-."
    val PCHAR = UNRESERVED + SUB_DELIMS + ":@"

    val USERNAME = UNRESERVED + SUB_DELIMS
    val PASSWORD = USERNAME + ":"
    val REG_NAME = UNRESERVED + SUB_DELIMS
    val PATH = PCHAR + "/"
    val PATH_SEGMENT = PCHAR
    val QUERY = PCHAR + "/?"

    /** [QUERY] without `&`, `=`, and `+`. `#` is excluded too; it is already absent from [QUERY]. */
    val QUERY_PARAMETER = QUERY - "&=+#"
    val FRAGMENT = QUERY

    /**
     * Bytes left as-is inside an IP-literal, without the brackets.
     * `%` is absent, so a decoded zone id `fe80::1%eth0` is stored as `fe80::1%25eth0`.
     */
    val IP_LITERAL = REG_NAME + ":."
}

/**
 * Percent-encodes every character of this string that is not in [allowed], as UTF-8 bytes.
 *
 * With [keepEscapes], a valid `%XX` sequence is copied unchanged, so already encoded text is not encoded twice;
 * a `%` that does not start a valid sequence is encoded as `%25`. Without it, every `%` is encoded.
 */
internal fun String.percentEncode(allowed: AsciiSet, keepEscapes: Boolean): String {
    var start = 0
    while (start < length && this[start] in allowed) {
        start++
    }
    if (start == length) return this

    val out = StringBuilder(length + 16)
    out.append(this, 0, start)
    var i = start
    while (i < length) {
        when (val c = this[i]) {
            in allowed -> {
                out.append(c)
                i++
            }

            '%' if keepEscapes && isPercentEscape(i) -> {
                out.append(this, i, i + 3)
                i += 3
            }

            else -> {
                val end = if (c.isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) {
                    i + 2
                } else {
                    i + 1
                }
                for (b in substring(i, end).encodeToByteArray()) {
                    out.appendPercentByte(b)
                }
                i = end
            }
        }
    }
    return out.toString()
}

/**
 * Decodes `%XX` sequences and interprets the result as UTF-8; malformed byte sequences become U+FFFD.
 * A `%` that does not start a valid sequence is kept literally. With [plusAsSpace], `+` decodes to a space.
 */
internal fun String.percentDecode(plusAsSpace: Boolean = false): String {
    if (indexOf('%') < 0 && !(plusAsSpace && indexOf('+') >= 0)) {
        return this
    }

    // '%' and '+' are ASCII, so they never occur inside a multi-byte UTF-8 sequence.
    val bytes = encodeToByteArray()
    val out = ByteArray(bytes.size)
    var n = 0
    var i = 0
    while (i < bytes.size) {
        val b = bytes[i]
        if (b == '%'.code.toByte() && i + 2 < bytes.size) {
            val hi = hexValue(bytes[i + 1].toInt().toChar())
            val lo = hexValue(bytes[i + 2].toInt().toChar())
            if (hi >= 0 && lo >= 0) {
                out[n++] = (hi shl 4 or lo).toByte()
                i += 3
                continue
            }
        }
        out[n++] = if (plusAsSpace && b == '+'.code.toByte()) {
            ' '.code.toByte()
        } else {
            b
        }
        i++
    }
    return out.decodeToString(0, n)
}

internal fun String.isPercentEscape(index: Int): Boolean =
    index + 2 < length && this[index] == '%' && this[index + 1] in UrlChars.HEXDIG && this[index + 2] in UrlChars.HEXDIG

private const val HEX_DIGITS = "0123456789ABCDEF"

private fun StringBuilder.appendPercentByte(b: Byte) {
    val v = b.toInt() and 0xFF
    append('%')
    append(HEX_DIGITS[v shr 4])
    append(HEX_DIGITS[v and 0xF])
}

private fun hexValue(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> -1
}
