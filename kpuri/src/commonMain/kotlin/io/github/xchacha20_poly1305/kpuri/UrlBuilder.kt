package io.github.xchacha20_poly1305.kpuri

/**
 * Mutable builder for a [Url]. Obtain one from [buildUrl] or [Url.newBuilder]; there is no public empty constructor.
 *
 * [username], [password], [host], [path], and [fragment] accept decoded text. These setters percent-encode
 * disallowed characters, including every `%`. Setters with the `encoded` prefix preserve valid `%XX` escapes
 * and encode disallowed characters. Getters return the corresponding decoded or encoded text.
 *
 * [build] then runs the shared structural checks. A null [scheme] is allowed only when this builder came from
 * [Url.newBuilder] on a relative reference ([Url.scheme] was null). [buildUrl] never allows it. Normalization that
 * inserts an empty username or a leading `/` is applied to the [Url] [build] returns, not written back here.
 * [scheme] is lowercased as soon as it is set.
 *
 * @since 0.1.0
 */
public class UrlBuilder internal constructor(
    private val options: UrlOptions,
    private val allowsRelative: Boolean,
) {
    /**
     * Lowercase scheme, or null when this builder is allowed to produce a relative reference.
     *
     * @since 0.1.0
     */
    public var scheme: String? = null
        set(value) {
            field = value?.lowercase()
        }

    private var storedUsername: String? = null

    /**
     * Decoded userinfo name; null when there is no userinfo. `:` is encoded.
     *
     * @since 0.1.0
     */
    public var username: String?
        get() = storedUsername?.percentDecode()
        set(value) {
            storedUsername = value?.percentEncode(UrlChars.USERNAME, keepEscapes = false)
        }

    /**
     * Percent-encoded userinfo name; null when there is no userinfo.
     *
     * @since 0.1.0
     */
    public var encodedUsername: String?
        get() = storedUsername
        set(value) {
            storedUsername = value?.percentEncode(UrlChars.USERNAME, keepEscapes = true)
        }

    private var storedPassword: String? = null

    /**
     * Decoded password; null when the userinfo has no `:`.
     *
     * @since 0.1.0
     */
    public var password: String?
        get() = storedPassword?.percentDecode()
        set(value) {
            storedPassword = value?.percentEncode(UrlChars.PASSWORD, keepEscapes = false)
        }

    /**
     * Percent-encoded password; null when the userinfo has no `:`.
     *
     * @since 0.1.0
     */
    public var encodedPassword: String?
        get() = storedPassword
        set(value) {
            storedPassword = value?.percentEncode(UrlChars.PASSWORD, keepEscapes = true)
        }

    private var storedHost: String? = null
    private var hostIsIpLiteral: Boolean = false

    /**
     * Decoded host, without brackets; null when there is no authority.
     *
     * A surrounding `[...]` pair is stripped. The body is an IP-literal when it was bracketed or contains `:`;
     * otherwise it is a reg-name. A decoded `%` is encoded, so `fe80::1%eth0` is stored as `fe80::1%25eth0`.
     *
     * @since 0.1.0
     */
    public var host: String?
        get() = storedHost?.percentDecode()
        set(value) {
            updateHost(value, keepEscapes = false)
        }

    /**
     * Percent-encoded host, without brackets; null when there is no authority. Brackets are stripped like [host].
     *
     * @since 0.1.0
     */
    public var encodedHost: String?
        get() = storedHost
        set(value) {
            updateHost(value, keepEscapes = true)
        }

    /**
     * Verbatim port text. Null means no port; `""` means a trailing colon, as in `host:`.
     *
     * @since 0.1.0
     */
    public var port: String? = null

    private var storedPath: String = ""

    /**
     * Decoded path. `%2F` becomes `/`.
     * A non-empty path that has an authority and does not start with `/` gains that `/` in [build], not here.
     *
     * @since 0.1.0
     */
    public var path: String
        get() = storedPath.percentDecode()
        set(value) {
            storedPath = value.percentEncode(UrlChars.PATH, keepEscapes = false)
        }

    /**
     * Percent-encoded path. May be empty.
     *
     * @since 0.1.0
     */
    public var encodedPath: String
        get() = storedPath
        set(value) {
            storedPath = value.percentEncode(UrlChars.PATH, keepEscapes = true)
        }

    /**
     * Appends [segment] as one path segment. Inside the segment, `/` becomes `%2F` and `%` becomes `%25`.
     *
     * The segment is joined to the current path with a single slash:
     * - an empty path takes the segment as-is, so `""` + `"a"` is `"a"` and `""` + `""` stays `""`
     *   ([build] adds a leading `/` when the result has an authority and the path is non-empty);
     * - a path that already ends in `/` is extended directly, so `"/a/"` + `"b"` is `"/a/b"`;
     * - otherwise `/` is inserted, so `"/a"` + `"b"` is `"/a/b"` and `"/a"` + `""` is `"/a/"`.
     *   An empty segment on a path that already ends in `/` does not add another slash.
     *
     * @param segment the decoded path segment.
     * @return this builder.
     * @since 0.1.0
     */
    public fun addPathSegment(segment: String): UrlBuilder {
        val encoded = segment.percentEncode(UrlChars.PATH_SEGMENT, keepEscapes = false)
        storedPath = if (encoded.isEmpty() && storedPath.isNotEmpty() && !storedPath.endsWith('/')) {
            "$storedPath/"
        } else {
            joinPaths(storedPath, encoded)
        }
        return this
    }

    /**
     * Appends every `/`-separated piece of [segments]. Each piece is encoded like [addPathSegment], and the slashes
     * that separated the pieces are kept, including a leading, trailing, or doubled slash:
     * `""` + `"a/b"` is `"a/b"`, `""` + `"/a/b"` is `"/a/b"`, `""` + `"a/b/"` is `"a/b/"`, and `""` + `"a//b"` is
     * `"a//b"`. That text is then joined to the current path the same way as [addPathSegment], so `"/a"` + `"b"` and
     * `"/a/"` + `"/b"` are both `"/a/b"`. An empty string adds nothing. Unlike [addPathSegment], an empty piece is a
     * real slash boundary (`"a//b"` stays doubled) rather than only a trailing-slash request.
     *
     * @param segments the decoded path segments separated by slashes.
     * @return this builder.
     * @since 0.1.0
     */
    public fun addPathSegments(segments: String): UrlBuilder {
        if (segments.isEmpty()) return this
        val addition = segments.split('/').joinToString("/") { piece ->
            piece.percentEncode(UrlChars.PATH_SEGMENT, keepEscapes = false)
        }
        storedPath = joinPaths(storedPath, addition)
        return this
    }

    private var queryPieces: List<EncodedQueryParameter>? = null

    /**
     * Percent-encoded query without `?`, or null when the URL has no query.
     * Assigned text is encoded with the query rules (`keepEscapes = true`) and then split on `&`.
     *
     * @since 0.1.0
     */
    public var encodedQuery: String?
        get() = queryPieces?.joinEncodedQuery()
        set(value) {
            queryPieces = value?.let { splitEncodedQuery(it.percentEncode(UrlChars.QUERY, keepEscapes = true)) }
        }

    /**
     * Appends a query parameter and percent-encodes characters such as `&`, `=`, `+`, and `#`.
     * Parameters already in the query are left byte for byte, including separator pieces such as the empty piece in
     * `a=1&&b=2`.
     *
     * @param name the decoded parameter name.
     * @param value the decoded value. Null omits `=`, while an empty string keeps it.
     * @return this builder.
     * @since 0.1.0
     */
    public fun addQueryParameter(name: String, value: String?): UrlBuilder = addQueryPiece(
        name.percentEncode(UrlChars.QUERY_PARAMETER, keepEscapes = false),
        value?.percentEncode(UrlChars.QUERY_PARAMETER, keepEscapes = false),
    )

    /**
     * Like [addQueryParameter], but [encodedName] and [encodedValue] may already contain `%XX`.
     * `&`, `=`, `+`, and `#` are still encoded, so a value cannot break the query apart.
     *
     * @param encodedName the name, with any existing percent escapes.
     * @param encodedValue the value, with any existing percent escapes. Null omits `=`.
     * @return this builder.
     * @since 0.1.0
     */
    public fun addEncodedQueryParameter(encodedName: String, encodedValue: String?): UrlBuilder = addQueryPiece(
        encodedName.percentEncode(UrlChars.QUERY_PARAMETER, keepEscapes = true),
        encodedValue?.percentEncode(UrlChars.QUERY_PARAMETER, keepEscapes = true),
    )

    /**
     * Sets the decoded parameter [name] to [value], encoded like [addQueryParameter].
     * The first existing match is replaced and the later matches are removed; names compare the way
     * [Url.queryParameter] does, with `+` read as a space. When [name] is absent, the parameter is appended.
     * Every piece that is not replaced keeps its original encoding.
     *
     * @param name the decoded parameter name.
     * @param value the decoded value. Null omits `=`, while an empty string keeps it.
     * @return this builder.
     * @since 0.1.0
     */
    public fun setQueryParameter(name: String, value: String?): UrlBuilder {
        val replacement = EncodedQueryParameter(
            name.percentEncode(UrlChars.QUERY_PARAMETER, keepEscapes = false),
            value?.percentEncode(UrlChars.QUERY_PARAMETER, keepEscapes = false),
        )
        val current = queryPieces
        if (current == null) {
            queryPieces = listOf(replacement)
            return this
        }
        var replaced = false
        val next = ArrayList<EncodedQueryParameter>(current.size + 1)
        for (piece in current) {
            if (piece.matchesDecodedName(name)) {
                if (!replaced) {
                    next.add(replacement)
                    replaced = true
                }
            } else {
                next.add(piece)
            }
        }
        if (!replaced) next.add(replacement)
        queryPieces = next
        return this
    }

    /**
     * Removes every parameter whose decoded name is [name].
     * When that leaves no real parameter, only separator pieces such as `&&` or nothing, [encodedQuery] becomes null
     * so the URL has no `?`. A query that is already only separators is left unchanged when [name] is absent.
     * Parameters that stay keep their original text.
     *
     * @param name the decoded parameter name.
     * @return this builder.
     * @since 0.1.0
     */
    public fun removeQueryParameter(name: String): UrlBuilder {
        val current = queryPieces ?: return this
        val next = current.filterNot { it.matchesDecodedName(name) }
        queryPieces = if (next.size != current.size && next.none { it.isRealQueryParameter() }) {
            null
        } else {
            next
        }
        return this
    }

    /**
     * Decoded fragment without `#`; null when there is no fragment.
     *
     * @since 0.1.0
     */
    public var fragment: String?
        get() = storedFragment?.percentDecode()
        set(value) {
            storedFragment = value?.percentEncode(UrlChars.FRAGMENT, keepEscapes = false)
        }

    private var storedFragment: String? = null

    /**
     * Percent-encoded fragment without `#`; null when there is no fragment.
     *
     * @since 0.1.0
     */
    public var encodedFragment: String?
        get() = storedFragment
        set(value) {
            storedFragment = value?.percentEncode(UrlChars.FRAGMENT, keepEscapes = true)
        }

    /**
     * Checks the components and returns a [Url], or throws [IllegalArgumentException].
     *
     * - A null [scheme] is rejected unless this builder came from a relative reference. Otherwise the scheme must
     *   match `ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )` and is lowercased.
     * - An authority is present exactly when [host] is non-null. Userinfo or [port] without a host is rejected.
     *   A password without a username is written as an empty username (`:pass@host`).
     * - [host] and [port] use the parser's validators and this builder's [UrlOptions].
     * - With an authority, a non-empty path that does not start with `/` gains a leading `/`.
     *   Without an authority, the path must not start with `//`.
     *   Without a scheme, the first segment of a relative path must not contain `:`.
     *
     * @return the validated URI reference.
     * @throws IllegalArgumentException if the components fail validation.
     * @since 0.1.0
     */
    public fun build(): Url = assembleUrl(
        components = UrlComponents(
            scheme = scheme,
            username = storedUsername,
            password = storedPassword,
            host = storedHost,
            hostIsIpLiteral = hostIsIpLiteral,
            port = port,
            path = storedPath,
            query = encodedQuery,
            fragment = storedFragment,
        ),
        options = options,
        allowsRelative = allowsRelative,
        onError = ::failBuild,
    )

    private fun updateHost(value: String?, keepEscapes: Boolean) {
        if (value == null) {
            storedHost = null
            hostIsIpLiteral = false
            return
        }
        val (body, bracketed) = unwrapHostBrackets(value)
        val ipLiteral = bracketed || ':' in body
        val allowed = if (ipLiteral) {
            UrlChars.IP_LITERAL
        } else {
            UrlChars.REG_NAME
        }
        storedHost = body.percentEncode(allowed, keepEscapes)
        hostIsIpLiteral = ipLiteral
    }

    private fun addQueryPiece(encodedName: String, encodedValue: String?): UrlBuilder {
        val piece = EncodedQueryParameter(encodedName, encodedValue)
        queryPieces = (queryPieces ?: emptyList()) + piece
        return this
    }

    private fun failBuild(error: UrlComponentError): Nothing {
        val message = when (error) {
            is UrlComponentError.Scheme -> "invalid scheme"
            UrlComponentError.SchemeRequired -> "scheme required"

            is UrlComponentError.Host -> if (error.ipLiteral) {
                "invalid IP literal"
            } else {
                "invalid host"
            }

            is UrlComponentError.Port -> "invalid port"
            UrlComponentError.AuthorityRequired -> "host required"
            UrlComponentError.PathStartsWithDoubleSlash -> "path starts with '//'"
            UrlComponentError.ColonInFirstSegment -> "colon in first path segment"
        }
        throw IllegalArgumentException(message)
    }
}

/**
 * Builds an absolute URI with a [UrlBuilder]. The scheme must be present when [block] finishes.
 *
 * @param scheme the initial scheme. [UrlBuilder.scheme] stores it in lowercase.
 * @param options the port validation policy.
 * @param block the initializer that configures the URI components.
 * @return the validated absolute URI.
 * @throws IllegalArgumentException if the configured components fail validation.
 * @since 0.1.0
 */
public fun buildUrl(scheme: String, options: UrlOptions = UrlOptions(), block: UrlBuilder.() -> Unit): Url {
    val builder = UrlBuilder(options, allowsRelative = false)
    builder.scheme = scheme
    builder.block()
    return builder.build()
}

/** A host written as `[...]` loses that one pair of brackets. The stored host never includes them. */
private fun unwrapHostBrackets(host: String): Pair<String, Boolean> {
    if (host.length >= 2 && host[0] == '[' && host[host.length - 1] == ']') {
        return host.substring(1, host.length - 1) to true
    }
    return host to false
}

/**
 * Joins two encoded paths with one `/` when neither side already provides that boundary.
 * An empty [addition] leaves [current] unchanged; a leading slash on [addition] is kept when [current] is empty.
 */
private fun joinPaths(current: String, addition: String): String = when {
    current.isEmpty() -> addition
    addition.isEmpty() -> current
    current.endsWith('/') && addition.startsWith('/') -> current + addition.substring(1)
    current.endsWith('/') || addition.startsWith('/') -> current + addition
    else -> "$current/$addition"
}

/** True when this piece is the parameter [name] after `+` and `%XX` are decoded, matching [Url.queryParameter]. */
private fun EncodedQueryParameter.matchesDecodedName(name: String): Boolean =
    isRealQueryParameter() && encodedName.percentDecode(plusAsSpace = true) == name
