# Module kpuri

**The public API is currently unstable. Future releases may introduce breaking changes.**

kpuri is a Kotlin Multiplatform library for parsing and building RFC 3986 URIs. This documentation follows the `dev` branch.

## Quickstart

Add `mavenCentral()` to your Gradle repositories and add the dependency to `commonMain.dependencies`. On JVM-only projects, use the same declaration in `dependencies`.

```kotlin
implementation("io.github.xchacha20-poly1305:kpuri:0.1.0")
```

Parse an absolute URI with `Url.parse`. Read `pathSegments` to keep an encoded slash inside its original segment, and use `queryParameterValues` to read repeated names in order.

```kotlin
import io.github.xchacha20_poly1305.kpuri.Url

val parsed = Url.parse("https://example.com/files/a%2Fb?q=kotlin+uri&tag=a&tag=b")
check(parsed.pathSegments == listOf("files", "a/b"))
check(parsed.queryParameter("q") == "kotlin uri")
check(parsed.queryParameterValues("tag") == listOf("a", "b"))

val updated = parsed.newBuilder().apply {
    setQueryParameter("tag", "c")
    fragment = "top"
}.build()
check(updated.toString() == "https://example.com/files/a%2Fb?q=kotlin+uri&tag=c#top")
```

Use `buildUrl` to create a URI from decoded components. The builder encodes characters that would otherwise split a path segment or a query parameter.

```kotlin
import io.github.xchacha20_poly1305.kpuri.buildUrl

val built = buildUrl("https") {
    host = "example.com"
    addPathSegment("files")
    addPathSegment("a/b c.txt")
    addQueryParameter("q", "a&b=c")
}
check(built.toString() == "https://example.com/files/a%2Fb%20c.txt?q=a%26b%3Dc")
```

## API reference

Open the package below for `Url`, `UrlBuilder`, `buildUrl`, `UrlOptions`, and `UrlSyntaxException`. Use `Url.parseReference` to accept relative references and `Url.parseOrNull` to get `null` when parsing an absolute URI fails.
