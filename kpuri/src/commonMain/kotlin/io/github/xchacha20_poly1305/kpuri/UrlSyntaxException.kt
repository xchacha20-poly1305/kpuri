package io.github.xchacha20_poly1305.kpuri

public class UrlSyntaxException(
    public val reason: String,
    public val input: String,
    public val index: Int,
) : IllegalArgumentException("$reason at index $index")
