package tech.ula.utils

import tech.ula.R
import java.util.regex.Matcher
import java.util.regex.Pattern

class ValidationUtility {

    fun validateFilesystemName(filesystemName: String): CredentialValidationStatus {
        return when {
            filesystemName.isEmpty() ->
                CredentialValidationStatus(false, R.string.error_filesystem_name)

            !validateFilesystemNameCharacters(filesystemName) ->
                CredentialValidationStatus(false, R.string.error_filesystem_name_invalid_characters)

            filesystemName == "." || filesystemName == ".." ->
                CredentialValidationStatus(false, R.string.error_filesystem_name_invalid_characters)

            else -> CredentialValidationStatus(true)
        }
    }

    fun validateUsername(username: String, blacklistUsernames: Array<String>): CredentialValidationStatus {
        return when {
            username.isEmpty() ->
                CredentialValidationStatus(false, R.string.error_empty_field)

            !validateUsernameCharacters(username) ->
                CredentialValidationStatus(false, R.string.error_username_invalid_characters)

            blacklistUsernames.contains(username) ->
                CredentialValidationStatus(false, R.string.error_username_in_blacklist)

            else -> CredentialValidationStatus(true)
        }
    }

    fun validatePassword(password: String): CredentialValidationStatus {
        return when {
            password.isEmpty() ->
                CredentialValidationStatus(false, R.string.error_empty_field)

            !validatePasswordCharacters(password) ->
                CredentialValidationStatus(false, R.string.error_password_invalid)

            else -> CredentialValidationStatus(true)
        }
    }

    fun validateVncPassword(vncPassword: String): CredentialValidationStatus {
        return when {
            vncPassword.isEmpty() ->
                CredentialValidationStatus(false, R.string.error_empty_field)

            vncPassword.length > 8 || vncPassword.length < 6 ->
                CredentialValidationStatus(false, R.string.error_vnc_password_length_incorrect)

            !validatePasswordCharacters(vncPassword) ->
                CredentialValidationStatus(false, R.string.error_vnc_password_invalid)

            else -> CredentialValidationStatus(true)
        }
    }

    private fun validateFilesystemNameCharacters(filesystemName: String): Boolean {
        val usernameRegex = "([a-zA-Z0-9!@#$%^&()_+=,.?<>]{0,50})"

        val compiledRegex = Pattern.compile(usernameRegex)
        val matcher = compiledRegex.matcher(filesystemName)
        val hasValidCharacters = matcher.matches()

        if (hasValidCharacters) {
            return true
        }
        return false
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

data class CredentialValidationStatus(val credentialIsValid: Boolean, val errorMessageId: Int = R.string.general_error_title)