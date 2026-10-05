package io.github.xchacha20_poly1305.kpuri

// Each validator returns the index of the first offending character, or -1 when the input is valid.

private const val VALID = -1
private const val MAX_PORT = 65535

/** `ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )`, RFC 3986 §3.1. */
internal fun invalidSchemeIndex(scheme: String): Int {
    if (scheme.isEmpty()) return 0
    if (scheme[0] !in UrlChars.ALPHA) return 0
    for (i in 1 until scheme.length) {
        if (scheme[i] !in UrlChars.SCHEME) return i
    }
    return VALID
}

/** Control characters are the only characters rejected outright in any component. */
internal fun invalidControlIndex(text: String): Int {
    return text.indexOfFirst {
        it.isUrlControl()
    }
}

/** A reg-name additionally rejects literal spaces and square brackets. Other characters are encoded. */
internal fun invalidRegNameIndex(host: String): Int {
    return host.indexOfFirst {
        it.isUrlControl() || it == ' ' || it == '[' || it == ']'
    }
}

/**
 * With [UrlOptions.validatePort], the port must be `*DIGIT` with a value of at most 65535.
 * Otherwise any text is kept, except characters that would end the authority or break its structure.
 */
internal fun invalidPortIndex(port: String, options: UrlOptions): Int {
    return if (options.validatePort) {
        var value = 0
        for (i in port.indices) {
            val c = port[i]
            if (c !in UrlChars.DIGIT) return i
            value = value * 10 + (c - '0')
            if (value > MAX_PORT) return i
        }
        VALID
    } else port.indexOfFirst {
        it.isUrlControl() || it in "/?#@[]"
    }
}

/** The content of an IP-literal without the brackets: IPv6address with optional `%25` zone id, or IPvFuture. */
internal fun invalidIpLiteralIndex(literal: String): Int {
    if (literal.startsWith('v') || literal.startsWith('V')) {
        return invalidIpvFutureIndex(literal)
    }
    val zoneStart = literal.indexOf("%25")
    if (zoneStart < 0) {
        return if (isIpv6Address(literal)) {
            VALID
        } else {
            0
        }
    }
    if (!isIpv6Address(literal.substring(0, zoneStart))) {
        return 0
    }
    return invalidZoneIdIndex(literal, zoneStart + 3)
}

/** `"v" 1*HEXDIG "." 1*( unreserved / sub-delims / ":" )`, RFC 3986 §3.2.2. */
private fun invalidIpvFutureIndex(literal: String): Int {
    var i = 1
    while (i < literal.length && literal[i] in UrlChars.HEXDIG) {
        i++
    }
    if (i == 1 || i == literal.length || literal[i] != '.') {
        return i
    }
    i++
    if (i == literal.length) {
        return i
    }
    while (i < literal.length) {
        val c = literal[i]
        if (c !in UrlChars.UNRESERVED && c !in UrlChars.SUB_DELIMS && c != ':') {
            return i
        }
        i++
    }
    return VALID
}

/** `ZoneID = 1*( unreserved / pct-encoded )`, RFC 6874. */
private fun invalidZoneIdIndex(literal: String, start: Int): Int {
    if (start == literal.length) return start
    var i = start
    while (i < literal.length) {
        when {
            literal[i] in UrlChars.UNRESERVED -> i++
            literal.isPercentEscape(i) -> i += 3
            else -> return i
        }
    }
    return VALID
}

/** IPv6address, RFC 3986 §3.2.2: eight 16-bit groups, at most one `::`, optionally ending in an IPv4 address. */
internal fun isIpv6Address(text: String): Boolean {
    val compressed = text.indexOf("::")
    val groups: List<String>
    if (compressed < 0) {
        groups = text.split(':')
    } else {
        if (text.indexOf("::", compressed + 1) >= 0) {
            return false
        }
        val head = text.substring(0, compressed)
        val headGroups = if (head.isEmpty()) {
            emptyList()
        } else {
            head.split(':')
        }
        val tail = text.substring(compressed + 2)
        val tailGroups = if (tail.isEmpty()) {
            emptyList()
        } else {
            tail.split(':')
        }
        groups = headGroups + tailGroups
    }

    // An IPv4 suffix must end the text, so "1.2.3.4::" is rejected.
    val endsWithGroup = compressed < 0 || compressed + 2 < text.length
    var count = 0
    for ((index, group) in groups.withIndex()) {
        if (index == groups.lastIndex && endsWithGroup && group.contains('.')) {
            if (!isIpv4Address(group)) {
                return false
            }
            count += 2
        } else {
            if (group.isEmpty() || group.length > 4 || group.any { it !in UrlChars.HEXDIG }) {
                return false
            }
            count++
        }
    }
    return if (compressed < 0) {
        count == 8
    } else {
        count <= 7
    }
}

/** IPv4address, RFC 3986 §3.2.2: four dec-octets without leading zeros. */
internal fun isIpv4Address(text: String): Boolean {
    val octets = text.split('.')
    if (octets.size != 4) {
        return false
    }
    return octets.all { octet ->
        octet.isNotEmpty()
                && octet.length <= 3
                && octet.all { it in UrlChars.DIGIT }
                && (octet.length == 1 || octet[0] != '0')
                && octet.toInt() <= 255
    }
}

private fun Char.isUrlControl(): Boolean = code < 0x20 || code == 0x7F
