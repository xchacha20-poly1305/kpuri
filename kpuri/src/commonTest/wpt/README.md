```markdown
# WPT URL fixtures

Source: https://github.com/web-platform-tests/wpt/blob/3b3e763e92976d6df99ad9972c9b29c1b7bf2cef/url/resources/urltestdata.json

`urltestdata.json` is vendored unchanged under the upstream BSD license. This suite runs a reviewed 131-case subset and does not claim WHATWG URL conformance or full WPT coverage.

`expectations.json` selects cases by array index, records `input` to detect index drift, and requires a `reason`.
- **Omitted `expected`**: Uses WPT's `href` (or expects rejection if absent), and verifies populated WPT component getters. Empty WPT getters are ignored because WHATWG conflates absent and empty values.
- **String `expected`**: Uses kpuri's reviewed serialization.
- **`null` `expected`**: Expects `UrlSyntaxException`.

Never regenerate expectations from the implementation under test.

### RFC 3986 vs. WHATWG Differences

kpuri parses RFC 3986 references without a base URL (`base` is ignored). Key divergences from WHATWG in this subset:
- Preserves empty components, port/IPv6 spelling, dot segments, and `file` authorities.
- Does not infer authorities for special schemes.
- Percent-encodes registered names (no IDNA), malformed escapes, and extra `#` delimiters.
- Rejects control characters instead of stripping them.

### Code Generation

After editing `expectations.json`, run:

```shell
python3 scripts/generate_wpt_tests.py
```

Commit the generated `WptUrlTest.kt`. Compiling fixtures directly into Kotlin avoids runtime JSON dependencies and platform resource loaders across KMP targets. Each valid case checks serialization, reparse stability, and builder round-tripping.

To update upstream fixtures, fetch `urltestdata.json` and `LICENSE.md` from a specific WPT commit, update the source URL above, review `expectations.json`, and regenerate.
