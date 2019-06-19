package tech.ula.utils

class LogUtility {

    private fun e(tag: String, message: String) {
        println("ERROR: $tag: $message")
    }

    fun w(tag: String, message: String) {
        println("WARN: $tag: $message")
    }

    fun v(tag: String, message: String) {
        println("INFO: $tag: $message")
    }

    fun d(tag: String, message: String) {
        println("DEBUG: $tag: $message")
    }

    fun logRuntimeErrorForCommand(functionName: String, command: String, err: String) {
        val errorMessage = "Error while executing " +
                "`$functionName()` with command: $command \n\tError = $err"
        this.e(tag = "RuntimeError", message = errorMessage)
    }
}
