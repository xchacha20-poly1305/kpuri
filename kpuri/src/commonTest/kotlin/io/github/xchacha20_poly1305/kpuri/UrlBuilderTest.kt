package io.github.xchacha20_poly1305.kpuri

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlBuilderTest {
    @Test
    fun queryParameterCharsetExcludesSeparators() {
        assertFalse('&' in UrlChars.QUERY_PARAMETER)
        assertFalse('=' in UrlChars.QUERY_PARAMETER)
        assertFalse('+' in UrlChars.QUERY_PARAMETER)
        assertFalse('#' in UrlChars.QUERY_PARAMETER)
        assertTrue('?' in UrlChars.QUERY_PARAMETER)
        assertTrue('/' in UrlChars.QUERY_PARAMETER)
    }

    @Test
    fun encodesEachComponent() {
        val url = buildUrl("https") {
            username = "user:name 名"
            password = "pa:ss 词"
            host = "主机.example"
            port = "443"
            path = "a/b c"
            fragment = "frag ment#"
            addQueryParameter("a b", "c d")
            addQueryParameter("x&y", "p=q+r#s")
            addQueryParameter("q?r", "s/t")
        }
        assertEquals("user%3Aname%20%E5%90%8D", url.encodedUsername)
        assertEquals("user:name 名", url.username)
        assertEquals("pa:ss%20%E8%AF%8D", url.encodedPassword)
        assertEquals("pa:ss 词", url.password)
        assertEquals("%E4%B8%BB%E6%9C%BA.example", url.encodedHost)
        assertEquals("主机.example", url.host)
        assertEquals("443", url.port)
        assertEquals("/a/b%20c", url.encodedPath)
        assertEquals("/a/b c", url.path)
        assertEquals("a%20b=c%20d&x%26y=p%3Dq%2Br%23s&q?r=s/t", url.encodedQuery)
        assertEquals("c d", url.queryParameter("a b"))
        assertEquals("p=q+r#s", url.queryParameter("x&y"))
        assertEquals("s/t", url.queryParameter("q?r"))
        assertEquals("frag%20ment%23", url.encodedFragment)
        assertEquals("frag ment#", url.fragment)
        assertEquals(
            "https://user%3Aname%20%E5%90%8D:pa:ss%20%E8%AF%8D@%E4%B8%BB%E6%9C%BA.example:443/a/b%20c?a%20b=c%20d&x%26y=p%3Dq%2Br%23s&q?r=s/t#frag%20ment%23",
            url.toString(),
        )
    }

    @Test
    fun decodedSettersEncodeEveryPercent() {
        val url = buildUrl("a") {
            username = "a%20b"
            password = "50%off"
            host = "a%20b"
            path = "a%2Fb"
            fragment = "100%"
        }
        assertEquals("a%2520b", url.encodedUsername)
        assertEquals("a%20b", url.username)
        assertEquals("50%25off", url.encodedPassword)
        assertEquals("50%off", url.password)
        assertEquals("a%2520b", url.encodedHost)
        assertEquals("a%20b", url.host)
        assertEquals("/a%252Fb", url.encodedPath)
        assertEquals("/a%2Fb", url.path)
        assertEquals("100%25", url.encodedFragment)
        assertEquals("100%", url.fragment)
    }

    @Test
    fun encodedSettersKeepValidEscapesAndEncodeTheRest() {
        val url = buildUrl("a") {
            encodedUsername = "a%20b 名"
            encodedPassword = "50%off"
            encodedHost = "%E4%B8%AD%E5%9B%BD.cn"
            encodedPath = "a%2Fb c"
            encodedQuery = "a=1 2&b=%7E"
            encodedFragment = "50%off#"
        }
        assertEquals("a%20b%20%E5%90%8D", url.encodedUsername)
        assertEquals("a b 名", url.username)
        assertEquals("50%25off", url.encodedPassword)
        assertEquals("50%off", url.password)
        assertEquals("%E4%B8%AD%E5%9B%BD.cn", url.encodedHost)
        assertEquals("中国.cn", url.host)
        assertEquals("/a%2Fb%20c", url.encodedPath)
        assertEquals("/a/b c", url.path)
        assertEquals("a=1%202&b=%7E", url.encodedQuery)
        assertEquals("~", url.queryParameter("b"))
        assertEquals("50%25off%23", url.encodedFragment)
        assertEquals("50%off#", url.fragment)

        val kept = buildUrl("a") {
            host = "h"
            encodedPassword = "a%20b"
            encodedFragment = "%7e"
        }
        assertEquals("a%20b", kept.encodedPassword)
        assertEquals("a b", kept.password)
        assertEquals("%7e", kept.encodedFragment)
        assertEquals("~", kept.fragment)
    }

    @Test
    fun gettersMatchTheUrlTheyBuild() {
        lateinit var builder: UrlBuilder
        val url = buildUrl("HTTP") {
            builder = this
            username = "a%20b"
            password = "x:y"
            host = "[::1]"
            port = "80"
            addPathSegment("a/b")
            addQueryParameter("n", "v")
            fragment = "f"
        }
        assertEquals("http", builder.scheme)
        assertEquals(url.username, builder.username)
        assertEquals(url.encodedUsername, builder.encodedUsername)
        assertEquals(url.password, builder.password)
        assertEquals(url.encodedPassword, builder.encodedPassword)
        assertEquals(url.host, builder.host)
        assertEquals(url.encodedHost, builder.encodedHost)
        assertEquals(url.port, builder.port)
        assertEquals(url.fragment, builder.fragment)
        assertEquals(url.encodedFragment, builder.encodedFragment)
        assertEquals(url.encodedQuery, builder.encodedQuery)
        // build() inserts the authority's leading '/'; the builder still holds the segment it was given.
        assertEquals("a%2Fb", builder.encodedPath)
        assertEquals("/a%2Fb", url.encodedPath)
    }

    @Test
    fun hostBracketsAndZoneId() {
        val literal = buildUrl("https") {
            host = "[::1]"
            port = "443"
        }
        assertEquals("::1", literal.host)
        assertEquals("::1", literal.encodedHost)
        assertEquals("https://[::1]:443", literal.toString())

        val bare = buildUrl("https") { host = "::1" }
        assertEquals("https://[::1]", bare.toString())

        val encoded = buildUrl("https") { encodedHost = "[fe80::1%25eth0]" }
        assertEquals("fe80::1%25eth0", encoded.encodedHost)
        assertEquals("fe80::1%eth0", encoded.host)
        assertEquals("https://[fe80::1%25eth0]", encoded.toString())

        val decodedZone = buildUrl("https") { host = "fe80::1%eth0" }
        assertEquals("fe80::1%25eth0", decodedZone.encodedHost)
        assertEquals("fe80::1%eth0", decodedZone.host)

        val name = buildUrl("https") { host = "example.com" }
        assertEquals("https://example.com", name.toString())
        val v4 = buildUrl("https") { host = "1.2.3.4" }
        assertEquals("https://1.2.3.4", v4.toString())

        // Brackets select IP-literal rules. Without them this is a reg-name, which is not rejected.
        assertEquals("999.1.1.1", buildUrl("a") { host = "999.1.1.1" }.host)
        assertBuildThrows("invalid IP literal") { buildUrl("a") { host = "[999.1.1.1]" } }
        assertBuildThrows("invalid IP literal") { buildUrl("a") { host = "[example.com]" } }
        assertBuildThrows("invalid IP literal") { buildUrl("a") { host = "::1::2" } }
        assertBuildThrows("invalid IP literal") { buildUrl("a") { encodedHost = "[v1.]" } }

        val future = buildUrl("a") { host = "[v1.x]" }
        assertEquals("v1.x", future.encodedHost)
        assertEquals("a://[v1.x]", future.toString())
        assertEquals("a://[v1.x]", future.newBuilder().build().toString())
        assertEquals("a://[v1.x]", Url.parse(future.toString()).toString())

        assertEquals("a://[v1.x]", Url.parse("a://[v1.x]").toString())
        assertEquals("a://[v1.x]", Url.parse("a://[v1.x]").newBuilder().build().toString())
        assertEquals("a://[v1.x]:80/p", Url.parse("a://[v1.x]:80/p").toString())
        assertEquals("a://[v1.x]:80/p", Url.parse("a://[v1.x]:80/p").newBuilder().build().toString())
    }

    @Test
    fun portIsVerbatim() {
        val empty = buildUrl("a") { host = "h"; port = "" }
        assertEquals("", empty.port)
        assertEquals("a://h:", empty.toString())
        assertNull(buildUrl("a") { host = "h" }.port)

        val hopping = buildUrl("hysteria2", UrlOptions(validatePort = false)) {
            username = "auth"
            host = "h"
            port = "443,7788-8899"
            path = "/"
            addQueryParameter("sni", "a")
        }
        assertEquals("hysteria2://auth@h:443,7788-8899/?sni=a", hopping.toString())
    }

    @Test
    fun addPathSegmentJoinsWithOneSlash() {
        val builder = Url.parse("a:").newBuilder()
        assertEquals("", builder.encodedPath)

        builder.addPathSegment("a")
        assertEquals("a", builder.encodedPath)
        builder.addPathSegment("b c")
        assertEquals("a/b%20c", builder.encodedPath)
        builder.addPathSegment("")
        assertEquals("a/b%20c/", builder.encodedPath)
        builder.addPathSegment("d")
        assertEquals("a/b%20c/d", builder.encodedPath)
        builder.addPathSegment("")
        assertEquals("a/b%20c/d/", builder.encodedPath)
        builder.addPathSegment("")
        assertEquals("a/b%20c/d/", builder.encodedPath)

        val slash = Url.parse("a:").newBuilder()
        slash.addPathSegment("a/b")
        assertEquals("a%2Fb", slash.encodedPath)
        slash.addPathSegment("")
        assertEquals("a%2Fb/", slash.encodedPath)

        val empty = Url.parse("a:").newBuilder()
        empty.addPathSegment("")
        assertEquals("", empty.encodedPath)

        val absolute = Url.parse("a://h/a").newBuilder()
        absolute.addPathSegment("b")
        assertEquals("/a/b", absolute.encodedPath)
        val directory = Url.parse("a://h/a/").newBuilder()
        directory.addPathSegment("b")
        assertEquals("/a/b", directory.encodedPath)
    }

    @Test
    fun addPathSegmentsKeepsItsOwnSlashes() {
        assertEquals("a/b", pathAfter("") { addPathSegments("a/b") })
        assertEquals("/a/b", pathAfter("") { addPathSegments("/a/b") })
        assertEquals("a/b/", pathAfter("") { addPathSegments("a/b/") })
        assertEquals("a//b", pathAfter("") { addPathSegments("a//b") })
        assertEquals("/", pathAfter("") { addPathSegments("/") })
        assertEquals("", pathAfter("") { addPathSegments("") })
        assertEquals("/x/a/b/", pathAfter("/x") { addPathSegments("a/b/") })
        assertEquals("/a/b", pathAfter("/a/") { addPathSegments("b") })
        assertEquals("/a/b", pathAfter("/a") { addPathSegments("/b") })
        assertEquals("/a/b", pathAfter("/a/") { addPathSegments("/b") })
        assertEquals("/a/", pathAfter("/a") { addPathSegments("/") })
        assertEquals("/a/", pathAfter("/a/") { addPathSegments("/") })
        assertEquals("/a//b", pathAfter("/a") { addPathSegments("//b") })
        assertEquals("a%20b/c%252F", pathAfter("") { addPathSegments("a b/c%2F") })
    }

    @Test
    fun buildInsertsSlashOnlyForAnAuthority() {
        assertEquals("a://h/p", buildUrl("a") { host = "h"; path = "p" }.toString())
        assertEquals("a://h/p", buildUrl("a") { host = "h"; path = "/p" }.toString())
        assertEquals("a://h", buildUrl("a") { host = "h"; path = "" }.toString())
        assertEquals("mailto:user@example.com", buildUrl("mailto") { path = "user@example.com" }.toString())
        assertEquals("a://h/a%2Fb", buildUrl("a") { host = "h"; addPathSegment("a/b") }.toString())
        assertEquals("https://user@example.com:8443/?q=1", buildUrl("https") {
            username = "user"
            host = "example.com"
            port = "8443"
            path = "/"
            addQueryParameter("q", "1")
        }.toString())
    }

    @Test
    fun queryEditsKeepUntouchedTextAndDropAnEmptyQuery() {
        val queries = listOf("", "a", "a=", "a=1", "a=1&", "a=1&&b=2", "=2", "a+b=c+%7E")
        for (query in queries) assertEquals(query, splitEncodedQuery(query).joinEncodedQuery(), query)

        val original = Url.parse("a://h/p?a+b=c+d&keep=%7E&a%20b=2")
        val removed = original.newBuilder().apply { removeQueryParameter("a b") }.build()
        assertEquals("keep=%7E", removed.encodedQuery)
        assertNull(removed.queryParameter("a b"))
        assertEquals("~", removed.queryParameter("keep"))

        val replaced = original.newBuilder().apply { setQueryParameter("a b", "z") }.build()
        assertEquals("a%20b=z&keep=%7E", replaced.encodedQuery)
        assertEquals(listOf("z"), replaced.queryParameterValues("a b"))

        val appended = Url.parse("a://h?keep=%7E").newBuilder().apply { setQueryParameter("n", "1") }.build()
        assertEquals("keep=%7E&n=1", appended.encodedQuery)

        val bare = Url.parse("a://h?a+b=1").newBuilder().apply { setQueryParameter("a b", null) }.build()
        assertEquals("a%20b", bare.encodedQuery)
        assertEquals("", bare.queryParameter("a b"))

        assertNull(Url.parse("a://h?a=1").newBuilder().apply { removeQueryParameter("a") }.build().encodedQuery)
        assertEquals("a://h", Url.parse("a://h?a=1").newBuilder().apply { removeQueryParameter("a") }.build().toString())
        assertNull(Url.parse("a://h?a=1&").newBuilder().apply { removeQueryParameter("a") }.build().encodedQuery)
        assertNull(Url.parse("a://h?&a=1&").newBuilder().apply { removeQueryParameter("a") }.build().encodedQuery)
        assertNull(Url.parse("a://h?a=1&&").newBuilder().apply { removeQueryParameter("a") }.build().encodedQuery)
        assertNull(Url.parse("https://example.com/index.html?lang=en").newBuilder().apply { removeQueryParameter("lang") }.build().encodedQuery)
        assertEquals("https://example.com/index.html", Url.parse("https://example.com/index.html?lang=en").newBuilder().apply { removeQueryParameter("lang") }.build().toString())

        val keptHole = Url.parse("a://h?a=1&&b=2").newBuilder().apply { removeQueryParameter("a") }.build()
        assertEquals("&b=2", keptHole.encodedQuery)
        val both = Url.parse("a://h?a=1&&b=2").newBuilder().apply {
            removeQueryParameter("a")
            removeQueryParameter("b")
        }.build()
        assertNull(both.encodedQuery)

        val separators = Url.parse("a://h?&").newBuilder().apply { removeQueryParameter("absent") }.build()
        assertEquals("&", separators.encodedQuery)
        // `?&` is two empty pieces. Appending keeps both, so the result is `&&a=1`.
        val added = Url.parse("a://h?&").newBuilder().apply { addQueryParameter("a", "1") }.build()
        assertEquals("&&a=1", added.encodedQuery)

        val encodedAdd = buildUrl("a") {
            host = "h"
            addEncodedQueryParameter("a%20b", "c d")
            addEncodedQueryParameter("50%", null)
            addEncodedQueryParameter("a+b", "c=d")
        }
        assertEquals("a%20b=c%20d&50%25&a%2Bb=c%3Dd", encodedAdd.encodedQuery)
        assertEquals("c d", encodedAdd.queryParameter("a b"))
        assertEquals("", encodedAdd.queryParameter("50%"))
        assertEquals("c=d", encodedAdd.queryParameter("a+b"))
    }

    @Test
    fun passwordWithoutUsernameIsEmpty() {
        val url = buildUrl("a") {
            host = "h"
            password = "p"
        }
        assertEquals("", url.username)
        assertEquals("", url.encodedUsername)
        assertEquals("p", url.password)
        assertEquals("a://:p@h", url.toString())

        val emptyPassword = buildUrl("a") {
            host = "h"
            username = "u"
            password = ""
        }
        assertEquals("a://u:@h", emptyPassword.toString())
    }

    @Test
    fun schemeIsLowercased() {
        assertEquals("http", buildUrl("HTTP") { host = "H" }.scheme)
        assertEquals("http://H", buildUrl("HTTP") { host = "H" }.toString())
    }

    @Test
    fun newBuilderRoundTrip() {
        val samples = listOf(
            "http://www.ics.uci.edu/pub/ietf/uri/#Related",
            "a://u:p@h:80/a%20b?a+b=c+%7E&x=1&&y#f%20",
            "a://[::1]:443/p",
            "a://[fe80::1%25eth0]/",
            "a://[v1.x]",
            "/index.html?lang=en",
            "a://h?",
            "a://:@h",
            "a://h:",
            "mailto:user@example.com",
            "",
            "ftp://u:p@ss@h:21",
            "a://u:50%25off@h",
            "HTTP://h",
        )
        for (sample in samples) {
            val url = Url.parseReference(sample)
            assertEquals(url, url.newBuilder().build(), sample)
            assertEquals(url.toString(), url.newBuilder().build().toString(), sample)
        }

        val hopping = Url.parse("hysteria2://auth@h:443,7788-8899/?sni=a", UrlOptions(validatePort = false))
        assertEquals(hopping, hopping.newBuilder().build())
        val edited = hopping.newBuilder().apply { port = "1,2" }.build()
        assertEquals("1,2", edited.port)
    }

    @Test
    fun buildRejectsEachStructuralError() {
        assertBuildThrows("scheme required") { buildUrl("http") { scheme = null; path = "/index.html" } }
        assertBuildThrows("scheme required") { Url.parse("http://h").newBuilder().apply { scheme = null }.build() }
        assertBuildThrows("invalid scheme") { buildUrl("") {} }
        assertBuildThrows("invalid scheme") { buildUrl("1http") {} }
        assertBuildThrows("invalid scheme") { buildUrl("ht_tp") {} }
        assertBuildThrows("invalid scheme") { buildUrl("a") { scheme = "" } }

        assertBuildThrows("host required") { buildUrl("a") { username = "u" } }
        assertBuildThrows("host required") { buildUrl("a") { password = "p" } }
        assertBuildThrows("host required") { buildUrl("a") { username = ""; password = "" } }
        assertBuildThrows("host required") { buildUrl("a") { port = "80" } }
        assertBuildThrows("host required") { buildUrl("a") { port = "" } }

        assertBuildThrows("invalid IP literal") { buildUrl("a") { host = "[::1::]" } }
        assertBuildThrows("invalid port") { buildUrl("a") { host = "h"; port = "65536" } }
        assertBuildThrows("invalid port") { buildUrl("a") { host = "h"; port = "443,1-2" } }
        assertBuildThrows("invalid port") { buildUrl("a") { host = "h"; port = "80a" } }
        assertBuildThrows("invalid port") {
            buildUrl("a", UrlOptions(validatePort = false)) { host = "h"; port = "443/1" }
        }
        assertBuildThrows("invalid port") {
            buildUrl("a", UrlOptions(validatePort = false)) { host = "h"; port = "4\n" }
        }

        assertBuildThrows("path starts with '//'") { buildUrl("mailto") { path = "//h" } }
        assertBuildThrows("path starts with '//'") { reference { encodedPath = "//h/p" } }
        assertEquals("a://h//x", buildUrl("a") { host = "h"; encodedPath = "//x" }.toString())

        assertBuildThrows("colon in first path segment") { reference { path = "a:b" } }
        assertBuildThrows("colon in first path segment") { reference { encodedPath = "a:b/c" } }
        assertEquals("a/b:c", reference { path = "a/b:c" }.toString())
        assertEquals("/a:b", reference { path = "/a:b" }.toString())
        assertEquals("./a:b", reference { path = "./a:b" }.toString())
        assertEquals("a:a:b", buildUrl("a") { path = "a:b" }.toString())
        assertEquals("//h/a:b", reference { host = "h"; path = "a:b" }.toString())

        val relative = Url.parseReference("/index.html?lang=en")
        assertNull(relative.newBuilder().build().scheme)
        assertEquals(relative, relative.newBuilder().apply { scheme = "http"; scheme = null }.build())
    }

    private fun pathAfter(initial: String, block: UrlBuilder.() -> Unit): String =
        Url.parse("a:").newBuilder().apply { encodedPath = initial }.apply(block).encodedPath

    private fun reference(block: UrlBuilder.() -> Unit): Url =
        Url.parseReference("").newBuilder().apply(block).build()

    private fun assertBuildThrows(message: String, block: () -> Unit) {
        val error = assertFailsWith<IllegalArgumentException>(block = block)
        assertFalse(error is UrlSyntaxException, error.toString())
        assertEquals(message, error.message)
    }
}
