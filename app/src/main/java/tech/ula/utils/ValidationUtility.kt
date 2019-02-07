package tech.ula.utils

import android.content.res.Resources
import tech.ula.R
import java.util.regex.Matcher
import java.util.regex.Pattern

class ValidationUtility(systemResources: Resources) {

    private val resources = systemResources

    fun validateUsername(username: String): Credential {
        var usernameIsValid = false
        var errorMessage = ""
        val blacklist = resources.getStringArray(R.array.blacklisted_usernames)

        when {
            username.isEmpty() -> {
                errorMessage = resources.getString(R.string.error_empty_field)
            }
            !validateUsernameCharacters(username) -> {
                errorMessage = resources.getString(R.string.error_username_invalid_characters)
            }
            blacklist.contains(username) -> {
                errorMessage = resources.getString(R.string.error_username_in_blacklist)
                errorMessage = String.format(errorMessage, username)
            }
            else ->
                usernameIsValid = true
        }

        return Credential(usernameIsValid, errorMessage)
    }

    fun validatePasswords(password: String, vncPassword: String): Credential {
        var passwordIsValid = false
        var errorMessage = ""

        when {
            password.isEmpty() || vncPassword.isEmpty() -> {
                errorMessage = resources.getString(R.string.error_empty_field)
            }
            vncPassword.length > 8 || vncPassword.length < 6 -> {
                errorMessage = resources.getString(R.string.error_vnc_password_length_incorrect)
            }
            !validatePasswordCharacters(password) -> {
                errorMessage = resources.getString(R.string.error_password_invalid)
            }
            !validatePasswordCharacters(vncPassword) -> {
                errorMessage = resources.getString(R.string.error_vnc_password_invalid)
            }
            else ->
                passwordIsValid = true
        }

        return Credential(passwordIsValid, errorMessage)
    }

    private fun validateUsernameCharacters(username: String): Boolean {
        val usernameRegex = "([a-z_][a-z0-9_]{0,30})"

        val compiledRegex = Pattern.compile(usernameRegex)
        val matcher = compiledRegex.matcher(username)
        val hasValidCharacters = matcher.matches()

        if (hasValidCharacters) {
            return true
        }
        return false
    }

    private fun validatePasswordCharacters(password: String): Boolean {
        val pattern: Pattern
        val matcher: Matcher

        val passwordRegex = "^[a-zA-Z0-9!@#$%^&*()_+=,./?<>:]*\$"

        pattern = Pattern.compile(passwordRegex)
        matcher = pattern.matcher(password)

        return matcher.matches()
    }
}

sealed class CredentialValidation
data class Credential(val credentialIsValid: Boolean, val errorMessage: String) : CredentialValidation()