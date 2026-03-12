import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

data class AdbCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val combinedOutput: String
        get() = buildString {
            if (stdout.isNotBlank()) {
                append(stdout.trim())
            }
            if (stderr.isNotBlank()) {
                if (isNotEmpty()) {
                    append(System.lineSeparator())
                }
                append(stderr.trim())
            }
        }
}

enum class WorkshopWaitStatus {
    Success,
    Failed,
    TimedOut,
}

data class WorkshopWaitResult(
    val status: WorkshopWaitStatus,
    val logcat: String,
)

val workshopPackageName = "top.apricityx.workshop"
val workshopMainActivity = "$workshopPackageName/.MainActivity"
val workshopActionDownload = "top.apricityx.workshop.action.DOWNLOAD"
val workshopLogcatTag = "WorkshopAdb"

fun Project.workshopStringProperty(name: String): String? =
    findProperty(name)?.toString()?.trim()?.takeIf { it.isNotEmpty() }

fun Project.requireWorkshopProperty(name: String): String =
    workshopStringProperty(name) ?: throw GradleException("Missing -P$name=<value>")

fun Project.resolveWorkshopSdkDir(): File {
    val localProperties = rootProject.file("local.properties")
    if (localProperties.exists()) {
        val properties = Properties()
        localProperties.inputStream().use(properties::load)
        properties.getProperty("sdk.dir")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.let { if (it.exists()) return it }
    }

    listOf("ANDROID_SDK_ROOT", "ANDROID_HOME")
        .mapNotNull(System::getenv)
        .map(::File)
        .firstOrNull(File::exists)
        ?.let { return it }

    throw GradleException("Android SDK not found. Set sdk.dir in local.properties or ANDROID_SDK_ROOT.")
}

fun Project.resolveWorkshopAdb(): File {
    val adbName = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "adb.exe" else "adb"
    val adb = resolveWorkshopSdkDir().resolve("platform-tools").resolve(adbName)
    if (!adb.exists()) {
        throw GradleException("adb not found at ${adb.absolutePath}")
    }
    return adb
}

fun Project.runAdbCommand(
    adb: File,
    serial: String?,
    vararg args: String,
    ignoreExitValue: Boolean = false,
): AdbCommandResult {
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    val command = buildList {
        add(adb.absolutePath)
        if (!serial.isNullOrBlank()) {
            add("-s")
            add(serial)
        }
        addAll(args)
    }

    val result = exec {
        commandLine(command)
        standardOutput = stdout
        errorOutput = stderr
        isIgnoreExitValue = true
    }

    val adbResult = AdbCommandResult(
        exitCode = result.exitValue,
        stdout = stdout.toString(Charsets.UTF_8.name()),
        stderr = stderr.toString(Charsets.UTF_8.name()),
    )

    if (!ignoreExitValue && adbResult.exitCode != 0) {
        throw GradleException(
            "adb command failed (${command.joinToString(" ")}): ${adbResult.combinedOutput.ifBlank { "exit=${adbResult.exitCode}" }}",
        )
    }

    return adbResult
}

fun Project.resolveWorkshopDeviceSerial(adb: File): String {
    val requestedSerial = workshopStringProperty("deviceSerial")
    val devicesOutput = runAdbCommand(adb, null, "devices").stdout
    val connectedDevices = devicesOutput.lineSequence()
        .drop(1)
        .map(String::trim)
        .filter { it.endsWith("\tdevice") }
        .map { it.substringBefore('\t') }
        .toList()

    if (connectedDevices.isEmpty()) {
        throw GradleException("No adb device is connected.")
    }

    if (requestedSerial != null) {
        if (requestedSerial !in connectedDevices) {
            throw GradleException("Requested device serial $requestedSerial is not connected. Connected: ${connectedDevices.joinToString()}")
        }
        return requestedSerial
    }

    if (connectedDevices.size > 1) {
        throw GradleException("Multiple adb devices are connected: ${connectedDevices.joinToString()}. Pass -PdeviceSerial=<serial>.")
    }

    return connectedDevices.single()
}

fun Project.resolveWorkshopArtifactsDir(appId: String, publishedFileId: String): File {
    val baseDir = workshopStringProperty("logsDir")
        ?.let(::File)
        ?: rootProject.layout.buildDirectory.dir("workshop-adb").get().asFile
    val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
    return baseDir.resolve("app-$appId-file-$publishedFileId-$timestamp").apply { mkdirs() }
}

fun Project.pullPrivateFile(
    adb: File,
    serial: String,
    remotePath: String,
): String? {
    val result = runAdbCommand(
        adb,
        serial,
        "exec-out",
        "run-as",
        workshopPackageName,
        "sh",
        "-c",
        "cat $remotePath 2>/dev/null",
        ignoreExitValue = true,
    )
    return result.stdout.takeIf { it.isNotBlank() }
}

fun Project.waitForWorkshopCompletion(
    adb: File,
    serial: String,
    timeoutSeconds: Long,
    pollIntervalMillis: Long,
): WorkshopWaitResult {
    val deadline = System.nanoTime() + timeoutSeconds * 1_000_000_000L
    var latestLogcat: String

    while (System.nanoTime() < deadline) {
        latestLogcat = runAdbCommand(
            adb,
            serial,
            "logcat",
            "-d",
            "-s",
            workshopLogcatTag,
            ignoreExitValue = true,
        ).combinedOutput

        when {
            latestLogcat.contains("Download completed files=") -> {
                return WorkshopWaitResult(WorkshopWaitStatus.Success, latestLogcat)
            }
            latestLogcat.contains("Download failed ") || latestLogcat.contains("State=Failed") -> {
                return WorkshopWaitResult(WorkshopWaitStatus.Failed, latestLogcat)
            }
        }

        Thread.sleep(pollIntervalMillis)
    }

    latestLogcat = runAdbCommand(
        adb,
        serial,
        "logcat",
        "-d",
        "-s",
        workshopLogcatTag,
        ignoreExitValue = true,
    ).combinedOutput

    return WorkshopWaitResult(WorkshopWaitStatus.TimedOut, latestLogcat)
}

fun Project.installWorkshopDebugApk(
    adb: File,
    serial: String,
    apk: File,
) {
    if (!apk.exists()) {
        throw GradleException("Debug APK not found at ${apk.absolutePath}. Run assembleDebug first.")
    }

    logger.lifecycle("Installing ${apk.name} to $serial")
    val installResult = runAdbCommand(adb, serial, "install", "-r", apk.absolutePath)
    if (installResult.combinedOutput.isNotBlank()) {
        logger.lifecycle(installResult.combinedOutput)
    }
}

fun Project.startWorkshopDownload(
    adb: File,
    serial: String,
    appId: String,
    publishedFileId: String,
) {
    runAdbCommand(adb, serial, "shell", "am", "force-stop", workshopPackageName, ignoreExitValue = true)
    runAdbCommand(
        adb,
        serial,
        "shell",
        "run-as",
        workshopPackageName,
        "sh",
        "-c",
        "rm -rf files/workshop/$appId/$publishedFileId",
        ignoreExitValue = true,
    )
    runAdbCommand(adb, serial, "logcat", "-c", ignoreExitValue = true)

    val startResult = runAdbCommand(
        adb,
        serial,
        "shell",
        "am",
        "start",
        "-W",
        "-a",
        workshopActionDownload,
        "-n",
        workshopMainActivity,
        "--es",
        "app_id",
        appId,
        "--es",
        "published_file_id",
        publishedFileId,
        "--ez",
        "auto_start",
        "true",
    )

    if (startResult.combinedOutput.isNotBlank()) {
        logger.lifecycle(startResult.combinedOutput)
    }
}

fun Project.exportWorkshopArtifacts(
    adb: File,
    serial: String,
    appId: String,
    publishedFileId: String,
    outputDir: File,
    logcat: String,
) {
    outputDir.resolve("logcat.txt").writeText(logcat)

    val remoteBase = "files/workshop/$appId/$publishedFileId"
    val downloadLog = pullPrivateFile(adb, serial, "$remoteBase/download.log")
    val metadata = pullPrivateFile(adb, serial, "$remoteBase/metadata.json")

    if (downloadLog != null) {
        outputDir.resolve("download.log").writeText(downloadLog)
    } else {
        outputDir.resolve("download.log.missing.txt").writeText("download.log was not found in $remoteBase")
    }

    if (metadata != null) {
        outputDir.resolve("metadata.json").writeText(metadata)
    } else {
        outputDir.resolve("metadata.missing.txt").writeText("metadata.json was not found in $remoteBase")
    }
}

tasks.register("workshopStartDownload") {
    group = "workshop"
    description = "Installs the debug APK and starts a workshop download on the connected adb device."
    dependsOn("assembleDebug")

    doLast {
        val appId = requireWorkshopProperty("workshopAppId")
        val publishedFileId = requireWorkshopProperty("publishedFileId")
        val adb = resolveWorkshopAdb()
        val serial = resolveWorkshopDeviceSerial(adb)
        val apk = project.layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile

        installWorkshopDebugApk(adb, serial, apk)
        startWorkshopDownload(adb, serial, appId, publishedFileId)
    }
}

tasks.register("workshopPullLogs") {
    group = "workshop"
    description = "Pulls workshop download artifacts from the connected adb device."

    doLast {
        val appId = requireWorkshopProperty("workshopAppId")
        val publishedFileId = requireWorkshopProperty("publishedFileId")
        val adb = resolveWorkshopAdb()
        val serial = resolveWorkshopDeviceSerial(adb)
        val artifactsDir = resolveWorkshopArtifactsDir(appId, publishedFileId)
        val logcat = runAdbCommand(
            adb,
            serial,
            "logcat",
            "-d",
            "-s",
            workshopLogcatTag,
            ignoreExitValue = true,
        ).combinedOutput

        exportWorkshopArtifacts(adb, serial, appId, publishedFileId, artifactsDir, logcat)
        logger.lifecycle("Workshop artifacts exported to ${artifactsDir.absolutePath}")
    }
}

tasks.register("workshopDownloadAndPullLogs") {
    group = "workshop"
    description = "Installs the debug APK, starts a workshop download, waits for completion, and pulls the resulting logs."
    dependsOn("assembleDebug")

    doLast {
        val appId = requireWorkshopProperty("workshopAppId")
        val publishedFileId = requireWorkshopProperty("publishedFileId")
        val timeoutSeconds = workshopStringProperty("downloadTimeoutSeconds")?.toLongOrNull() ?: 300L
        val pollIntervalMillis = workshopStringProperty("pollIntervalMillis")?.toLongOrNull() ?: 2_000L
        val adb = resolveWorkshopAdb()
        val serial = resolveWorkshopDeviceSerial(adb)
        val apk = project.layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        val artifactsDir = resolveWorkshopArtifactsDir(appId, publishedFileId)

        installWorkshopDebugApk(adb, serial, apk)
        startWorkshopDownload(adb, serial, appId, publishedFileId)

        logger.lifecycle("Waiting for workshop download to finish on $serial")
        val waitResult = waitForWorkshopCompletion(adb, serial, timeoutSeconds, pollIntervalMillis)
        exportWorkshopArtifacts(adb, serial, appId, publishedFileId, artifactsDir, waitResult.logcat)

        val summary = buildString {
            appendLine("status=${waitResult.status}")
            appendLine("deviceSerial=$serial")
            appendLine("appId=$appId")
            appendLine("publishedFileId=$publishedFileId")
            appendLine("artifactsDir=${artifactsDir.absolutePath}")
        }
        artifactsDir.resolve("result.txt").writeText(summary)
        logger.lifecycle("Workshop artifacts exported to ${artifactsDir.absolutePath}")

        val pulledDownloadLog = artifactsDir.resolve("download.log")
        if (!pulledDownloadLog.exists()) {
            throw GradleException("download.log was not found on device. Artifacts: ${artifactsDir.absolutePath}")
        }

        when (waitResult.status) {
            WorkshopWaitStatus.Success -> logger.lifecycle("Workshop download finished successfully.")
            WorkshopWaitStatus.Failed -> throw GradleException("Workshop download failed. See ${artifactsDir.absolutePath}")
            WorkshopWaitStatus.TimedOut -> throw GradleException("Workshop download timed out after ${timeoutSeconds}s. See ${artifactsDir.absolutePath}")
        }
    }
}
