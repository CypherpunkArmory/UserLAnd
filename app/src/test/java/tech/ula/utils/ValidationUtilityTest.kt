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

    private val defaultPassword = "password"

    @Before
    fun setup() {
        validationUtility = ValidationUtility(resources)

        val blacklistedUsernames = arrayOf(
                "adm",
                "audio",
                "autologin",
                "backup",
                "bin",
                "cdrom",
                "daemon",
                "dbus",
                "dialout",
                "dip",
                "disk",
                "fax",
                "floppy",
                "ftp",
                "games",
                "gnats",
                "http",
                "input",
                "irc",
                "kmem",
                "kvm",
                "list",
                "locate",
                "lock",
                "log",
                "lp",
                "lpadmin",
                "mail",
                "mem",
                "netdev",
                "network",
                "news",
                "nobody",
                "nogroup",
                "operator",
                "optical",
                "plugdev",
                "postgres",
                "power",
                "proc",
                "proxy",
                "rfkill",
                "root",
                "sambashare",
                "sasl",
                "scanner",
                "shadow",
                "smmsp",
                "src",
                "staff",
                "storage",
                "sudo",
                "sync",
                "sys",
                "systemd-journal",
                "tape",
                "tty",
                "users",
                "utmp",
                "uucp",
                "uuidd",
                "vboxusers",
                "video",
                "voice",
                "wheel",
                "www-data"
        )

        whenever(resources.getString(R.string.error_empty_field))
                .thenReturn("Each field must be entered!")
        whenever(resources.getString(R.string.error_username_invalid_characters))
                .thenReturn("Characters in username must be 1 to 30 characters with lowercase letters and/or numbers.")
        whenever(resources.getStringArray(R.array.blacklisted_usernames))
                .thenReturn(blacklistedUsernames)
        whenever(resources.getString(R.string.error_password_invalid))
                .thenReturn("Password has invalid characters")
        whenever(resources.getString(R.string.error_vnc_password_invalid))
                .thenReturn("VNC Password has invalid characters")
        whenever(resources.getString(R.string.error_vnc_password_length_incorrect))
                .thenReturn("VNC Password must be between 6 to 8 characters!")
    }

    @Test
    fun `Username is empty`() {
        val username = ""

        credential = validationUtility.validateUsername(username)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessage, "Each field must be entered!")
    }

    @Test
    fun `Username is capitalized`() {
        val username = "A"
        credential = validationUtility.validateUsername(username)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessage, "Characters in username must be 1 to 30 characters with lowercase letters and/or numbers.")
    }

    @Test
    fun `Username has capital letters`() {
        val username = "abC"
        credential = validationUtility.validateUsername(username)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessage, "Characters in username must be 1 to 30 characters with lowercase letters and/or numbers.")
    }

    @Test
    fun `Username starts with numbers`() {
        val username = "123abc"
        credential = validationUtility.validateUsername(username)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessage, "Characters in username must be 1 to 30 characters with lowercase letters and/or numbers.")
    }

    @Test
    fun `Username starts with underscore`() {
        val username = "_123abc"
        credential = validationUtility.validateUsername(username)
        assertTrue(credential.credentialIsValid)
        assertEquals(credential.errorMessage, "")
    }

    @Test
    fun `Username is in blacklist`() {
        val username = "root"
        whenever(resources.getString(R.string.error_username_in_blacklist))
                .thenReturn("The username: `root` is reserved and cannot be used as a filesystem username.")

        credential = validationUtility.validateUsername(username)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessage, "The username: `root` is reserved and cannot be used as a filesystem username.")
    }

    @Test
    fun `password is empty`() {
        val password = ""
        val vncPassword = defaultPassword

        credential = validationUtility.validatePasswords(password, vncPassword)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessage, "Each field must be entered!")
    }

    @Test
    fun `vnc password is empty`() {
        val password = defaultPassword
        val vncPassword = ""

        credential = validationUtility.validatePasswords(password, vncPassword)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessage, "Each field must be entered!")
    }

    @Test
    fun `vnc password is too long`() {
        val password = defaultPassword
        val vncPassword = "abcdefghijklmnop"

        credential = validationUtility.validatePasswords(password, vncPassword)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessage, "VNC Password must be between 6 to 8 characters!")
    }

    @Test
    fun `vnc password is too short`() {
        val password = defaultPassword
        val vncPassword = "abc"

        credential = validationUtility.validatePasswords(password, vncPassword)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessage, "VNC Password must be between 6 to 8 characters!")
    }

    @Test
    fun `password has a space`() {
        val password = "pass word"
        val vncPassword = defaultPassword

        credential = validationUtility.validatePasswords(password, vncPassword)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessage, "Password has invalid characters")
    }

    @Test
    fun `vnc password has a space`() {
        val password = defaultPassword
        val vncPassword = "te sting"

        credential = validationUtility.validatePasswords(password, vncPassword)
        assertFalse(credential.credentialIsValid)
        assertEquals(credential.errorMessage, "VNC Password has invalid characters")
    }
}