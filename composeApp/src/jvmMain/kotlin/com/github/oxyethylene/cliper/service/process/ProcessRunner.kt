package com.github.oxyethylene.cliper.service.process

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

interface ProcessRunner {
    suspend fun run(
        command: List<String>,
        acceptedExitCodes: Set<Int> = setOf(0),
        onStdErrLine: ((String) -> Unit)? = null,
    ): CommandResult

    fun cancelActiveProcess()
}

class DefaultProcessRunner : ProcessRunner {
    private val activeProcess = AtomicReference<Process?>(null)

    override suspend fun run(
        command: List<String>,
        acceptedExitCodes: Set<Int>,
        onStdErrLine: ((String) -> Unit)?,
    ): CommandResult = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(false)
                .start()
            activeProcess.set(process)

            val stdout = StringBuilder()
            val stderr = StringBuilder()

            coroutineScope {
                val stdoutReader = async {
                    process.inputStream.consumeLines { line ->
                        stdout.appendLine(line)
                    }
                }

                val stderrReader = async {
                    process.errorStream.consumeLines { line ->
                        stderr.appendLine(line)
                        onStdErrLine?.invoke(line)
                    }
                }

                val waiter = async { process.waitFor() }
                awaitAll(stdoutReader, stderrReader, waiter)
            }

            val exitCode = process.exitValue()
            val result = CommandResult(
                exitCode = exitCode,
                stdout = stdout.toString(),
                stderr = stderr.toString(),
            )

            if (!acceptedExitCodes.contains(exitCode)) {
                throw IllegalStateException(
                    buildString {
                        append("Command failed with exit code ")
                        append(exitCode)
                        append(".\n")
                        append(result.stderr.ifBlank { result.stdout })
                    },
                )
            }

            result
        } catch (cancelled: CancellationException) {
            cancelActiveProcess()
            throw cancelled
        } finally {
            activeProcess.set(null)
        }
    }

    override fun cancelActiveProcess() {
        val process = activeProcess.getAndSet(null) ?: return
        process.destroy()
        if (process.isAlive) {
            process.destroyForcibly()
        }
    }

    private fun java.io.InputStream.consumeLines(onLine: (String) -> Unit) {
        BufferedReader(InputStreamReader(this, StandardCharsets.UTF_8)).use { reader ->
            reader.lineSequence().forEach(onLine)
        }
    }
}
