package tech.ula.utils

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
    lateinit var credential: CredentialValidationStatus

    private var blacklistUsernames = arrayOf("root")

    @Before
    fun setup() {
        validationUtility = ValidationUtility()
    }

    @Test
    fun `Validate fails appropriately if filesystem name is empty`() {
        val filesystemName = ""
        credential = validationUtility.validateFilesystemName(filesystemName)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_filesystem_name)
    }

    @Test
    fun `Validate fails appropriately if filesystem name is just a period`() {
        val filesystemName = "."
        credential = validationUtility.validateFilesystemName(filesystemName)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_filesystem_name_invalid_characters)
    }

    @Test
    fun `Validate fails appropriately if filesystem name has invalid characters such as forward slashes`() {
        val filesystemName = "filename/"
        credential = validationUtility.validateFilesystemName(filesystemName)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_filesystem_name_invalid_characters)
    }

    @Test
    fun `Validate succeeds if filesystem name is just letters and underscores`() {
        val filesystemName = "filesystem_name"
        credential = validationUtility.validateFilesystemName(filesystemName)
        assertTrue(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.general_error_title)
    }

    @Test
    fun `Validate fails appropriately if username is empty`() {
        val username = ""
        credential = validationUtility.validateUsername(username, blacklistUsernames)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_empty_field)
    }

    @Test
    fun `Validation fails appropriately if username is capitalized`() {
        val username = "A"
        credential = validationUtility.validateUsername(username, blacklistUsernames)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_username_invalid_characters)
    }

    @Test
    fun `Validation fails appropriately if username has capital letters`() {
        val username = "abC"
        credential = validationUtility.validateUsername(username, blacklistUsernames)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_username_invalid_characters)
    }

    @Test
    fun `Validation fails appropriately if username starts with numbers`() {
        val username = "123abc"
        credential = validationUtility.validateUsername(username, blacklistUsernames)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_username_invalid_characters)
    }

    @Test
    fun `Validation succeeds appropriately if username starts with underscore`() {
        val username = "_123abc"
        credential = validationUtility.validateUsername(username, blacklistUsernames)
        assertTrue(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.general_error_title)
    }

    @Test
    fun `Validation fails appropriately if username has space`() {
        val username = "user name"
        credential = validationUtility.validateUsername(username, blacklistUsernames)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_username_invalid_characters)
    }

    @Test
    fun `Validation fails appropriately if username is too long`() {
        val username = "abcdefghijklmnopqrstuvwxyz123456"
        credential = validationUtility.validateUsername(username, blacklistUsernames)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessageId, R.string.error_username_invalid_characters)
    }

    @Test
    fun `Validation fails appropriately if username is in blacklist`() {
        val username = "root"

        credential = validationUtility.validateUsername(username, blacklistUsernames)
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

        credential = validationUtility.validateVncPassword(vncPassword)
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