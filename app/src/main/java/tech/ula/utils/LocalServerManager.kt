package tech.ula.utils

import tech.ula.model.entities.ServiceType
import tech.ula.model.entities.Session
import java.io.File

/**
 * LocalServerManager
 *
 * Responsible for starting/stopping and probing UserLAnd-backed servers (SSH / VNC / XSDL)
 * inside a filesystem via proot + busybox.
 *
 * Hardening & refactor notes (2025, Rafael Melo Reis – ∆RafaelVerboΩ):
 * - Safer PID handling (invalid/zombie detection, null-safe serverPid).
 * - Centralized error logging with function tags.
 * - Shared USERLAND_* environment contract for better interoperability inside the container.
 * - More defensive behavior on invalid sessions / invalid PIDs.
 */
class LocalServerManager(
    private val applicationFilesDirPath: String,
    private val busyboxExecutor: BusyboxExecutor,
    private val logger: Logger = SentryLogger()
) {

    private val vncDisplayNumber = 51

    /**
     * Extract a PID from a Process instance in a defensive way.
     *
     * Android/Java implementations differ, so we:
     *  1) Try reflection on "pid" field (common on Android's ProcessImpl).
     *  2) Fallback to parsing Process.toString().
     *  3) On failure, log and return -1.
     */
    private fun getProcessPid(process: Process): Long {
        // Try reflection first.
        runCatching {
            val clazz = process.javaClass
            val field = clazz.getDeclaredField("pid")
            field.isAccessible = true
            val pid = field.get(process)
            if (pid is Int && pid > 0) return pid.toLong()
            if (pid is Long && pid > 0L) return pid
        }.onFailure {
            // Ignored here, we fallback to string parsing below.
        }

        // Fallback: parse from toString(), which looks like "Process[pid=1234, hasExited=false]"
        return runCatching {
            process.toString()
                .substringAfter("pid=", missingDelimiterValue = "")
                .substringBefore(",", missingDelimiterValue = "")
                .substringBefore("]", missingDelimiterValue = "")
                .trim()
                .toLong()
                .takeIf { it > 0L }
        }.getOrElse {
            logRuntimeError(
                function = "getProcessPid",
                message = "Failed to obtain PID from Process; process.toString()='${process}'"
            )
            -1L
        } ?: -1L
    }

    /**
     * Centralized runtime error logging for this manager.
     */
    private fun logRuntimeError(function: String, message: String) {
        val details = "func: $function err: $message"
        val breadcrumb = UlaBreadcrumb(
            "LocalServerManager",
            BreadcrumbType.RuntimeError,
            details
        )
        logger.addBreadcrumb(breadcrumb)
    }

    /**
     * Build a base environment map that is injected into all proot commands
     * to improve interoperability inside the container.
     *
     * These variables can be used by scripts and init systems in the guest
     * to detect that they're running under UserLAnd.
     */
    private fun buildBaseEnv(session: Session): MutableMap<String, String> {
        val env = mutableMapOf<String, String>()

        // Generic UserLAnd context
        env["USERLAND_SESSION_NAME"] = session.name
        env["USERLAND_USERNAME"] = session.username
        env["USERLAND_FS_ID"] = session.filesystemId.toString()
        env["USERLAND_SERVICE_TYPE"] = session.serviceType.name
        env["USERLAND_APP_FILES_DIR"] = applicationFilesDirPath

        // Room for future flags (debug levels, feature toggles, etc.)
        // e.g.: env["USERLAND_LOG_LEVEL"] = "INFO"

        return env
    }

    /**
     * Start the server associated with the session type.
     * Returns the PID of the newly started process, or <= 0 on failure.
     */
    fun startServer(session: Session): Long {
        // Basic sanity: invalid entries should not attempt to start anything.
        if (session.filesystemId <= 0L) {
            logRuntimeError(
                function = "startServer",
                message = "Invalid filesystemId '${session.filesystemId}' for session '${session.name}'"
            )
            return -1L
        }

        return when (session.serviceType) {
            ServiceType.Ssh -> startSSHServer(session)
            ServiceType.Vnc -> startVNCServer(session)
            ServiceType.Xsdl -> setDisplayNumberAndStartTwm(session)
            else -> {
                logRuntimeError(
                    function = "startServer",
                    message = "Unsupported serviceType '${session.serviceType}' for session '${session.name}'"
                )
                -1L
            }
        }
    }

    /**
     * Stop the service associated with this session.
     * Defensively handles invalid PIDs and cleans up stale pidfiles.
     */
    fun stopService(session: Session) {
        val sessionPid = session.pid
        val serverPid = session.serverPid()

        // If we don't have any meaningful PIDs, don't call kill script blindly.
        if (sessionPid <= 0L && serverPid <= 0L) {
            logRuntimeError(
                function = "stopService",
                message = "No valid PID found for session '${session.name}'. sessionPid=$sessionPid, serverPid=$serverPid"
            )
            deletePidFile(session)
            return
        }

        val command = "support/killProcTree.sh $sessionPid $serverPid"
        val result = busyboxExecutor.executeScript(command)

        if (result is FailedExecution) {
            logRuntimeError("stopService", result.reason)
        }

        // Regardless of script result, attempt to clean stale pidfile.
        deletePidFile(session)
    }

    /**
     * Checks if the server for this session is currently running.
     * Avoids calling the script when there is no valid serverPid.
     */
    fun isServerRunning(session: Session): Boolean {
        // XSDL server is handled by third-party app; consider it running from our perspective.
        if (session.serviceType == ServiceType.Xsdl) return true

        val serverPid = session.serverPid()
        if (serverPid <= 0L) {
            // No valid pidfile or cannot parse PID.
            return false
        }

        val command = "support/isServerInProcTree.sh $serverPid"
        val result = busyboxExecutor.executeScript(command)
        return when (result) {
            is SuccessfulExecution -> true
            is FailedExecution -> {
                // If the script indicates "No such process", treat as stale pidfile and clean it.
                if (result.reason.contains("No such process", ignoreCase = true) ||
                    result.reason.contains("not found", ignoreCase = true)
                ) {
                    deletePidFile(session)
                }
                logRuntimeError("isServerRunning", result.reason)
                false
            }
            else -> false
        }
    }

    /**
     * Deletes the PID file for the given session if it exists.
     */
    private fun deletePidFile(session: Session) {
        val pidFile = File(session.pidFilePath())
        if (pidFile.exists() && !pidFile.delete()) {
            logRuntimeError(
                function = "deletePidFile",
                message = "Failed to delete pidfile at '${pidFile.absolutePath}'"
            )
        }
    }

    /**
     * Start SSH server inside the filesystem.
     */
    private fun startSSHServer(session: Session): Long {
        val filesystemDirName = session.filesystemId.toString()
        deletePidFile(session)

        val command = "/support/startSSHServer.sh"
        val env = buildBaseEnv(session)

        val result = busyboxExecutor.executeProotCommand(
            command = command,
            filesystemDirName = filesystemDirName,
            commandShouldTerminate = false,
            env = env
        )

        return when (result) {
            is OngoingExecution -> {
                val pid = getProcessPid(result.process)
                if (pid <= 0L) {
                    logRuntimeError(
                        function = "startSSHServer",
                        message = "Obtained invalid PID ($pid) for session '${session.name}'"
                    )
                    -1L
                } else {
                    pid
                }
            }

            is FailedExecution -> {
                logRuntimeError("startSSHServer", result.reason)
                -1L
            }

            else -> -1L
        }
    }

    /**
     * Start VNC server inside the filesystem.
     */
    private fun startVNCServer(session: Session): Long {
        val filesystemDirName = session.filesystemId.toString()
        deletePidFile(session)

        val command = "/support/startVNCServer.sh"
        val env = buildBaseEnv(session).apply {
            this["INITIAL_USERNAME"] = session.username
            this["INITIAL_VNC_PASSWORD"] = session.vncPassword
            this["DIMENSIONS"] = session.geometry
        }

        val result = busyboxExecutor.executeProotCommand(
            command = command,
            filesystemDirName = filesystemDirName,
            commandShouldTerminate = false,
            env = env
        )

        return when (result) {
            is OngoingExecution -> {
                val pid = getProcessPid(result.process)
                if (pid <= 0L) {
                    logRuntimeError(
                        function = "startVNCServer",
                        message = "Obtained invalid PID ($pid) for session '${session.name}'"
                    )
                    -1L
                } else {
                    pid
                }
            }

            is FailedExecution -> {
                logRuntimeError("startVNCServer", result.reason)
                -1L
            }

            else -> -1L
        }
    }

    /**
     * Start XSDL-backed server / twm with fixed DISPLAY and audio targets.
     */
    private fun setDisplayNumberAndStartTwm(session: Session): Long {
        val filesystemDirName = session.filesystemId.toString()
        deletePidFile(session)

        val command = "/support/startXSDLServer.sh"
        val env = buildBaseEnv(session).apply {
            this["INITIAL_USERNAME"] = session.username
            this["DISPLAY"] = ":4721"
            this["PULSE_SERVER"] = "127.0.0.1:4721"
        }

        val result = busyboxExecutor.executeProotCommand(
            command = command,
            filesystemDirName = filesystemDirName,
            commandShouldTerminate = false,
            env = env
        )

        return when (result) {
            is OngoingExecution -> {
                val pid = getProcessPid(result.process)
                if (pid <= 0L) {
                    logRuntimeError(
                        function = "setDisplayNumberAndStartTwm",
                        message = "Obtained invalid PID ($pid) for session '${session.name}'"
                    )
                    -1L
                } else {
                    pid
                }
            }

            is FailedExecution -> {
                logRuntimeError("setDisplayNumberAndStartTwm", result.reason)
                -1L
            }

            else -> -1L
        }
    }

    // ---- PID utilities bound to Session ----

    private fun Session.pidRelativeFilePath(): String {
        return when (this.serviceType) {
            ServiceType.Ssh -> "/run/dropbear.pid"
            ServiceType.Vnc -> "/home/${this.username}/.vnc/localhost:$vncDisplayNumber.pid"
            ServiceType.Xsdl -> "/tmp/xsdl.pidfile"
            else -> "error"
        }
    }

    private fun Session.pidFilePath(): String {
        return "$applicationFilesDirPath/${this.filesystemId}${this.pidRelativeFilePath()}"
    }

    /**
     * Read the server PID from the pidfile, if present and parseable.
     * Returns -1 on any failure.
     */
    private fun Session.serverPid(): Long {
        val pidFilePath = this.pidFilePath()
        val pidFile = File(pidFilePath)
        if (!pidFile.exists()) return -1L

        return try {
            val text = pidFile.readText().trim()
            text.toLong().takeIf { it > 0L } ?: run {
                logRuntimeError(
                    function = "serverPid",
                    message = "Non-positive PID '$text' in pidfile '$pidFilePath'"
                )
                -1L
            }
        } catch (e: Exception) {
            logRuntimeError(
                function = "serverPid",
                message = "Failed to parse PID from pidfile '$pidFilePath': ${e.message}"
            )
            -1L
        }
    }
}
