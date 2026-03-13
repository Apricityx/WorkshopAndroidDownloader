package top.apricityx.workshop

import android.app.Application
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.Closeable
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class OnDeviceDescriptionTranslator(
    application: Application,
) : Closeable {
    private val languageIdentifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(LANGUAGE_CONFIDENCE_THRESHOLD)
            .build(),
    )

    suspend fun translateDescription(
        text: String,
        targetLocale: Locale = Locale.getDefault(),
    ): String = withContext(Dispatchers.IO) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) {
            return@withContext normalizedText
        }

        val targetLanguage = resolveTargetLanguage(targetLocale)
        val sourceLanguage = detectSupportedSourceLanguage(normalizedText)
        if (sourceLanguage == targetLanguage) {
            return@withContext normalizedText
        }

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build(),
        )

        try {
            translator.downloadModelIfNeeded().awaitMlKit()
            translator.translate(normalizedText).awaitMlKit()
        } finally {
            translator.close()
        }
    }

    override fun close() {
        languageIdentifier.close()
    }

    private suspend fun detectSupportedSourceLanguage(text: String): String {
        val detectedLanguageTag = languageIdentifier.identifyLanguage(text).awaitMlKit()
        TranslateLanguage.fromLanguageTag(detectedLanguageTag)?.let { language ->
            return language
        }

        return languageIdentifier.identifyPossibleLanguages(text)
            .awaitMlKit()
            .asSequence()
            .mapNotNull { candidate -> TranslateLanguage.fromLanguageTag(candidate.languageTag) }
            .firstOrNull()
            ?: throw IllegalStateException("暂时无法识别这段描述的语言。")
    }

    companion object {
        private const val LANGUAGE_CONFIDENCE_THRESHOLD = 0.4f

        internal fun resolveTargetLanguage(locale: Locale): String =
            TranslateLanguage.fromLanguageTag(locale.toLanguageTag())
                ?: TranslateLanguage.fromLanguageTag(locale.language)
                ?: TranslateLanguage.CHINESE
    }
}

private suspend fun <T> Task<T>.awaitMlKit(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) {
            continuation.resume(result)
        }
    }
    addOnFailureListener { error ->
        if (continuation.isActive) {
            continuation.resumeWithException(error)
        }
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}
