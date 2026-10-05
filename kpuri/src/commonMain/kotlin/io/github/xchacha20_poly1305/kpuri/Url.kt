package io.github.xchacha20_poly1305.kpuri

/**
 * An immutable URI reference (RFC 3986).
 *
 * Every component is stored in encoded form and decoded on read, so a parsed valid URI is written back unchanged.
 * A null component is absent; an empty string is present but empty (RFC 3986 §5.3).
 */
public class Url internal constructor(
    /** Lowercase scheme; null for a relative reference. */
    public val scheme: String?,
    /** Null when there is no userinfo. */
    public val encodedUsername: String?,
    /** Null when the userinfo has no ':'. */
    public val encodedPassword: String?,
    /** Without square brackets; null when there is no authority. */
    public val encodedHost: String?,
    /**
     * True when [encodedHost] is an IP-literal. [toString] wraps those hosts in brackets, including an IPvFuture
     * literal such as `v1.x` that contains no `:`.
     */
    internal val hostIsIpLiteral: Boolean,
    /** Verbatim port text; null when the authority has no ':' after the host. */
    public val port: String?,
    public val encodedPath: String,
    /** Without the leading '?'; null when there is no '?'. */
    public val encodedQuery: String?,
    /** Without the leading '#'; null when there is no '#'. */
    public val encodedFragment: String?,
    /** Options this URL was parsed or built with. [newBuilder] reuses them for the port check. */
    internal val options: UrlOptions,
) {
    /** [encodedUsername] percent-decoded; null when there is no userinfo. */
    public val username: String?
        get() = encodedUsername?.percentDecode()

    /** [encodedPassword] percent-decoded; null when the userinfo has no ':'. */
    public val password: String?
        get() = encodedPassword?.percentDecode()

    /** [encodedHost] percent-decoded, without brackets; null when there is no authority. */
    public val host: String?
        get() = encodedHost?.percentDecode()

    /** [encodedPath] percent-decoded as a whole. `%2F` becomes `/`. */
    public val path: String
        get() = encodedPath.percentDecode()

    /**
     * Segments of [encodedPath]: split on `/` first, then percent-decode each piece.
     * An empty path has no segments, and a leading `/` is not itself a segment, so `""` is `[]`, `"/"` is `[""]`,
     * and `"/a/b"` is `["a", "b"]`. `%2F` stays inside one segment; [path] decodes it to `/` and loses that boundary.
     */
    public val pathSegments: List<String> by lazy {
        if (encodedPath.isEmpty()) return@lazy emptyList()
        val pieces = encodedPath.split('/')
        val segments = if (encodedPath.startsWith('/')) pieces.drop(1) else pieces
        segments.map { it.percentDecode() }
    }

    /** [encodedFragment] percent-decoded; null when there is no '#'. */
    public val fragment: String?
        get() = encodedFragment?.percentDecode()

    /**
     * [encodedQuery] split on `&`, computed once.
     * [EncodedQueryParameter.encodedValue] is null when that piece has no `=`. Empty pieces are kept.
     */
    internal val encodedQueryParameters: List<EncodedQueryParameter> by lazy {
        encodedQuery?.let { splitEncodedQuery(it) } ?: emptyList()
    }

    /**
     * Decoded (name, value) pairs, in order, with `+` read as a space. A parameter with no `=` has the value `""`.
     *
     * A piece with an empty name and no `=` (the empty string between `&&`, a trailing `&`, or a query of `?`)
     * is kept in [encodedQueryParameters] so the query can be written back unchanged, but it is only a separator:
     * it is not a parameter named `""`. A piece that contains `=` and an empty name, such as `=v`, is.
     */
    private val queryParameters: List<Pair<String, String>> by lazy {
        encodedQueryParameters
            .filter { it.isRealQueryParameter() }
            .map {
                it.encodedName.percentDecode(plusAsSpace = true) to (it.encodedValue?.percentDecode(plusAsSpace = true)
                    ?: "")
            }
    }

    /** Decoded query parameter names, in the order they first appear. */
    public val queryParameterNames: Set<String> by lazy {
        queryParameters.mapTo(LinkedHashSet()) { it.first }
    }

    /**
     * The first value of the query parameter [name], decoded. Null when [name] is absent.
     * A parameter with no `=` (`?a`) and one with an empty value (`?a=`) both yield `""`.
     */
    public fun queryParameter(name: String): String? = queryParameters.firstOrNull { it.first == name }?.second

    /** Every value of [name], in order, decoded like [queryParameter]. Empty when [name] is absent. */
    public fun queryParameterValues(name: String): List<String> =
        queryParameters.filter { it.first == name }.map { it.second }

    /**
     * A builder filled with this URL's components and [options].
     *
     * A null [scheme] may be built again only when this URL is a relative reference. [buildUrl] never allows that,
     * and clearing the scheme of an absolute URL fails in [UrlBuilder.build].
     */
    public fun newBuilder(): UrlBuilder = UrlBuilder(options, allowsRelative = scheme == null).also { builder ->
        builder.scheme = scheme
        builder.encodedUsername = encodedUsername
        builder.encodedPassword = encodedPassword
        // The encoded setter strips one surrounding `[...]` pair and records that this host is an IP-literal.
        builder.encodedHost = if (hostIsIpLiteral && encodedHost != null) "[$encodedHost]" else encodedHost
        builder.port = port
        builder.encodedPath = encodedPath
        builder.encodedQuery = encodedQuery
        builder.encodedFragment = encodedFragment
    }

    private val string: String = buildString {
        if (scheme != null) append(scheme).append(':')
        if (encodedHost != null) {
            append("//")
            if (encodedUsername != null) {
                append(encodedUsername)
                if (encodedPassword != null) {
                    append(':').append(encodedPassword)
                }
                append('@')
            }
            if (hostIsIpLiteral) {
                append('[').append(encodedHost).append(']')
            } else {
                append(encodedHost)
            }
            if (port != null) append(':').append(port)
        }
        append(encodedPath)
        if (encodedQuery != null) {
            append('?').append(encodedQuery)
        }
        if (encodedFragment != null) {
            append('#').append(encodedFragment)
        }
    }

    override fun toString(): String = string

    /** True when [other] is a [Url] with the same [toString] form. */
    override fun equals(other: Any?): Boolean = other is Url && toString() == other.toString()

    override fun hashCode(): Int = toString().hashCode()

    public companion object {
        /** Parses an absolute URI. Throws [UrlSyntaxException] when [input] has no scheme or is malformed. */
        public fun parse(input: String, options: UrlOptions = UrlOptions()): Url =
            UrlParser(input, options).parse(requireScheme = true)

        /** Like [parse], but returns null instead of throwing. */
        public fun parseOrNull(input: String, options: UrlOptions = UrlOptions()): Url? = try {
            parse(input, options)
        } catch (_: UrlSyntaxException) {
            null
        }

        /** Parses a URI or a relative reference such as `/index.html?lang=en`. */
        public fun parseReference(input: String, options: UrlOptions = UrlOptions()): Url =
            UrlParser(input, options).parse(requireScheme = false)
    }
}

/**
 * One `&`-separated piece of [Url.encodedQuery].
 *
 * [encodedValue] is null when the piece has no `=`. Empty pieces are kept so the query can be written back unchanged.
 */
internal data class EncodedQueryParameter(
    val encodedName: String,
    val encodedValue: String?,
)

/** False for a separator piece: an empty name and no `=`, as in `&&` or a trailing `&`. */
internal fun EncodedQueryParameter.isRealQueryParameter(): Boolean =
    encodedName.isNotEmpty() || encodedValue != null

/** Splits [query] on `&`. A piece without `=` has a null value. Empty pieces are kept. */
internal fun splitEncodedQuery(query: String): List<EncodedQueryParameter> =
    query.split('&').map { piece ->
        val separator = piece.indexOf('=')
        if (separator < 0) {
            EncodedQueryParameter(piece, null)
        } else {
            EncodedQueryParameter(piece.substring(0, separator), piece.substring(separator + 1))
        }
    }

/**
 * Inverse of [splitEncodedQuery].
 * An empty list joins as `""`, which is an empty query, not a missing one; callers drop separator-only results themselves.
 */
internal fun List<EncodedQueryParameter>.joinEncodedQuery(): String =
    joinToString(separator = "&") { piece ->
        if (piece.encodedValue == null) piece.encodedName else piece.encodedName + "=" + piece.encodedValue
    }
