package top.apricityx.workshop

import android.app.Application
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.elvishew.xlog.XLog.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

internal class SteamLoginDebugLogManager(
    private val application: Application,
) {
    private val lock = Any()

    fun startAttempt(
        mode: SteamLoginInputMode,
        dialogMode: SteamLoginDialogMode,
        accountNameHint: String?,
        targetAccountId: String?,
    ): String {
        val attemptId = UUID.randomUUID().toString()
        val file = logFile(attemptId)
        runCatching {
            synchronized(lock) {
                ensureParentExists(file)
                file.writeText(
                    buildString {
                        appendLine("# Steam login debug log")
                        appendLine("# createdAt=${timestamp()}")
                        appendLine("# attemptId=$attemptId")
                        appendLine("# inputMode=${mode.name}")
                        appendLine("# dialogMode=${dialogMode.name}")
                        appendLine("# accountHint=${maskForLog(accountNameHint)}")
                        appendLine("# targetAccountIdPresent=${targetAccountId != null}")
                        appendLine("# manufacturer=${Build.MANUFACTURER}")
                        appendLine("# brand=${Build.BRAND}")
                        appendLine("# model=${Build.MODEL}")
                        appendLine("# sdkInt=${Build.VERSION.SDK_INT}")
                        appendLine("# release=${Build.VERSION.RELEASE}")
                        appendLine("# incremental=${Build.VERSION.INCREMENTAL}")
                        appendLine("# logFile=${file.absolutePath}")
                        appendLine("# runtimeLogDir=${AppRuntimeLogManager.logDirectoryPath(application)}")
                        appendLine("# runtimeCrashLog=${AppRuntimeLogManager.crashLogPath(application)}")
                    },
                    Charsets.UTF_8,
                )
            }
        }.onFailure { error ->
            Log.w(WorkshopAppContract.logTag, "Failed to initialize Steam login debug log", error)
        }
        return attemptId
    }

    fun logFilePath(attemptId: String): String = logFile(attemptId).absolutePath

    fun append(
        attemptId: String,
        line: String,
    ) {
        val file = logFile(attemptId)
        runCatching {
            synchronized(lock) {
                ensureParentExists(file)
                file.appendText("${timestamp()} $line\n", Charsets.UTF_8)
            }
        }.onFailure { error ->
            Log.w(WorkshopAppContract.logTag, "Failed to append Steam login debug log", error)
        }
    }

    fun appendError(
        attemptId: String,
        summary: String,
        error: Throwable,
    ) {
        append(attemptId, summary)
        append(attemptId, error.stackTraceSummary())
    }

    fun latestLogPath(): String? = latestLogFile()?.absolutePath

    fun latestShareableFile(): ExportedDownloadFile? {
        val file = latestLogFile() ?: return null
        val contentUri = runCatching {
            FileProvider.getUriForFile(
                application,
                "${application.packageName}.fileprovider",
                file,
            )
        }.getOrNull() ?: return null
        return ExportedDownloadFile(
            relativePath = file.name,
            sizeBytes = file.length(),
            modifiedEpochMillis = file.lastModified(),
            contentUri = contentUri.toString(),
            userVisiblePath = file.absolutePath,
        )
    }

    private fun latestLogFile(): File? =
        logRootDir()
            .takeIf(File::isDirectory)
            ?.listFiles { file -> file.isFile && file.name.startsWith(LOG_FILE_PREFIX) && file.name.endsWith(".txt") }
            ?.maxByOrNull(File::lastModified)

    private fun logFile(attemptId: String): File =
        File(logRootDir(), "$LOG_FILE_PREFIX$attemptId.txt")

    private fun logRootDir(): File {
        val externalRoot = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.let { File(it, "debug") }
        if (externalRoot != null && (externalRoot.exists() || externalRoot.mkdirs())) {
            return externalRoot
        }
        return File(application.filesDir, "debug").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private fun ensureParentExists(file: File) {
        val parent = file.parentFile ?: return
        if (!parent.exists()) {
            parent.mkdirs()
        }
    }

    private fun timestamp(): String = TIMESTAMP_FORMAT.format(Date())

    private fun maskForLog(value: String?): String =
        value
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { trimmed ->
                when {
                    trimmed.length <= 2 -> "*".repeat(trimmed.length)
                    else -> "${trimmed.first()}***${trimmed.last()}"
                }
            }
            ?: "-"

    private companion object {
        private const val LOG_FILE_PREFIX = "steam-login-debug-"
        private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }
}

private fun Throwable.stackTraceSummary(): String {
    val writer = StringWriter()
    printStackTrace(PrintWriter(writer))
    return writer.toString().trimEnd()
}
