package io.github.xchacha20_poly1305.kpuri

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UrlParserTest {
    private fun assertComponents(
        input: String,
        scheme: String?,
        username: String? = null,
        password: String? = null,
        host: String? = null,
        port: String? = null,
        path: String = "",
        query: String? = null,
        fragment: String? = null,
        options: UrlOptions = UrlOptions(),
        output: String = input,
    ) {
        val url = Url.parseReference(input, options)
        assertEquals(output, url.toString(), "output of $input")
        assertReparseStable(input, options)
        assertEquals(scheme, url.scheme, "scheme of $input")
        assertEquals(username, url.encodedUsername, "username of $input")
        assertEquals(password, url.encodedPassword, "password of $input")
        assertEquals(host, url.encodedHost, "host of $input")
        assertEquals(port, url.port, "port of $input")
        assertEquals(path, url.encodedPath, "path of $input")
        assertEquals(query, url.encodedQuery, "query of $input")
        assertEquals(fragment, url.encodedFragment, "fragment of $input")
    }

    @Test
    fun rfc3986Examples() {
        assertComponents(
            "http://www.ics.uci.edu/pub/ietf/uri/#Related",
            scheme = "http", host = "www.ics.uci.edu", path = "/pub/ietf/uri/", fragment = "Related",
        )
        assertComponents("ftp://ftp.is.co.za/rfc/rfc1808.txt", scheme = "ftp", host = "ftp.is.co.za", path = "/rfc/rfc1808.txt")
        assertComponents("http://www.ietf.org/rfc/rfc2396.txt", scheme = "http", host = "www.ietf.org", path = "/rfc/rfc2396.txt")
        assertComponents("ldap://[2001:db8::7]/c=GB?objectClass?one", scheme = "ldap", host = "2001:db8::7", path = "/c=GB", query = "objectClass?one")
        assertComponents("mailto:John.Doe@example.com", scheme = "mailto", path = "John.Doe@example.com")
        assertComponents("news:comp.infosystems.www.servers.unix", scheme = "news", path = "comp.infosystems.www.servers.unix")
        assertComponents("tel:+1-816-555-1212", scheme = "tel", path = "+1-816-555-1212")
        assertComponents("telnet://192.0.2.16:80/", scheme = "telnet", host = "192.0.2.16", port = "80", path = "/")
        assertComponents("urn:oasis:names:specification:docbook:dtd:xml:4.1.2", scheme = "urn", path = "oasis:names:specification:docbook:dtd:xml:4.1.2")
        assertComponents("foo://example.com:8042/over/there?name=ferret#nose", scheme = "foo", host = "example.com", port = "8042", path = "/over/there", query = "name=ferret", fragment = "nose")
    }

    @Test
    fun absentAndEmpty() {
        assertComponents("a:", scheme = "a")
        assertComponents("a://", scheme = "a", host = "")
        assertComponents("a://h", scheme = "a", host = "h")
        assertComponents("a://h?", scheme = "a", host = "h", query = "")
        assertComponents("a://h#", scheme = "a", host = "h", fragment = "")
        assertComponents("a://h:", scheme = "a", host = "h", port = "")
        assertComponents("a://u@h", scheme = "a", username = "u", host = "h")
        assertComponents("a://@h", scheme = "a", username = "", host = "h")
        assertComponents("a://:@h", scheme = "a", username = "", password = "", host = "h")
        assertComponents("a://u:p@h", scheme = "a", username = "u", password = "p", host = "h")
    }

    @Test
    fun relativeReferences() {
        assertComponents("/index.html?lang=en", scheme = null, path = "/index.html", query = "lang=en")
        assertComponents("//h/p", scheme = null, host = "h", path = "/p")
        assertComponents("", scheme = null)
        assertComponents("#f", scheme = null, fragment = "f")
        assertComponents("a/b:c", scheme = null, path = "a/b:c")
    }

    @Test
    fun schemeIsLowercased() {
        assertEquals("http", Url.parse("HTTP://h").scheme)
        assertEquals("http://h", Url.parse("HTTP://h").toString())
    }

    @Test
    fun userinfoSplitsAtLastAt() {
        assertComponents("ftp://u:p@ss@h:21", scheme = "ftp", username = "u", password = "p%40ss", host = "h", port = "21", output = "ftp://u:p%40ss@h:21")
        assertComponents("ftp://u:p:q@h", scheme = "ftp", username = "u", password = "p:q", host = "h")
        assertComponents("http://h/a@b", scheme = "http", host = "h", path = "/a@b")
        assertComponents("a://h:1/2@x:3", scheme = "a", host = "h", port = "1", path = "/2@x:3")
    }

    @Test
    fun ipLiteralHost() {
        assertComponents("a://[::1]:80", scheme = "a", host = "::1", port = "80")
        assertComponents("a://[fe80::1%25eth0]", scheme = "a", host = "fe80::1%25eth0")
        assertComponents("a://[v1.x]", scheme = "a", host = "v1.x")
    }

    @Test
    fun portHopping() {
        val lenient = UrlOptions(validatePort = false)
        assertComponents("hysteria2://auth@h:443,7788-8899/?sni=a", scheme = "hysteria2", username = "auth", host = "h", port = "443,7788-8899", path = "/", query = "sni=a", options = lenient)
        assertComponents("hysteria2://[::1]:443,7788-8899", scheme = "hysteria2", host = "::1", port = "443,7788-8899", options = lenient)
        assertComponents("a://h:65536", scheme = "a", host = "h", port = "65536", options = lenient)
    }

    @Test
    fun lenientCharactersAreEncoded() {
        assertComponents("a://u:50%off@h", scheme = "a", username = "u", password = "50%25off", host = "h", output = "a://u:50%25off@h")
        assertComponents("a://h/a b?x=1 2#中国", scheme = "a", host = "h", path = "/a%20b", query = "x=1%202", fragment = "%E4%B8%AD%E5%9B%BD", output = "a://h/a%20b?x=1%202#%E4%B8%AD%E5%9B%BD")
        assertComponents("a://h#a#b", scheme = "a", host = "h", fragment = "a%23b", output = "a://h#a%23b")
        assertComponents("a://h/%7e?%41", scheme = "a", host = "h", path = "/%7e", query = "%41")
        assertComponents("a://中国.cn", scheme = "a", host = "%E4%B8%AD%E5%9B%BD.cn", output = "a://%E4%B8%AD%E5%9B%BD.cn")
        assertComponents("a://h?a=\"|\"", scheme = "a", host = "h", query = "a=%22%7C%22", output = "a://h?a=%22%7C%22")
    }

    @Test
    fun validInputRoundTrips() {
        val inputs = listOf(
            "a:", "a://", "a://h?", "a://:@h", "a://h:", "a://[::1]:80/p?q#f",
            "https://user:pass@example.com:8443/a/b?x=1&y=%E4%B8%AD#top",
            "ftp://ftp.example.com/pub/file%20name.txt;type=i",
            "a://h/%7e?%41",
            "a://h?a=1&&b=2&c&=&", "a://h?&", "a://h?+", "a://h?a+b=c+d&a%20b=%2B&%E4%B8%AD=%FF&%26=%3D",
            "a://%FF:%FF@%FF/%FF#%FF", "a://u:50%25off@h",
        )
        for (input in inputs) assertRoundTrip(input)
    }

    private fun assertFails(input: String, index: Int, options: UrlOptions = UrlOptions(), parse: (String, UrlOptions) -> Url = Url::parse) {
        val e = assertFailsWith<UrlSyntaxException>(input) { parse(input, options) }
        assertEquals(index, e.index, "index for $input: ${e.reason}")
        assertEquals(input, e.input)
    }

    @Test
    fun failures() {
        assertFails("//h", 0)
        assertFails("/index.html", 0)
        assertFails(":x", 0, parse = Url::parseReference)
        assertFails("1a://h", 0, parse = Url::parseReference)
        assertFails("h_t://h", 1)
        assertFails("a://h\n", 5)
        assertFails("a://h/\u0000", 6)
        assertFails("a://[::1", 4)
        assertFails("a://[::1/]", 4)
        assertFails("a://[::g]", 5)
        assertFails("a://[::1]x", 9)
        assertFails("a://h:65536", 10)
        assertFails("a://h:443,1-2", 9)
        assertFails("a://a b", 5)
        assertFails("a://a]b", 5)
    }

    @Test
    fun parseOrNull() {
        assertNull(Url.parseOrNull("/relative"))
        assertEquals("a://h", Url.parseOrNull("a://h").toString())
    }
}
