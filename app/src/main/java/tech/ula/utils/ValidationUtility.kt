package tech.ula.utils

import tech.ula.R
import java.util.regex.Matcher
import java.util.regex.Pattern

class ValidationUtility(blacklisted_usernames: Array<String>) {

    private val blacklist = blacklisted_usernames

    fun validateUsername(username: String): Credential {
        return when {
            username.isEmpty() ->
                Credential(false, R.string.error_empty_field)

            !validateUsernameCharacters(username) ->
                Credential(false, R.string.error_username_invalid_characters)

            blacklist.contains(username) ->
                Credential(false, R.string.error_username_in_blacklist)

            else -> Credential(true)
        }
    }

    fun validatePassword(password: String): Credential {
        return when {
            password.isEmpty() ->
                Credential(false, R.string.error_empty_field)

            !validatePasswordCharacters(password) ->
                Credential(false, R.string.error_password_invalid)

            else -> Credential(true)
        }
    }

    fun validateVncPassword(vncPassword: String): Credential {
        return when {
            vncPassword.isEmpty() ->
                Credential(false, R.string.error_empty_field)

            vncPassword.length > 8 || vncPassword.length < 6 ->
                Credential(false, R.string.error_vnc_password_length_incorrect)

            !validatePasswordCharacters(vncPassword) ->
                Credential(false, R.string.error_vnc_password_invalid)

            else -> Credential(true)
        }
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

data class Credential(val credentialIsValid: Boolean, val errorMessageId: Int = 0)