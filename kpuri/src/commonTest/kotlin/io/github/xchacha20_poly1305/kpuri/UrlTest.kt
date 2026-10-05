package io.github.xchacha20_poly1305.kpuri

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class UrlTest {
    @Test
    fun decodedScalarsAbsentAndEmpty() {
        val bare = Url.parse("a://h")
        assertNull(bare.username)
        assertNull(bare.password)
        assertEquals("h", bare.host)
        assertEquals("", bare.path)
        assertNull(bare.fragment)

        val emptyUser = Url.parse("a://@h")
        assertEquals("", emptyUser.username)
        assertNull(emptyUser.password)

        val emptyUserinfo = Url.parse("a://:@h")
        assertEquals("", emptyUserinfo.username)
        assertEquals("", emptyUserinfo.password)

        assertEquals("u", Url.parse("a://u:@h").username)
        assertEquals("", Url.parse("a://u:@h").password)
        assertEquals("", Url.parse("a://:p@h").username)
        assertEquals("p", Url.parse("a://:p@h").password)

        assertEquals("", Url.parse("a://h#").fragment)
        assertEquals("f", Url.parse("a://h#f").fragment)

        val opaque = Url.parse("mailto:user@example.com")
        assertNull(opaque.username)
        assertNull(opaque.host)
        assertEquals("user@example.com", opaque.path)
        assertNull(opaque.fragment)
    }

    @Test
    fun decodedScalarsPercentAndUtf8() {
        val url = Url.parse("a://%E4%B8%AD%E5%9B%BD:%61%20b@%E4%B8%AD%E5%9B%BD.example/%E4%B8%AD%2Fb#%E5%9B%BD")
        assertEquals("中国", url.username)
        assertEquals("a b", url.password)
        assertEquals("中国.example", url.host)
        assertEquals("/中/b", url.path)
        assertEquals("国", url.fragment)

        val malformed = Url.parse("a://%FF:%FF@%FF/%FF#%FF")
        assertEquals("\uFFFD", malformed.username)
        assertEquals("\uFFFD", malformed.password)
        assertEquals("\uFFFD", malformed.host)
        assertEquals("/\uFFFD", malformed.path)
        assertEquals("\uFFFD", malformed.fragment)

        val truncated = Url.parse("a://h/%E4%B8")
        assertEquals("/\uFFFD", truncated.path)

        // '+' is a space only in the query.
        val plus = Url.parse("a://a+b:c+d@e+f/g+h#i+j")
        assertEquals("a+b", plus.username)
        assertEquals("c+d", plus.password)
        assertEquals("e+f", plus.host)
        assertEquals("/g+h", plus.path)
        assertEquals("i+j", plus.fragment)
    }

    @Test
    fun pathSegments() {
        assertEquals(emptyList(), Url.parse("a:").pathSegments)
        assertEquals(emptyList(), Url.parse("a://h").pathSegments)
        assertEquals(listOf(""), Url.parse("a://h/").pathSegments)
        assertEquals(listOf("a", "b"), Url.parse("a://h/a/b").pathSegments)
        assertEquals(listOf("a", "b"), Url.parseReference("/a/b").pathSegments)
        assertEquals(listOf("a", "b"), Url.parseReference("a/b").pathSegments)
        assertEquals(listOf("a", "b", ""), Url.parse("a://h/a/b/").pathSegments)
        assertEquals(listOf("a", ""), Url.parseReference("a/").pathSegments)
        assertEquals(listOf("", ""), Url.parse("a://h//").pathSegments)

        val lossy = Url.parse("a://h/a%2Fb/c")
        assertEquals("/a/b/c", lossy.path)
        assertEquals(listOf("a/b", "c"), lossy.pathSegments)

        val decoded = Url.parse("a://h/%E4%B8%AD/%20%FF")
        assertEquals(listOf("中", " \uFFFD"), decoded.pathSegments)
        assertEquals("/中/ \uFFFD", decoded.path)

        assertEquals(listOf("a+b"), Url.parse("a://h/a+b").pathSegments)
        assertSame(decoded.pathSegments, decoded.pathSegments)
    }

    @Test
    fun queryParameterListKeepsEmptyPieces() {
        val noQuery = Url.parse("a://h")
        assertNull(noQuery.encodedQuery)
        assertEquals(emptyList(), noQuery.encodedQueryParameters)

        val emptyQuery = Url.parse("a://h?")
        assertEquals("", emptyQuery.encodedQuery)
        assertEquals(listOf(EncodedQueryParameter("", null)), emptyQuery.encodedQueryParameters)

        val url = Url.parse("a://h?a=1&&b=2&c&=&")
        assertEquals(
            listOf(
                EncodedQueryParameter("a", "1"),
                EncodedQueryParameter("", null),
                EncodedQueryParameter("b", "2"),
                EncodedQueryParameter("c", null),
                EncodedQueryParameter("", ""),
                EncodedQueryParameter("", null),
            ),
            url.encodedQueryParameters,
        )
        assertSame(url.encodedQueryParameters, url.encodedQueryParameters)
    }

    @Test
    fun queryParametersAbsentAndEmpty() {
        val noQuery = Url.parse("a://h")
        assertEquals(emptySet(), noQuery.queryParameterNames)
        assertNull(noQuery.queryParameter("a"))
        assertNull(noQuery.queryParameter(""))
        assertEquals(emptyList(), noQuery.queryParameterValues("a"))

        val emptyQuery = Url.parse("a://h?")
        assertEquals(emptySet(), emptyQuery.queryParameterNames)
        assertNull(emptyQuery.queryParameter(""))
        assertEquals(emptyList(), emptyQuery.queryParameterValues(""))

        val bare = Url.parse("a://h?a")
        assertEquals(setOf("a"), bare.queryParameterNames)
        assertEquals("", bare.queryParameter("a"))
        assertEquals(listOf(""), bare.queryParameterValues("a"))
        assertNull(bare.queryParameter("b"))
        assertEquals(emptyList(), bare.queryParameterValues("b"))

        assertEquals("", Url.parse("a://h?a=").queryParameter("a"))
        assertEquals(listOf(""), Url.parse("a://h?a=").queryParameterValues("a"))

        val holes = Url.parse("a://h?a=1&&b=2")
        assertEquals(listOf("a", "b"), holes.queryParameterNames.toList())
        assertNull(holes.queryParameter(""))
        assertEquals(emptyList(), holes.queryParameterValues(""))

        val separatorsOnly = Url.parse("a://h?&")
        assertEquals(emptySet(), separatorsOnly.queryParameterNames)
        assertNull(separatorsOnly.queryParameter(""))

        val emptyName = Url.parse("a://h?a=1&=2")
        assertEquals(listOf("a", ""), emptyName.queryParameterNames.toList())
        assertEquals("1", emptyName.queryParameter("a"))
        assertEquals("2", emptyName.queryParameter(""))
        assertEquals(listOf("2"), emptyName.queryParameterValues(""))

        val mixed = Url.parse("a://h?&=&")
        assertEquals(listOf(""), mixed.queryParameterNames.toList())
        assertEquals("", mixed.queryParameter(""))
        assertEquals(listOf(""), mixed.queryParameterValues(""))
    }

    @Test
    fun queryParameterOrderAndRepeats() {
        val url = Url.parse("a://h?b=1&a=2&b=3&a=1=x")
        assertEquals(listOf("b", "a"), url.queryParameterNames.toList())
        assertEquals("1", url.queryParameter("b"))
        assertEquals(listOf("1", "3"), url.queryParameterValues("b"))
        assertEquals("2", url.queryParameter("a"))
        assertEquals(listOf("2", "1=x"), url.queryParameterValues("a"))
        assertNull(url.queryParameter("c"))
        assertEquals(emptyList(), url.queryParameterValues("c"))
    }

    @Test
    fun queryParameterDecoding() {
        val url = Url.parse("a://h?a+b=c+d&a%20b=%2B&%E4%B8%AD=%FF&%26=%3D")
        assertEquals(listOf("a b", "中", "&"), url.queryParameterNames.toList())
        assertEquals("c d", url.queryParameter("a b"))
        assertEquals(listOf("c d", "+"), url.queryParameterValues("a b"))
        assertNull(url.queryParameter("a+b"))
        assertEquals("\uFFFD", url.queryParameter("中"))
        assertEquals("=", url.queryParameter("&"))

        assertEquals("国", Url.parse("a://h?中=国").queryParameter("中"))
        assertEquals("%E4%B8%AD=%E5%9B%BD", Url.parse("a://h?中=国").encodedQuery)

        assertEquals(" ", Url.parse("a://h?a=+").queryParameter("a"))
        assertEquals(" ", Url.parse("a://h?a=%20").queryParameter("a"))
        assertEquals("x", Url.parse("a://h?+=x").queryParameter(" "))
        assertNull(Url.parse("a://h?+=x").queryParameter("+"))
        assertEquals("", Url.parse("a://h?+").queryParameter(" "))
        assertEquals("\uFFFD", Url.parse("a://h?a=%E4%B8").queryParameter("a"))
    }

    @Test
    fun equalsAndHashCode() {
        val url = Url.parse("a://h/p?x=1#f")
        assertEquals(url, url)
        val again = Url.parse("a://h/p?x=1#f")
        assertEquals(url, again)
        assertEquals(url.hashCode(), again.hashCode())
        assertEquals(url.hashCode(), url.hashCode())

        val upper = Url.parse("HTTP://h")
        val lower = Url.parse("http://h")
        assertEquals(upper, lower)
        assertEquals(upper.hashCode(), lower.hashCode())

        assertFalse(url.equals(Url.parse("a://h?x=2")))
        assertFalse(Url.parse("a://h").equals(Url.parse("a://h?")))
        assertFalse(Url.parse("a://h").equals(Url.parse("a://h#")))
        assertFalse(Url.parse("a://u@h").equals(Url.parse("a://:@h")))

        val encodedTilde = Url.parse("a://h/%7e")
        val literalTilde = Url.parse("a://h/~")
        assertEquals("/~", encodedTilde.path)
        assertEquals("/~", literalTilde.path)
        assertFalse(encodedTilde.equals(literalTilde))

        assertFalse(url.equals(url.toString()))
        assertFalse(url.equals(null))
    }
}
