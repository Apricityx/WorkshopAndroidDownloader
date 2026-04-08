package top.apricityx.workshop

object WorkshopInputValidator {
    fun validate(appIdText: String, publishedFileIdText: String): String? {
        if (appIdText.isBlank() || publishedFileIdText.isBlank()) {
            return "AppID and PublishedFileId are required."
        }
        if (appIdText.toUIntOrNull() == null || appIdText == "0") {
            return "AppID must be a positive integer."
        }
        if (WorkshopPublishedFileIdParser.parse(publishedFileIdText) == null) {
            return "PublishedFileId must be a positive integer or a valid workshop link."
        }
        return null
    }
}
