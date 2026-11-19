package io.github.interloper.noted.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun verify_acceptsOnlyTheOriginalPin() {
        val hashed = PinHasher.hash("1234")
        assertTrue(PinHasher.verify("1234", hashed.hash, hashed.salt))
        assertFalse(PinHasher.verify("1235", hashed.hash, hashed.salt))
        assertFalse(PinHasher.verify("", hashed.hash, hashed.salt))
    }

    @Test
    fun hash_isSaltedAndNeverContainsThePin() {
        val a = PinHasher.hash("123456")
        val b = PinHasher.hash("123456")
        assertNotEquals(a.salt, b.salt)
        assertNotEquals(a.hash, b.hash)
        assertFalse(a.hash.contains("123456"))
    }

    @Test
    fun verify_rejectsCorruptStoredValues() {
        val hashed = PinHasher.hash("1234")
        assertFalse(PinHasher.verify("1234", "not base64!", hashed.salt))
        assertFalse(PinHasher.verify("1234", hashed.hash, PinHasher.hash("1234").salt))
    }

    @Test
    fun isValidPin_requiresFourToTwelveDigits() {
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("123456789012"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("1234567890123"))
        assertFalse(PinHasher.isValidPin("12a4"))
    }
}
