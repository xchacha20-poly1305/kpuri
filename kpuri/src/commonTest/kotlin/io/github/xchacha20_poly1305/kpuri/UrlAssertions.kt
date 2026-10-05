package io.github.xchacha20_poly1305.kpuri

import kotlin.test.assertEquals

/**
 * [input] is already in the form [Url.toString] writes.
 * Parsing it is a no-op, and parsing that text again yields an equal [Url].
 * [options] is the port policy the input was accepted with.
 */
internal fun assertRoundTrip(input: String, options: UrlOptions = UrlOptions()) {
    val url = Url.parseReference(input, options)
    assertEquals(input, url.toString(), input)
    assertEquals(url, reparse(url, options), input)
}

/** Parsing may rewrite [input]. Parsing the written form again does not rewrite it a second time. */
internal fun assertReparseStable(input: String, options: UrlOptions = UrlOptions()) {
    val url = Url.parseReference(input, options)
    assertEquals(url, reparse(url, options), input)
}

private fun reparse(url: Url, options: UrlOptions): Url {
    val text = url.toString()
    return if (url.scheme == null) Url.parseReference(text, options) else Url.parse(text, options)
}
