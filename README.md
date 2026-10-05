# kpuri

A Kotlin Multiplatform library for parsing and building URIs according to [RFC 3986](https://www.rfc-editor.org/rfc/rfc3986).

- **Lossless.** Parsing a valid URI and calling `toString()` gives back the same text. Components are stored percent-encoded and decoded when read.
- **Absent is not empty.** `http://h/p` has no query (`null`); `http://h/p?` has an empty one (`""`).
- **One set of rules.** The parser and the builder share the same structural checks, so a `Url` that one accepts the other accepts too.
- **Pure common code.** No platform-specific sources and no dependencies beyond the Kotlin standard library.

Supported targets: JVM (Java 8+), JS and Wasm/JS, Linux x64/arm64, Windows x64 (mingw), macOS arm64, iOS arm64 and the iOS simulator on arm64.

## Installation

Releases are published to Maven Central under `io.github.xchacha20-poly1305:kpuri`. Make sure `mavenCentral()` is among your repositories, usually in `settings.gradle.kts`:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

Then add the dependency to the source set that uses it:

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.xchacha20-poly1305:kpuri:0.1.0")
        }
    }
}
```

On a JVM-only project, add the same coordinate to `dependencies { }`.

### Version catalog

With a Gradle version catalog, declare kpuri in `gradle/libs.versions.toml`:

```toml
[versions]
kpuri = "0.1.0"

[libraries]
kpuri = { module = "io.github.xchacha20-poly1305:kpuri", version.ref = "kpuri" }
```

and refer to it as `libs.kpuri`:

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kpuri)
        }
    }
}
```

### Snapshots

Snapshot builds (`-SNAPSHOT` versions) are published to the Maven Central snapshot repository, which has to be added separately:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            mavenContent { snapshotsOnly() }
        }
    }
}
```

## Parsing

```kotlin
import io.github.xchacha20_poly1305.kpuri.Url

val url = Url.parse("https://user:p%40ss@example.com:8443/docs/a%2Fb/c?q=kotlin+uri&tag=a&tag=b#top")

url.scheme                      // "https"
url.username                    // "user"
url.password                    // "p@ss"
url.encodedPassword             // "p%40ss"
url.host                        // "example.com"
url.port                        // "8443"
url.path                        // "/docs/a/b/c"
url.pathSegments                // ["docs", "a/b", "c"]
url.queryParameter("q")         // "kotlin uri"
url.queryParameterValues("tag") // ["a", "b"]
url.queryParameterNames         // [q, tag]
url.fragment                    // "top"
url.toString()                  // the input, unchanged
```

Every component has a decoded getter (`host`, `path`, `fragment`, ...) and an encoded one (`encodedHost`, `encodedPath`, `encodedFragment`, ...). `path` decodes `%2F` to `/`; `pathSegments` splits first and decodes each segment, so the boundary is kept. Decoded query reads treat `+` as a space.

The scheme is lowercased. Characters that are not allowed in a component are percent-encoded rather than rejected, while valid `%XX` escapes are kept as they are:

```kotlin
Url.parse("HTTP://example.com/a b?x=ü").toString() // "http://example.com/a%20b?x=%C3%BC"
```

### Entry points

| Function                    | Accepts                                                               | On error                    |
|-----------------------------|-----------------------------------------------------------------------|-----------------------------|
| `Url.parse(input)`          | absolute URIs only                                                    | throws `UrlSyntaxException` |
| `Url.parseOrNull(input)`    | absolute URIs only                                                    | returns `null`              |
| `Url.parseReference(input)` | absolute URIs and relative references such as `../index.html?lang=en` | throws `UrlSyntaxException` |

`UrlSyntaxException` is an `IllegalArgumentException` that carries the `reason`, the `input`, and the `index` in the input where the problem was found:

```kotlin
Url.parse("http://[::1/") // UrlSyntaxException: missing ']' in host at index 7
```

URIs without an authority work too: `Url.parse("mailto:someone@example.com")` has a `null` host and the path `someone@example.com`.

### Query parameters

The query is split on `&`. A piece without `=` is a parameter whose value is `""`. Empty pieces, as in `a&&b` or a trailing `&`, are separators and are not reported as parameters, but they are kept so the query is written back unchanged.

```kotlin
val url = Url.parse("http://h/?a&b=&=c&&")
url.queryParameterNames  // [a, b, ""]
url.queryParameter("a")  // ""
url.queryParameter("")   // "c"
url.toString()           // "http://h/?a&b=&=c&&"
```

## Building

Use `buildUrl` for a new URI. Properties without the `encoded` prefix take decoded text and encode everything the component does not allow, including `%`.

```kotlin
import io.github.xchacha20_poly1305.kpuri.buildUrl

val url = buildUrl("https") {
    host = "example.com"
    addPathSegment("files")
    addPathSegment("a/b c.txt")       // one segment: '/' becomes %2F
    addQueryParameter("q", "a&b=c")   // '&' and '=' are encoded
    addQueryParameter("flag", null)   // no '=': "flag"
    fragment = "top"
}
// https://example.com/files/a%2Fb%20c.txt?q=a%26b%3Dc&flag#top
```

`encoded*` setters (`encodedPath`, `encodedQuery`, ...) and `addEncodedQueryParameter` take text that may already contain `%XX`. They keep valid escapes and encode anything else, the same way the parser does.

Path helpers:

- `addPathSegment(segment)` appends one segment and encodes `/` inside it.
- `addPathSegments("a/b/c")` appends several segments and keeps the slashes between them.

Query helpers:

- `addQueryParameter(name, value)` appends a parameter; a `null` value omits the `=`.
- `setQueryParameter(name, value)` replaces the first match, removes later ones, or appends when the name is absent.
- `removeQueryParameter(name)` removes every match; the `?` is dropped when no parameter remains.

Parameters that are not touched keep their original encoding.

### Modifying an existing URI

`Url` is immutable. `newBuilder()` returns a builder filled with its components:

```kotlin
val updated = url.newBuilder().apply {
    port = "8443"
    setQueryParameter("q", "new")
    removeQueryParameter("flag")
}.build()
// https://example.com:8443/files/a%2Fb%20c.txt?q=new#top
```

### Validation

`build()` checks the result and throws `IllegalArgumentException` when it is not a valid URI:

```kotlin
buildUrl("http") { port = "80" }      // host required
buildUrl("http") { path = "//x" }     // path starts with '//'
```

It also applies the normalizations RFC 3986 requires: a non-empty path gets a leading `/` when there is a host (`host = "h"; path = "x"` builds `http://h/x`), and a password without a username is written as `:password@`.

## Hosts

IP-literals are stored without brackets. `toString()` adds them back. In the builder, a host is treated as an IP-literal when it is written in brackets or contains `:`. IPv6 zone identifiers follow [RFC 6874](https://www.rfc-editor.org/rfc/rfc6874), so `%` in a zone is encoded as `%25`:

```kotlin
buildUrl("http") {
    host = "fe80::1%eth0"
    port = "8080"
}
// http://[fe80::1%25eth0]:8080

Url.parse("http://[fe80::1%25eth0]:8080/").host // "fe80::1%eth0"
```

IPvFuture literals (`[v1.x]`) are supported as well. Registered names are not case-folded or converted to Punycode.

## Ports

`port` is a `String?`, not an `Int`. `null` means there is no port, and `""` means the authority ends with a bare `:`. By default the port must consist of digits. `UrlOptions(validatePort = false)` keeps the port text verbatim, which allows non-numeric forms such as a port range:

```kotlin
import io.github.xchacha20_poly1305.kpuri.UrlOptions

val options = UrlOptions(validatePort = false)
Url.parse("hysteria2://example.com:443,7788-8899/", options).port // "443,7788-8899"
```

`buildUrl` and the parse functions all take `options`. A `Url` remembers the options it was created with, and `newBuilder()` reuses them.

## Equality

Two `Url`s are equal when their `toString()` forms are equal. kpuri does not apply further RFC 3986 §6 normalization: `http://h/p` and `http://h/p?` are different, and so are `%7E` and `~`.

## License

[Apache License 2.0](LICENSE)
