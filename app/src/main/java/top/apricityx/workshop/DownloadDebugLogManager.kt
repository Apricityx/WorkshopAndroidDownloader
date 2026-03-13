package top.apricityx.workshop

import android.app.Application
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DownloadDebugLogManager(
    private val application: Application,
) {
    private val lock = Any()

    fun initializeTaskLog(task: DownloadCenterTaskUiState) {
        val file = taskLogFile(task.id)
        runCatching {
            synchronized(lock) {
                if (!file.exists() || file.length() == 0L) {
                    ensureParentExists(file)
                    file.writeText(
                        buildString {
                            appendLine("# Steam Workshop download debug log")
                            appendLine("# createdAt=${timestamp()}")
                            appendLine("# taskId=${task.id}")
                            appendLine("# appId=${task.appId}")
                            appendLine("# publishedFileId=${task.publishedFileId}")
                            appendLine("# gameTitle=${task.gameTitle}")
                            appendLine("# itemTitle=${task.itemTitle}")
                            appendLine("# manufacturer=${Build.MANUFACTURER}")
                            appendLine("# brand=${Build.BRAND}")
                            appendLine("# model=${Build.MODEL}")
                            appendLine("# sdkInt=${Build.VERSION.SDK_INT}")
                            appendLine("# release=${Build.VERSION.RELEASE}")
                            appendLine("# incremental=${Build.VERSION.INCREMENTAL}")
                            appendLine("# logFile=${file.absolutePath}")
                        },
                        Charsets.UTF_8,
                    )
                }
            }
        }.onFailure { error ->
            Log.w(WorkshopAppContract.logTag, "Failed to initialize debug log for taskId=${task.id}", error)
        }
    }

    fun logFilePath(taskId: String): String = taskLogFile(taskId).absolutePath

    fun append(taskId: String, line: String) {
        val file = taskLogFile(taskId)
        runCatching {
            synchronized(lock) {
                ensureParentExists(file)
                file.appendText("${timestamp()} $line\n", Charsets.UTF_8)
            }
        }.onFailure { error ->
            Log.w(WorkshopAppContract.logTag, "Failed to append debug log for taskId=$taskId", error)
        }
    }

    fun appendError(
        taskId: String,
        summary: String,
        error: Throwable,
    ) {
        append(taskId, summary)
        append(taskId, error.stackTraceSummary())
    }

    fun shareableFile(task: DownloadCenterTaskUiState): ExportedDownloadFile? {
        val file = taskLogFile(task.id)
        if (!file.isFile) {
            return null
        }

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

    private fun taskLogFile(taskId: String): File =
        File(logRootDir(), "download-debug-$taskId.txt")

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

    private fun timestamp(): String =
        TIMESTAMP_FORMAT.format(Date())

    companion object {
        private val TIMESTAMP_FORMAT =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }
}

private fun Throwable.stackTraceSummary(): String {
    val writer = StringWriter()
    printStackTrace(PrintWriter(writer))
    return writer.toString().trimEnd()
}
