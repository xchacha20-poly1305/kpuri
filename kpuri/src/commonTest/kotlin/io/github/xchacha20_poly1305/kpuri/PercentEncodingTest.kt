package io.github.xchacha20_poly1305.kpuri

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PercentEncodingTest {
    @Test
    fun asciiSetMembership() {
        assertTrue('a' in UrlChars.UNRESERVED)
        assertTrue('~' in UrlChars.UNRESERVED)
        assertFalse('/' in UrlChars.UNRESERVED)
        assertFalse('中' in UrlChars.UNRESERVED)
        assertTrue(':' in UrlChars.PASSWORD)
        assertFalse(':' in UrlChars.USERNAME)
        assertTrue('?' in UrlChars.QUERY)
        assertFalse('&' in UrlChars.QUERY_PARAMETER)
        assertFalse('+' in UrlChars.QUERY_PARAMETER)
        assertTrue(',' in UrlChars.QUERY_PARAMETER)
    }

    @Test
    fun encodeReturnsSameInstanceWhenNothingToEncode() {
        val s = "abc-._~"
        assertSame(s, s.percentEncode(UrlChars.UNRESERVED, keepEscapes = false))
    }

    @Test
    fun encodeDisallowedCharacters() {
        assertEquals("a%20b", "a b".percentEncode(UrlChars.PATH, keepEscapes = false))
        assertEquals("a%3Ab", "a:b".percentEncode(UrlChars.USERNAME, keepEscapes = false))
        assertEquals("a:b", "a:b".percentEncode(UrlChars.PASSWORD, keepEscapes = false))
        assertEquals("a%26b%3Dc%2Bd%23", "a&b=c+d#".percentEncode(UrlChars.QUERY_PARAMETER, keepEscapes = false))
    }

    @Test
    fun encodeNonAsciiAsUtf8() {
        assertEquals("%E4%B8%AD%E5%9B%BD", "中国".percentEncode(UrlChars.FRAGMENT, keepEscapes = false))
        assertEquals("%F0%9F%87%A8%F0%9F%87%B3", "🇨🇳".percentEncode(UrlChars.FRAGMENT, keepEscapes = false))
    }

    @Test
    fun encodePercent() {
        assertEquals("a%2520b", "a%20b".percentEncode(UrlChars.PASSWORD, keepEscapes = false))
        assertEquals("a%20b", "a%20b".percentEncode(UrlChars.PASSWORD, keepEscapes = true))
        assertEquals("a%2fb", "a%2fb".percentEncode(UrlChars.PASSWORD, keepEscapes = true))
        assertEquals("50%25off", "50%off".percentEncode(UrlChars.PASSWORD, keepEscapes = true))
        assertEquals("%25", "%".percentEncode(UrlChars.PASSWORD, keepEscapes = true))
        assertEquals("%252", "%2".percentEncode(UrlChars.PASSWORD, keepEscapes = true))
    }

    @Test
    fun decode() {
        assertEquals("a b", "a%20b".percentDecode())
        assertEquals("中国", "%E4%B8%AD%e5%9b%bd".percentDecode())
        assertEquals("a+b", "a+b".percentDecode())
        assertEquals("a b+", "a+b%2B".percentDecode(plusAsSpace = true))
        assertEquals("50%off", "50%off".percentDecode())
        assertEquals("%", "%".percentDecode())
        assertEquals("�", "%FF".percentDecode())
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val values = listOf("", "plain", "a b&c=d+e#f", "50%off", "p@ss:w/rd?", "中国 🇨🇳", "%%41")
        for (value in values) {
            assertEquals(value, value.percentEncode(UrlChars.QUERY_PARAMETER, keepEscapes = false).percentDecode())
        }
    }
}
