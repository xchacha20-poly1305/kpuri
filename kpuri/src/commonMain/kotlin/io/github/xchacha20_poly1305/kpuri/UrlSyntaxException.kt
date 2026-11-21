package io.github.xchacha20_poly1305.kpuri

/**
 * Reports a syntax or structural validation failure from [Url.parse] or [Url.parseReference].
 *
 * @constructor Creates an exception with a [reason], the original [input], and a zero-based [index]. Since 0.1.0.
 * @since 0.1.0
 */
public class UrlSyntaxException(
    /**
     * A description of the parsing failure.
     *
     * @since 0.1.0
     */
    public val reason: String,
    /**
     * The original URI text supplied to the parser.
     *
     * @since 0.1.0
     */
    public val input: String,
    /**
     * The zero-based position in [input] where the parser detected the failure.
     *
     * @since 0.1.0
     */
    public val index: Int,
) : IllegalArgumentException("$reason at index $index")
