package io.github.xchacha20_poly1305.kpuri

public data class UrlOptions(
    /** When false, the port is kept verbatim,
     *  e.g. the Hysteria 2 port hopping form `443,7788-8899`.
     */
    val validatePort: Boolean = true,
)
