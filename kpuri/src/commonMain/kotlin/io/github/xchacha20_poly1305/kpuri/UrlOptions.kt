package io.github.xchacha20_poly1305.kpuri

/**
 * Validation options for parsing and building URI references.
 *
 * @constructor Creates a validation policy. [validatePort] defaults to true. Since 0.1.0.
 * @since 0.1.0
 */
public data class UrlOptions(
    /**
     * When true, a non-empty port must contain only ASCII digits and have a value of at most 65535.
     * When false, forms such as `443,7788-8899` are preserved verbatim, subject to URI structure checks.
     * An empty port is allowed in either mode.
     *
     * @since 0.1.0
     */
    val validatePort: Boolean = true,
)
