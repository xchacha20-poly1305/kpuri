package io.github.xchacha20_poly1305.kpuri

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationTest {
    @Test
    fun scheme() {
        for (valid in listOf("http", "ssh", "svn+ssh", "a.b-c", "S3")) {
            assertEquals(-1, invalidSchemeIndex(valid), valid)
        }
        assertEquals(0, invalidSchemeIndex(""))
        assertEquals(0, invalidSchemeIndex("1http"))
        assertEquals(0, invalidSchemeIndex("+a"))
        assertEquals(2, invalidSchemeIndex("ht_tp"))
    }

    @Test
    fun port() {
        val strict = UrlOptions()
        val lenient = UrlOptions(validatePort = false)
        for (valid in listOf("", "0", "443", "65535", "00080")) {
            assertEquals(-1, invalidPortIndex(valid, strict), valid)
        }
        assertEquals(4, invalidPortIndex("65536", strict))
        assertEquals(4, invalidPortIndex("99999999999999999999", strict))
        assertEquals(3, invalidPortIndex("443,7788-8899", strict))
        assertEquals(-1, invalidPortIndex("443,7788-8899", lenient))
        assertEquals(3, invalidPortIndex("443/", lenient))
        assertEquals(1, invalidPortIndex("4\n", lenient))
    }

    @Test
    fun regName() {
        assertEquals(-1, invalidRegNameIndex("example.com"))
        assertEquals(-1, invalidRegNameIndex("中国.example"))
        assertEquals(-1, invalidRegNameIndex("a%20b"))
        assertEquals(1, invalidRegNameIndex("a b"))
        assertEquals(0, invalidRegNameIndex("[::1"))
        assertEquals(1, invalidRegNameIndex("a\u0000"))
    }

    @Test
    fun control() {
        assertEquals(-1, invalidControlIndex("a b\u0080"))
        assertEquals(1, invalidControlIndex("a\tb"))
        assertEquals(1, invalidControlIndex("a\u007F"))
    }

    @Test
    fun ipv4() {
        for (valid in listOf("0.0.0.0", "1.2.3.4", "255.255.255.255", "10.0.10.1")) {
            assertTrue(isIpv4Address(valid), valid)
        }
        for (invalid in listOf("", "1.2.3", "1.2.3.4.5", "256.1.1.1", "01.1.1.1", "1..1.1", "a.b.c.d", "1.2.3.4 ")) {
            assertFalse(isIpv4Address(invalid), invalid)
        }
    }

    @Test
    fun ipv6() {
        val valid = listOf(
            "::", "::1", "1::", "1:2:3:4:5:6:7:8", "1::8", "1:2:3:4:5:6::8", "1:2:3:4:5:6:7::",
            "::ffff:192.0.2.1", "64:ff9b::192.0.2.33", "1:2:3:4:5:6:1.2.3.4", "fe80::ABCD",
        )
        for (address in valid) {
            assertTrue(isIpv6Address(address), address)
        }
        val invalid = listOf(
            "", ":", ":::", "1:2:3:4:5:6:7", "1:2:3:4:5:6:7:8:9", "1::2::3", ":1:2:3:4:5:6:7:8",
            "1:2:3:4:5:6:7:8:", "12345::", "g::", "::1.2.3", "1.2.3.4::", "1:2:3:4:5:6:7:1.2.3.4",
            "1::2:3:4:5:6:7:8",
        )
        for (address in invalid) {
            assertFalse(isIpv6Address(address), address)
        }
    }

    @Test
    fun ipLiteral() {
        for (valid in listOf("::1", "fe80::1%25eth0", "fe80::1%25en%30", "v1.x", "V1F.a:b!")) {
            assertEquals(-1, invalidIpLiteralIndex(valid), valid)
        }
        assertEquals(0, invalidIpLiteralIndex("example.com"))
        assertEquals(0, invalidIpLiteralIndex("fe80::1%eth0"))
        assertEquals(10, invalidIpLiteralIndex("fe80::1%25"))
        assertEquals(11, invalidIpLiteralIndex("fe80::1%25e/0"))
        assertEquals(1, invalidIpLiteralIndex("v.x"))
        assertEquals(3, invalidIpLiteralIndex("v1."))
        assertEquals(3, invalidIpLiteralIndex("v1.@"))
    }
}
