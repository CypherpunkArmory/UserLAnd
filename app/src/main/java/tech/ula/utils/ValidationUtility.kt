package tech.ula.utils

import java.util.regex.Matcher
import java.util.regex.Pattern

class ValidationUtility() {

    fun isUsernameValid(password: String): Boolean {
        val pattern: Pattern
        val matcher: Matcher

        val PASSWORD_PATTERN = "^[a-zA-Z0-9]*\$"

        pattern = Pattern.compile(PASSWORD_PATTERN)
        matcher = pattern.matcher(password)

        return matcher.matches()
    }


    fun isPasswordValid(password: String): Boolean {
        val pattern: Pattern
        val matcher: Matcher

        val PASSWORD_PATTERN = "^[a-zA-Z0-9!@#$%^&*()_+=,./?<>:]*\$"

        pattern = Pattern.compile(PASSWORD_PATTERN)
        matcher = pattern.matcher(password)

        return matcher.matches()
    }
}