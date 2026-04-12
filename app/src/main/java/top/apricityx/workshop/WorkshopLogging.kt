package top.apricityx.workshop

import com.elvishew.xlog.XLog

internal fun workshopLogInfo(message: String) {
    runCatching {
        XLog.tag(WorkshopAppContract.logTag).i(message)
    }
}

internal fun workshopLogWarn(
    message: String,
    error: Throwable? = null,
) {
    runCatching {
        if (error == null) {
            XLog.tag(WorkshopAppContract.logTag).w(message)
        } else {
            XLog.tag(WorkshopAppContract.logTag).w(message, error)
        }
    }
}

internal fun workshopLogError(
    message: String,
    error: Throwable? = null,
) {
    runCatching {
        if (error == null) {
            XLog.tag(WorkshopAppContract.logTag).e(message)
        } else {
            XLog.tag(WorkshopAppContract.logTag).e(message, error)
        }
    }
}
