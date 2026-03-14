package top.apricityx.workshop

import android.app.Application
import android.os.Environment
import androidx.core.content.FileProvider
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.XLog
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.file.FilePrinter
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppRuntimeLogManager {
    private const val runtimeLogFolderName = "runtime-logs"
    private const val fatalCrashFileName = "fatal-crash-last.txt"
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    @Volatile
    private var initialized = false

    fun initialize(application: Application) {
        if (initialized) {
            return
        }

        synchronized(lock) {
            if (initialized) {
                return
            }

            val runtimeLogDir = runtimeLogDir(application).apply { mkdirs() }
            val configuration = LogConfiguration.Builder()
                .tag(WorkshopAppContract.logTag)
                .build()
            val filePrinter = FilePrinter.Builder(runtimeLogDir.absolutePath).build()
            XLog.init(configuration, AndroidPrinter(), filePrinter)
            installUncaughtExceptionHandler(application)
            XLog.tag(WorkshopAppContract.logTag).i("Runtime logging initialized dir=%s", runtimeLogDir.absolutePath)
            initialized = true
        }
    }

    fun logDirectoryPath(application: Application): String =
        runtimeLogDir(application).absolutePath

    fun crashLogPath(application: Application): String =
        fatalCrashFile(application).absolutePath

    fun shareableLatestLogFile(application: Application): ExportedDownloadFile? {
        val file = latestShareableLogFile(application) ?: return null
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

    private fun latestShareableLogFile(application: Application): File? {
        val candidates = buildList {
            addAll(runtimeLogDir(application).listFiles().orEmpty().filter(File::isFile))
            fatalCrashFile(application).takeIf(File::isFile)?.let(::add)
        }
        return candidates.maxByOrNull(File::lastModified)
    }

    private fun installUncaughtExceptionHandler(application: Application) {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeFatalCrash(application, thread, throwable)
            runCatching {
                XLog.tag(WorkshopAppContract.logTag)
                    .e("Uncaught exception on thread ${thread.name}\n${throwable.stackTraceSummary()}")
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeFatalCrash(
        application: Application,
        thread: Thread,
        throwable: Throwable,
    ) {
        val file = fatalCrashFile(application)
        synchronized(lock) {
            file.parentFile?.mkdirs()
            file.writeText(
                buildString {
                    appendLine("# Workshop runtime crash log")
                    appendLine("# timestamp=${timestampFormat.format(Date())}")
                    appendLine("# thread=${thread.name}")
                    appendLine("# manufacturer=${android.os.Build.MANUFACTURER}")
                    appendLine("# brand=${android.os.Build.BRAND}")
                    appendLine("# model=${android.os.Build.MODEL}")
                    appendLine("# sdkInt=${android.os.Build.VERSION.SDK_INT}")
                    appendLine("# release=${android.os.Build.VERSION.RELEASE}")
                    appendLine("# incremental=${android.os.Build.VERSION.INCREMENTAL}")
                    appendLine()
                    appendLine(throwable.stackTraceSummary())
                },
                Charsets.UTF_8,
            )
        }
    }

    private fun runtimeLogDir(application: Application): File {
        val externalRoot = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.let { File(it, "debug/$runtimeLogFolderName") }
        if (externalRoot != null && (externalRoot.exists() || externalRoot.mkdirs())) {
            return externalRoot
        }

        return File(application.filesDir, "debug/$runtimeLogFolderName").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private fun fatalCrashFile(application: Application): File =
        File(runtimeLogDir(application), fatalCrashFileName)
}

private fun Throwable.stackTraceSummary(): String {
    val writer = StringWriter()
    printStackTrace(PrintWriter(writer))
    return writer.toString().trimEnd()
}
