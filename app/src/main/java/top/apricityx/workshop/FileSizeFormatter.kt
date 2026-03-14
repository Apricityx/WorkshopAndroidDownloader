package top.apricityx.workshop

import java.text.DecimalFormat

fun formatBinaryFileSize(bytes: Long): String {
    if (bytes <= 0L) {
        return "0 B"
    }
    if (bytes < 1024L) {
        return "$bytes B"
    }

    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return "${DecimalFormat("0.0").format(value)} ${units[unitIndex.coerceAtLeast(0)]}"
}
