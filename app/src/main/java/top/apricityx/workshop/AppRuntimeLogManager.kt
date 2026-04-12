package top.apricityx.workshop

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppRuntimeLogManager {
    private const val runtimeLogFolderName = "runtime-logs"
    private const val logBundleFolderName = "runtime-log-bundles"
    private const val fatalCrashFileName = "fatal-crash-last.txt"
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val bundleNameFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
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

    fun latestLogPath(application: Application): String? =
        latestShareableLogFile(application)?.absolutePath

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

    suspend fun shareableLogBundle(application: Application): ExportedDownloadFile? = withContext(Dispatchers.IO) {
        val bundleFile = createLogBundle(
            application = application,
            outputDir = bundleDirectory(application),
        )
        cleanupOldBundles(bundleDirectory(application))
        bundleFile.toExportedDownloadFile(application)
    }

    suspend fun exportLogBundle(application: Application): ExportedDownloadFile? = withContext(Dispatchers.IO) {
        val bundleFile = createLogBundle(
            application = application,
            outputDir = bundleDirectory(application),
        )
        cleanupOldBundles(bundleDirectory(application))
        try {
            exportBundleToDownloads(application, bundleFile)
        } finally {
            bundleFile.delete()
        }
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

    private fun bundleDirectory(application: Application): File =
        File(application.filesDir, "debug/$logBundleFolderName").apply {
            if (!exists()) {
                mkdirs()
            }
        }

    private fun debugRoots(application: Application): List<File> =
        buildList {
            application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?.let { File(it, "debug") }
                ?.takeIf(File::exists)
                ?.let(::add)
            File(application.filesDir, "debug")
                .takeIf(File::exists)
                ?.takeIf { internal ->
                    none { existing -> existing.absolutePath == internal.absolutePath }
                }
                ?.let(::add)
        }

    private fun createLogBundle(
        application: Application,
        outputDir: File,
    ): File {
        outputDir.mkdirs()
        val bundleFile = File(outputDir, "workshop-runtime-logs-${bundleNameFormat.format(Date())}.zip")
        val sourceFiles = collectDebugFiles(application)
        ZipOutputStream(bundleFile.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.txt"))
            zip.write(buildBundleManifest(application, sourceFiles).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            sourceFiles.forEach { source ->
                zip.putNextEntry(ZipEntry(source.entryName))
                source.file.inputStream().buffered().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return bundleFile
    }

    private fun collectDebugFiles(application: Application): List<LogBundleSource> =
        debugRoots(application).flatMap { root ->
            val rootLabel = if (root.absolutePath.startsWith(application.filesDir.absolutePath)) {
                "internal-debug"
            } else {
                "external-debug"
            }
            root.walkTopDown()
                .filter(File::isFile)
                .filterNot { file ->
                    file.absolutePath.contains("$logBundleFolderName${File.separator}") ||
                        file.name.endsWith(".zip", ignoreCase = true) && file.name.startsWith("workshop-runtime-logs-")
                }
                .map { file ->
                    LogBundleSource(
                        file = file,
                        entryName = "$rootLabel/${file.relativeTo(root).invariantSeparatorsPath}",
                    )
                }
                .toList()
        }.distinctBy { it.file.absolutePath }

    private fun buildBundleManifest(
        application: Application,
        sourceFiles: List<LogBundleSource>,
    ): String =
        buildString {
            appendLine("# Workshop runtime log bundle")
            appendLine("# createdAt=${timestampFormat.format(Date())}")
            appendLine("# versionName=${BuildConfig.VERSION_NAME}")
            appendLine("# manufacturer=${android.os.Build.MANUFACTURER}")
            appendLine("# brand=${android.os.Build.BRAND}")
            appendLine("# model=${android.os.Build.MODEL}")
            appendLine("# sdkInt=${android.os.Build.VERSION.SDK_INT}")
            appendLine("# release=${android.os.Build.VERSION.RELEASE}")
            appendLine("# incremental=${android.os.Build.VERSION.INCREMENTAL}")
            appendLine("# runtimeLogDir=${runtimeLogDir(application).absolutePath}")
            appendLine("# sourceFileCount=${sourceFiles.size}")
            appendLine()
            sourceFiles.forEach { source ->
                appendLine("${source.entryName}\t${source.file.length()}\t${source.file.lastModified()}")
            }
        }

    private fun exportBundleToDownloads(
        application: Application,
        bundleFile: File,
    ): ExportedDownloadFile? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportBundleToMediaStore(application, bundleFile)
        } else {
            exportBundleToLegacyDownloads(application, bundleFile)
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportBundleToMediaStore(
        application: Application,
        bundleFile: File,
    ): ExportedDownloadFile? {
        val relativePath = downloadsRootRelativePath()
        val resolver = application.contentResolver
        val itemUri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, bundleFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            },
        ) ?: return null

        return runCatching {
            resolver.openOutputStream(itemUri, "w")?.use { output ->
                bundleFile.inputStream().buffered().use { input -> input.copyTo(output) }
            } ?: error("Failed to open MediaStore output stream for runtime log bundle.")
            resolver.update(
                itemUri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            ExportedDownloadFile(
                relativePath = bundleFile.name,
                sizeBytes = bundleFile.length(),
                modifiedEpochMillis = System.currentTimeMillis(),
                contentUri = itemUri.toString(),
                userVisiblePath = relativePath + bundleFile.name,
            )
        }.getOrElse {
            resolver.delete(itemUri, null, null)
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun exportBundleToLegacyDownloads(
        application: Application,
        bundleFile: File,
    ): ExportedDownloadFile? {
        val publicRoot = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS ?: "Download",
        ).takeIf {
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        }
        val appSpecificRoot = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(application.filesDir, "exports/downloads")

        val destinationFile = runCatching {
            val destinationDir = File(publicRoot ?: appSpecificRoot, "workshop/logs").apply { mkdirs() }
            File(destinationDir, bundleFile.name).also { bundleFile.copyTo(it, overwrite = true) }
        }.getOrElse {
            val fallbackDir = File(appSpecificRoot, "workshop/logs").apply { mkdirs() }
            File(fallbackDir, bundleFile.name).also { bundleFile.copyTo(it, overwrite = true) }
        }

        return destinationFile.toExportedDownloadFile(application)
    }

    private fun File.toExportedDownloadFile(application: Application): ExportedDownloadFile? {
        val contentUri = runCatching {
            FileProvider.getUriForFile(
                application,
                "${application.packageName}.fileprovider",
                this,
            )
        }.getOrNull() ?: return null
        return ExportedDownloadFile(
            relativePath = name,
            sizeBytes = length(),
            modifiedEpochMillis = lastModified(),
            contentUri = contentUri.toString(),
            userVisiblePath = absolutePath,
        )
    }

    private fun cleanupOldBundles(directory: File) {
        directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith("workshop-runtime-logs-") && it.name.endsWith(".zip") }
            .sortedByDescending(File::lastModified)
            .drop(4)
            .forEach(File::delete)
    }

    private fun downloadsRootRelativePath(): String =
        (Environment.DIRECTORY_DOWNLOADS ?: "Download") + "/workshop/logs/"
}

private data class LogBundleSource(
    val file: File,
    val entryName: String,
)

private fun Throwable.stackTraceSummary(): String {
    val writer = StringWriter()
    printStackTrace(PrintWriter(writer))
    return writer.toString().trimEnd()
}
