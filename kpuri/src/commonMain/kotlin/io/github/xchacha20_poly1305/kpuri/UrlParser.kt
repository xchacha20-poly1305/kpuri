package io.github.xchacha20_poly1305.kpuri

/**
 * Splits [input] on RFC 3986 Appendix B boundaries.
 *
 * Scheme, host, port, encoding, and the remaining structure are checked by [assembleUrl], which [UrlBuilder] also
 * uses. This scanner only rejects what cannot be split: a control character, an unclosed `[`, and junk between an
 * IP-literal and its port. Those failures still report an index into [input] via [UrlSyntaxException].
 */
internal class UrlParser(
    private val input: String,
    private val options: UrlOptions,
) {
    fun parse(requireScheme: Boolean): Url {
        invalidControlIndex(input).let {
            if (it >= 0) fail("control character", it)
        }

        val scheme: String?
        var pos: Int
        val schemeEnd = input.indexOfAny(charArrayOf(':', '/', '?', '#'))
        if (schemeEnd >= 0 && input[schemeEnd] == ':') {
            scheme = input.substring(0, schemeEnd)
            pos = schemeEnd + 1
        } else {
            scheme = null
            pos = 0
        }

        var username: String? = null
        var password: String? = null
        var host: String? = null
        var hostIsIpLiteral = false
        var hostIndex = 0
        var port: String? = null
        var portIndex = 0
        if (input.startsWith("//", pos)) {
            val authorityStart = pos + 2
            val authorityEnd = input.indexOfAny(charArrayOf('/', '?', '#'), authorityStart).let {
                if (it < 0) {
                    input.length
                } else {
                    it
                }
            }

            // The last '@' ends the userinfo, since passwords often contain an unencoded '@'.
            val at = input.lastIndexOf('@', authorityEnd - 1).takeIf { it >= authorityStart }
            val hostStart = if (at == null) {
                authorityStart
            } else {
                val colon = input.indexOf(':', authorityStart).takeIf { it in authorityStart until at }
                if (colon == null) {
                    username = input.substring(authorityStart, at)
                } else {
                    username = input.substring(authorityStart, colon)
                    password = input.substring(colon + 1, at)
                }
                at + 1
            }

            val portStart: Int
            if (hostStart < authorityEnd && input[hostStart] == '[') {
                val close = input.indexOf(']', hostStart).takeIf {
                    it in hostStart until authorityEnd
                }
                if (close == null) {
                    fail("missing ']' in host", hostStart)
                }
                host = input.substring(hostStart + 1, close)
                hostIsIpLiteral = true
                hostIndex = hostStart + 1
                portStart = close + 1
                if (portStart < authorityEnd && input[portStart] != ':') {
                    fail("unexpected character after IP literal", portStart)
                }
            } else {
                portStart = input.indexOf(':', hostStart).takeIf {
                    it in hostStart until authorityEnd
                } ?: authorityEnd
                host = input.substring(hostStart, portStart)
                hostIndex = hostStart
            }

            if (portStart < authorityEnd) {
                port = input.substring(portStart + 1, authorityEnd)
                portIndex = portStart + 1
            }
            pos = authorityEnd
        }

        val pathEnd = input.indexOfAny(charArrayOf('?', '#'), pos)
            .let {
                if (it < 0) {
                    input.length
                } else {
                    it
                }
            }
        val path = input.substring(pos, pathEnd)
        pos = pathEnd

        var query: String? = null
        if (pos < input.length && input[pos] == '?') {
            val queryEnd = input.indexOf('#', pos)
                .let {
                    if (it < 0) {
                        input.length
                    } else {
                        it
                    }
                }
            query = input.substring(pos + 1, queryEnd)
            pos = queryEnd
        }

        val fragment = if (pos < input.length) {
            input.substring(pos + 1)
        } else {
            null
        }

        return assembleUrl(
            components = UrlComponents(
                scheme = scheme,
                username = username,
                password = password,
                host = host,
                hostIsIpLiteral = hostIsIpLiteral,
                port = port,
                path = path,
                query = query,
                fragment = fragment,
            ),
            options = options,
            allowsRelative = !requireScheme,
            onError = { error -> failStructure(error, scheme, hostIndex, portIndex) },
        )
    }

    private fun failStructure(
        error: UrlComponentError,
        scheme: String?,
        hostIndex: Int,
        portIndex: Int,
    ): Nothing = when (error) {
        is UrlComponentError.Scheme -> {
            val reason = if (scheme != null && scheme.isEmpty()) {
                "missing scheme"
            } else {
                "invalid character in scheme"
            }
            fail(reason, error.index)
        }

        UrlComponentError.SchemeRequired -> fail("missing scheme", 0)

        is UrlComponentError.Host -> {
            val reason = if (error.ipLiteral) {
                "invalid IP literal"
            } else {
                "invalid character in host"
            }
            fail(reason, hostIndex + error.index)
        }

        is UrlComponentError.Port -> fail("invalid port", portIndex + error.index)
        UrlComponentError.AuthorityRequired -> fail("missing host", 0)
        UrlComponentError.PathStartsWithDoubleSlash -> fail("path starts with '//'", 0)
        UrlComponentError.ColonInFirstSegment -> fail("colon in first path segment", 0)
    }

    private fun fail(reason: String, index: Int): Nothing = throw UrlSyntaxException(reason, input, index)
}
