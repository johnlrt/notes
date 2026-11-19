package com.johnlrt.freenotes.engine

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.johnlrt.freenotes.data.Stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * On-device Handwriting-to-Text recognizer powered by Google ML Kit Digital Ink Recognition
 * (the same underlying engine as `libdigitalink.so` observed in Freenotes 3.29.0).
 */
object HandwritingRecognizer {

    suspend fun recognize(
        strokes: List<Stroke>,
        languageTag: String = "en-US"
    ): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            if (strokes.isEmpty()) error("No strokes selected")

            val modelId = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
                ?: DigitalInkRecognitionModelIdentifier.EN_US
            val model = DigitalInkRecognitionModel.builder(modelId).build()
            val modelManager = RemoteModelManager.getInstance()

            val downloaded = modelManager.isModelDownloaded(model).await()
            if (!downloaded) {
                modelManager.download(model, DownloadConditions.Builder().build()).await()
            }

            val inkBuilder = Ink.builder()
            var baseTime = 0L
            for (stroke in strokes) {
                if (stroke.points.isEmpty()) continue
                val strokeBuilder = Ink.Stroke.builder()
                for (pt in stroke.points) {
                    strokeBuilder.addPoint(Ink.Point.create(pt.x, pt.y, baseTime + pt.timeOffsetMs))
                }
                baseTime += (stroke.points.lastOrNull()?.timeOffsetMs ?: 0L) + 200L
                inkBuilder.addStroke(strokeBuilder.build())
            }

            val recognizer = DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(model).build()
            )
            try {
                val result = recognizer.recognize(inkBuilder.build()).await()
                val text = result.candidates.firstOrNull()?.text?.trim().orEmpty()
                if (text.isEmpty()) error("Could not recognize handwriting")
                text
            } finally {
                recognizer.close()
            }
        }
    }
}
