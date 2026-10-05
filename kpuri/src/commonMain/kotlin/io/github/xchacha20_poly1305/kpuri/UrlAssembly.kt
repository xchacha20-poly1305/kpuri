package io.github.xchacha20_poly1305.kpuri

/**
 * Component text before [assembleUrl] validates it and percent-encodes with `keepEscapes = true`.
 *
 * The parser passes slices of the raw input. The builder passes text its setters have already encoded, for which
 * that second encode is a no-op. An IP-literal [host] is not encoded here: the parser has checked the literal, and
 * the builder has already turned a decoded `%` into `%25`.
 */
internal class UrlComponents(
    internal val scheme: String?,
    internal val username: String?,
    internal val password: String?,
    internal val host: String?,
    /** Decided by the caller: the parser saw `[...]`; the builder saw brackets or a `:`. A reg-name never has `:`. */
    internal val hostIsIpLiteral: Boolean,
    internal val port: String?,
    internal val path: String,
    internal val query: String?,
    internal val fragment: String?,
)

/** A structural failure. Any index is relative to that component, not to the original URI text. */
internal sealed class UrlComponentError {
    internal class Scheme(internal val index: Int) : UrlComponentError()
    internal object SchemeRequired : UrlComponentError()
    internal class Host(internal val index: Int, internal val ipLiteral: Boolean) : UrlComponentError()
    internal class Port(internal val index: Int) : UrlComponentError()
    internal object AuthorityRequired : UrlComponentError()
    internal object PathStartsWithDoubleSlash : UrlComponentError()
    internal object ColonInFirstSegment : UrlComponentError()
}

/**
 * The single structural check shared by [UrlParser] and [UrlBuilder].
 *
 * Validates scheme, host, and port, encodes the other components, and applies the normalizations [UrlBuilder.build]
 * documents: a missing username implied by a password, and a leading `/` on a non-empty path that has an authority.
 * [onError] does not return. The parser turns it into [UrlSyntaxException] at an index in the input; the builder
 * throws [IllegalArgumentException].
 */
internal fun assembleUrl(
    components: UrlComponents,
    options: UrlOptions,
    allowsRelative: Boolean,
    onError: (UrlComponentError) -> Nothing,
): Url {
    val scheme = normalizeScheme(components.scheme, allowsRelative, onError)
    var username = components.username?.percentEncode(UrlChars.USERNAME, keepEscapes = true)
    val password = components.password?.percentEncode(UrlChars.PASSWORD, keepEscapes = true)
    if (password != null && username == null) username = ""

    val host = normalizeHost(components.host, components.hostIsIpLiteral, onError)
    if (components.port != null) {
        val index = invalidPortIndex(components.port, options)
        if (index >= 0) {
            onError(UrlComponentError.Port(index))
        }
    }
    if (host == null && (username != null || components.port != null)) {
        onError(UrlComponentError.AuthorityRequired)
    }

    var path = components.path.percentEncode(UrlChars.PATH, keepEscapes = true)
    if (host != null && path.isNotEmpty() && !path.startsWith('/')) {
        path = "/$path"
    }
    if (host == null && path.startsWith("//")) {
        onError(UrlComponentError.PathStartsWithDoubleSlash)
    }
    if (scheme == null && host == null && hasColonInFirstSegment(path)) {
        onError(UrlComponentError.ColonInFirstSegment)
    }

    return Url(
        scheme = scheme,
        encodedUsername = username,
        encodedPassword = password,
        encodedHost = host,
        hostIsIpLiteral = host != null && components.hostIsIpLiteral,
        port = components.port,
        encodedPath = path,
        encodedQuery = components.query?.percentEncode(UrlChars.QUERY, keepEscapes = true),
        encodedFragment = components.fragment?.percentEncode(UrlChars.FRAGMENT, keepEscapes = true),
        options = options,
    )
}

private fun normalizeScheme(
    scheme: String?,
    allowsRelative: Boolean,
    onError: (UrlComponentError) -> Nothing,
): String? {
    if (scheme == null) {
        if (!allowsRelative) {
            onError(UrlComponentError.SchemeRequired)
        }
        return null
    }
    val index = invalidSchemeIndex(scheme)
    if (index >= 0) {
        onError(UrlComponentError.Scheme(index))
    }
    return scheme.lowercase()
}

private fun normalizeHost(
    host: String?,
    hostIsIpLiteral: Boolean,
    onError: (UrlComponentError) -> Nothing,
): String? {
    if (host == null) return null
    if (hostIsIpLiteral) {
        val index = invalidIpLiteralIndex(host)
        if (index >= 0) {
            onError(UrlComponentError.Host(index, ipLiteral = true))
        }
        return host
    }
    val index = invalidRegNameIndex(host)
    if (index >= 0) {
        onError(UrlComponentError.Host(index, ipLiteral = false))
    }
    return host.percentEncode(UrlChars.REG_NAME, keepEscapes = true)
}

/** RFC 3986 §4.2: a relative path's first segment cannot contain `:`, or the text would look like a scheme. */
private fun hasColonInFirstSegment(path: String): Boolean {
    if (path.isEmpty() || path.startsWith('/')) {
        return false
    }
    val slash = path.indexOf('/')
    val first = if (slash < 0) {
        path
    } else {
        path.substring(0, slash)
    }
    return ':' in first
}
