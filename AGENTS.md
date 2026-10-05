# kpuri

kpuri is a Kotlin Multiplatform RFC 3986 URI library (single module `:kpuri`, package `io.github.xchacha20_poly1305.kpuri`). All code is in `commonMain`/`commonTest`; there are no platform-specific source sets.

## Commands

```sh
./gradlew :kpuri:jvmTest                     # fastest test run
./gradlew :kpuri:jvmTest --tests 'io.github.xchacha20_poly1305.kpuri.UrlParserTest'
./gradlew :kpuri:jvmTest --tests '*UrlParserTest.someTestName'
./gradlew allTests                           # every target this host can build (what CI runs)
./gradlew :kpuri:jsNodeTest :kpuri:wasmJsNodeTest :kpuri:linuxX64Test
```

- Browser test tasks are disabled; JS and Wasm tests run under Node.js only.
- Apple targets only build on macOS. On Linux they are skipped (`kotlin.native.ignoreDisabledTargets=true`).
- No linter is configured beyond `kotlin.code.style=official`. `explicitApi()` is on, so every public declaration needs an explicit visibility modifier.
- JVM bytecode targets Java 8.

## Release

`.github/workflows/test.yml` runs `allTests` on macOS for every branch push and pull request, except those that change only `.md` files. `.github/workflows/publish.yml` calls it first, then publishes to Maven Central; it runs only on a `v*` tag push or a manual dispatch. A manual dispatch on a branch publishes the `version` in `gradle.properties` (a `-SNAPSHOT`). A `v<version>` tag publishes and releases that version via `-Pversion=`; snapshot tags are rejected.

## Architecture

Public API: `Url` (immutable value), `UrlBuilder` (obtained only via `buildUrl(scheme) { ... }` or `Url.newBuilder()`), `UrlOptions`, `UrlSyntaxException`.

**One validation path for parsing and building.** `UrlParser` only splits the input on RFC 3986 Appendix B boundaries and rejects what cannot be split (control characters, unclosed `[`, junk after an IP-literal). It then hands raw component slices to `assembleUrl` (`UrlAssembly.kt`). `UrlBuilder.build()` hands its already-encoded components to the same `assembleUrl`. That function owns all structural checks (scheme, host, port, authority/path rules) and normalizations (empty username implied by a password, leading `/` on a path with an authority). Errors come back as `UrlComponentError` with a component-relative index; the parser converts them to `UrlSyntaxException` with an index into the input, the builder to `IllegalArgumentException`. New structural rules belong in `assembleUrl`, not in the parser or builder.

**Encoded storage, decoded reads.** `Url` stores every component percent-encoded and decodes on access (`host` vs `encodedHost`, etc.). `toString()` reassembles the stored text, so parsing valid input and writing it back is lossless; `equals`/`hashCode` compare the `toString()` form. `null` means a component is absent, `""` means present but empty — preserve this distinction.

**Two encode modes** (`PercentEncoding.kt`): `percentEncode(set, keepEscapes = true)` keeps valid `%XX` and encodes anything else (used by `encoded*` setters and the parser); `keepEscapes = false` encodes every `%` (used by decoded setters). Per-component allowed sets live in `UrlChars`, built from `AsciiSet` 128-bit masks.

**Query handling.** The query is split on `&` into `EncodedQueryParameter` pieces, keeping empty pieces so the query round-trips. A piece with an empty name and no `=` is a separator, not a parameter (`isRealQueryParameter`). Decoded query reads treat `+` as space.

**Hosts.** IP-literals are stored without brackets and flagged by `hostIsIpLiteral`; `toString()` re-adds the brackets. The builder treats a host as an IP-literal when it was bracketed or contains `:`. IPv6 zone ids follow RFC 6874 (`%25`).

**Port.** `UrlOptions(validatePort = false)` keeps the port text verbatim instead of requiring digits.

## Tests

`UrlAssertions.kt` provides `assertRoundTrip` (input is already canonical: parse → `toString()` is identical and reparse is equal) and `assertReparseStable` (parsing may rewrite the input, but a second parse does not). Use these for parser cases instead of hand-written comparisons.
