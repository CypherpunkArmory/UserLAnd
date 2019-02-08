package tech.ula.utils

import android.content.res.Resources
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import tech.ula.R

@RunWith(MockitoJUnitRunner::class)
class ValidationUtilityTest {

    @Mock
    lateinit var validationUtility: ValidationUtility

    @Mock
    lateinit var credential: Credential

    @Mock
    lateinit var resources: Resources

    @Before
    fun setup() {
        val blacklistedUsernames = arrayOf("root")
        validationUtility = ValidationUtility(blacklistedUsernames)
    }

    @Test
    fun `Validate fails appropriately if username is empty`() {
        val username = ""

        credential = validationUtility.validateUsername(username)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_empty_field)
    }

    @Test
    fun `Validation fails appropriately if username is capitalized`() {
        val username = "A"
        credential = validationUtility.validateUsername(username)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_username_invalid_characters)
    }

    @Test
    fun `Validation fails appropriately if username has capital letters`() {
        val username = "abC"
        credential = validationUtility.validateUsername(username)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_username_invalid_characters)
    }

    @Test
    fun `Validation fails appropriately if username starts with numbers`() {
        val username = "123abc"
        credential = validationUtility.validateUsername(username)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_username_invalid_characters)
    }

    @Test
    fun `Validation succeeds appropriately if username starts with underscore`() {
        val username = "_123abc"
        credential = validationUtility.validateUsername(username)
        assertTrue(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, 0)
    }

    @Test
    fun `Validation fails appropriately if username has space`() {
        val username = "user name"
        credential = validationUtility.validateUsername(username)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_username_invalid_characters)
    }

    @Test
    fun `Validation fails appropriately if username is too long`() {
        val username = "abcdefghijklmnopqrstuvwxyz123456"
        credential = validationUtility.validateUsername(username)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_username_invalid_characters)
    }

    @Test
    fun `Validation fails appropriately if username is in blacklist`() {
        val username = "root"

        credential = validationUtility.validateUsername(username)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_username_in_blacklist)
    }

    @Test
    fun `Validation fails appropriately if password is empty`() {
        val password = ""

        credential = validationUtility.validatePassword(password)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_empty_field)
    }

    @Test
    fun `Validation fails appropriately if vnc password is empty`() {
        val vncPassword = ""

        credential = validationUtility.validatePassword(vncPassword)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_empty_field)
    }

    @Test
    fun `Validation fails appropriately if vnc password is too long`() {
        val vncPassword = "abcdefghijklmnop"

        credential = validationUtility.validateVncPassword(vncPassword)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_vnc_password_length_incorrect)
    }

    @Test
    fun `Validation fails appropriately if vnc password is too short`() {
        val vncPassword = "abc"

        credential = validationUtility.validateVncPassword(vncPassword)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_vnc_password_length_incorrect)
    }

    @Test
    fun `Validation fails appropriately if password has a space`() {
        val password = "pass word"

        credential = validationUtility.validatePassword(password)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_password_invalid)
    }

    @Test
    fun `Validation fails appropriately if vnc password has a space`() {
        val vncPassword = "te sting"

        credential = validationUtility.validateVncPassword(vncPassword)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_vnc_password_invalid)
    }
}