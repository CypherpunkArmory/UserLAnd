package tech.ula.utils

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

sealed class ExecutionResult
data class MissingExecutionAsset(val asset: String) : ExecutionResult()
object SuccessfulExecution : ExecutionResult()
data class FailedExecution(val reason: String) : ExecutionResult()
data class OngoingExecution(val process: Process) : ExecutionResult()

class BusyboxExecutor(
    private val ulaFiles: UlaFiles,
    private val prootDebugLogger: ProotDebugLogger,
    private val busyboxWrapper: BusyboxWrapper = BusyboxWrapper(ulaFiles)
) {

    companion object {
        private const val TAG = "BusyboxExecutor"

        // Timeout padrão para comandos PROOT que "devem terminar"
        // (evita travamentos infinitos / zumbis em scripts de bootstrap).
        private const val PROOT_TERMINATING_TIMEOUT_MILLIS: Long = 300_000L // 5 minutos
    }

    private val discardOutput: (String) -> Any = { Log.d(TAG, it) }

    fun executeScript(
        scriptCall: String,
        listener: (String) -> Any = discardOutput
    ): ExecutionResult {
        val updatedCommand = busyboxWrapper.wrapScript(scriptCall)
        return runCommand(updatedCommand, listener)
    }

    fun executeCommand(
        command: String,
        listener: (String) -> Any = discardOutput
    ): ExecutionResult {
        val updatedCommand = busyboxWrapper.wrapCommand(command)
        return runCommand(updatedCommand, listener)
    }

    /**
     * Executa comando simples (sem PROOT), com ambiente Busybox.
     * Saída é consumida para evitar deadlock de buffer.
     */
    private fun runCommand(
        command: List<String>,
        listener: (String) -> Any
    ): ExecutionResult {
        if (!busyboxWrapper.busyboxIsPresent()) {
            Log.e(TAG, "busybox binary missing; cannot run command: $command")
            return MissingExecutionAsset("busybox")
        }

        val env = busyboxWrapper.getBusyboxEnv()
        val processBuilder = ProcessBuilder(command)
            .directory(ulaFiles.filesDir)
            .redirectErrorStream(true)

        processBuilder.environment().putAll(env)

        return try {
            Log.d(TAG, "runCommand(): $command, env=$env")
            val process = processBuilder.start()
            collectOutput(process.inputStream, listener)
            getProcessResult(process)
        } catch (err: Exception) {
            Log.e(TAG, "runCommand() failed: $err")
            FailedExecution("runCommand failed: ${err.message ?: err.toString()}")
        }
    }

    /**
     * Executa comando dentro do filesystem via PROOT.
     *
     * @param command             script ou comando dentro do rootfs.
     * @param filesystemDirName   nome do diretório do rootfs (id).
     * @param commandShouldTerminate se true, espera o término (com timeout).
     * @param env                 variáveis extras a serem injetadas.
     */
    fun executeProotCommand(
        command: String,
        filesystemDirName: String,
        commandShouldTerminate: Boolean,
        env: HashMap<String, String> = hashMapOf(),
        listener: (String) -> Any = discardOutput,
        coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ): ExecutionResult {
        when {
            !busyboxWrapper.busyboxIsPresent() ->
                return MissingExecutionAsset("busybox")
            !busyboxWrapper.prootIsPresent() ->
                return MissingExecutionAsset("proot")
            !busyboxWrapper.executionScriptIsPresent() ->
                return MissingExecutionAsset("execution script")
        }

        val prootDebugEnabled = prootDebugLogger.isEnabled
        val prootDebugLevel = if (prootDebugEnabled) prootDebugLogger.verbosityLevel else "-1"

        val updatedCommand = busyboxWrapper.addBusyboxAndProot(command)
        val filesystemDir = File("${ulaFiles.filesDir.absolutePath}/$filesystemDirName")

        // Merge de ambiente:
        // 1) base PROOT env
        // 2) env fornecido pelo chamador (sobrescreve se necessário)
        val mergedEnv = busyboxWrapper.getProotEnv(filesystemDir, prootDebugLevel).apply {
            putAll(env)
        }

        val processBuilder = ProcessBuilder(updatedCommand)
            .directory(ulaFiles.filesDir)
            .redirectErrorStream(true)

        processBuilder.environment().putAll(mergedEnv)

        return try {
            Log.d(
                TAG,
                "executeProotCommand(): cmd=$updatedCommand, fs=$filesystemDirName, env=$mergedEnv, terminate=$commandShouldTerminate"
            )
            val process = processBuilder.start()

            return when {
                prootDebugEnabled && commandShouldTerminate -> {
                    listener("Output redirecting to proot debug log")
                    prootDebugLogger.logStream(process.inputStream, coroutineScope)
                    waitForProcessWithTimeout(process, PROOT_TERMINATING_TIMEOUT_MILLIS)
                }

                prootDebugEnabled && !commandShouldTerminate -> {
                    listener("Output redirecting to proot debug log")
                    prootDebugLogger.logStream(process.inputStream, coroutineScope)
                    OngoingExecution(process)
                }

                commandShouldTerminate -> {
                    collectOutput(process.inputStream, listener)
                    waitForProcessWithTimeout(process, PROOT_TERMINATING_TIMEOUT_MILLIS)
                }

                else -> {
                    // Não consome saída aqui; chamador deverá cuidar disso via process.
                    OngoingExecution(process)
                }
            }
        } catch (err: Exception) {
            Log.e(TAG, "executeProotCommand() failed: $err")
            FailedExecution("executeProotCommand failed: ${err.message ?: err.toString()}")
        }
    }

    /**
     * Deleção recursiva robusta:
     * - Garante que o path está dentro de filesDir (sandbox).
     * - Faz quoting básico para evitar injeção de shell.
     */
    suspend fun recursivelyDelete(absolutePath: String): ExecutionResult =
        withContext(Dispatchers.IO) {
            try {
                val root = ulaFiles.filesDir.canonicalFile
                val target = File(absolutePath).canonicalFile

                // Segurança: não deixa apagar fora da sandbox do app.
                if (!target.path.startsWith(root.path)) {
                    Log.w(
                        TAG,
                        "recursivelyDelete() refusing to delete outside app dir: $target"
                    )
                    return@withContext FailedExecution(
                        "Refusing to delete path outside app directory: ${target.path}"
                    )
                }

                if (!target.exists()) {
                    // Nada para deletar; considerar sucesso.
                    return@withContext SuccessfulExecution
                }

                // Escapar aspas simples para uso em shell.
                val safePath = target.path.replace("'", "'\\''")
                val command = "rm -rf '$safePath'"

                Log.d(TAG, "recursivelyDelete(): $command")
                return@withContext executeCommand(command)
            } catch (e: Exception) {
                Log.e(TAG, "recursivelyDelete() failed: $e")
                return@withContext FailedExecution(
                    "recursivelyDelete failed: ${e.message ?: e.toString()}"
                )
            }
        }

    /**
     * Consome a saída do processo linha a linha, notificando o listener.
     * Fecha o InputStream ao final.
     */
    private fun collectOutput(inputStream: InputStream, listener: (String) -> Any) {
        inputStream.bufferedReader(Charsets.UTF_8).use { buf ->
            buf.forEachLine { line ->
                try {
                    listener(line)
                } catch (ignored: Exception) {
                    // Listener não deve quebrar o pipeline de leitura.
                    Log.w(TAG, "Listener threw while handling line: $line", ignored)
                }
            }
        }
    }

    /**
     * Aguarda o término do processo com timeout opcional.
     * Em caso de timeout, tenta destruir o processo e retorna FailedExecution.
     */
    private fun waitForProcessWithTimeout(
        process: Process,
        timeoutMillis: Long
    ): ExecutionResult {
        return try {
            val finished: Boolean = if (timeoutMillis > 0 && Build.VERSION.SDK_INT >= 26) {
                process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            } else {
                // Fallback para APIs mais antigas: espera bloqueante.
                process.waitFor()
                true
            }

            if (!finished) {
                Log.w(TAG, "Process timeout after ${timeoutMillis} ms, destroying...")
                process.destroy()
                if (Build.VERSION.SDK_INT >= 26) {
                    // Dar uma chance para terminar graciosamente
                    if (!process.waitFor(1, TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                    }
                } else {
                    // Em APIs antigas, forçar destruição na sequência.
                    process.destroy()
                }
                FailedExecution("Command timeout after ${timeoutMillis} ms")
            } else {
                val exitCode = process.exitValue()
                if (exitCode == 0) {
                    SuccessfulExecution
                } else {
                    FailedExecution("Command failed with exit code: $exitCode")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "waitForProcessWithTimeout() failed: $e")
            FailedExecution("waitForProcessWithTimeout failed: ${e.message ?: e.toString()}")
        }
    }

    /**
     * Versão simples (sem timeout) usada para comandos básicos.
     */
    private fun getProcessResult(process: Process): ExecutionResult {
        return try {
            val exitCode = process.waitFor()
            if (exitCode == 0) SuccessfulExecution
            else FailedExecution("Command failed with exit code: $exitCode")
        } catch (e: Exception) {
            Log.e(TAG, "getProcessResult() failed: $e")
            FailedExecution("getProcessResult failed: ${e.message ?: e.toString()}")
        }
    }
}

// This class is intended to allow stubbing of elements that are unavailable during unit tests.
class BusyboxWrapper(private val ulaFiles: UlaFiles) {

    /**
     * For basic commands, CWD should be applicationFilesDir.
     * Uses "sh -c" for flexibility, so caller MUST sanitize/quote args.
     */
    fun wrapCommand(command: String): List<String> {
        return listOf(ulaFiles.busybox.path, "sh", "-c", command)
    }

    /**
     * For script calls, split on whitespace and preserve arguments.
     */
    fun wrapScript(command: String): List<String> {
        val args = command
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

        return listOf(ulaFiles.busybox.path, "sh") + args
    }

    fun getBusyboxEnv(): HashMap<String, String> {
        return hashMapOf(
            "LIB_PATH" to ulaFiles.supportDir.absolutePath,
            "ROOT_PATH" to ulaFiles.filesDir.absolutePath
        )
    }

    fun busyboxIsPresent(): Boolean {
        return ulaFiles.busybox.exists()
    }

    // Proot scripts expect CWD to be applicationFilesDir/<filesystem>
    fun addBusyboxAndProot(command: String): List<String> {
        val args = command
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

        return listOf(
            ulaFiles.busybox.absolutePath,
            "sh",
            "support/execInProot.sh"
        ) + args
    }

    fun getProotEnv(filesystemDir: File, prootDebugLevel: String): HashMap<String, String> {
        // TODO This hack should be removed once there are no users on releases 2.5.14 - 2.6.1
        handleHangingBindingDirectories(filesystemDir)

        val emulatedStorageBinding =
            "-b ${ulaFiles.emulatedUserDir.absolutePath}:/storage/internal"
        val externalStorageBinding = ulaFiles.sdCardUserDir?.run {
            "-b ${this.absolutePath}:/storage/sdcard"
        } ?: ""

        val bindings = "$emulatedStorageBinding $externalStorageBinding"
        val osVersion = System.getProperty("os.version") ?: "unknown"

        return hashMapOf(
            "LD_LIBRARY_PATH" to ulaFiles.supportDir.absolutePath,
            "LIB_PATH" to ulaFiles.supportDir.absolutePath,
            "ROOT_PATH" to ulaFiles.filesDir.absolutePath,
            "ROOTFS_PATH" to filesystemDir.absolutePath,
            "PROOT_DEBUG_LEVEL" to prootDebugLevel,
            "EXTRA_BINDINGS" to bindings,
            "OS_VERSION" to osVersion
        )
    }

    fun prootIsPresent(): Boolean {
        return ulaFiles.proot.exists()
    }

    fun executionScriptIsPresent(): Boolean {
        val execInProotFile = File(ulaFiles.supportDir, "execInProot.sh")
        return execInProotFile.exists()
    }

    // TODO this hack should be removed when no users are left using version 2.5.14 - 2.6.1
    private fun handleHangingBindingDirectories(filesystemDir: File) {
        // If users upgraded from a version 2.5.14 - 2.6.1, the storage directory will exist but
        // with unusable permissions. It needs to be recreated.
        val storageBindingDir = File(filesystemDir, "storage")
        val storageBindingDirEmpty = storageBindingDir.listFiles()?.isEmpty() ?: true
        if (storageBindingDir.exists() && storageBindingDir.isDirectory && storageBindingDirEmpty) {
            storageBindingDir.delete()
        }
        storageBindingDir.mkdirs()

        // If users upgraded from a version before 2.5.14, the old sdcard binding should be removed
        // to increase clarity.
        val sdCardBindingDir = File(filesystemDir, "sdcard")
        val sdCardBindingDirEmpty = sdCardBindingDir.listFiles()?.isEmpty() ?: true
        if (sdCardBindingDir.exists() && sdCardBindingDir.isDirectory && sdCardBindingDirEmpty) {
            sdCardBindingDir.delete()
        }
    }
}
