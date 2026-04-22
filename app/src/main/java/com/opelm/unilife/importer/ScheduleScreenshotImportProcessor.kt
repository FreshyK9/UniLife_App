package com.opelm.unilife.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class ScheduleScreenshotImportProcessor(
    private val context: Context,
    private val parser: ScheduleScreenshotParser
) {
    suspend fun process(imageUri: Uri): ScheduleImportParseOutput {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val baseImage = InputImage.fromFilePath(context, imageUri)
            val originalResult = recognizer.process(baseImage).awaitResult()

            val documents = mutableListOf(originalResult.toDocument())
            loadBitmap(imageUri)?.let { bitmap ->
                val enhancedBitmap = bitmap.toLowContrastFriendlyBitmap()
                if (enhancedBitmap != bitmap) {
                    val enhancedImage = InputImage.fromBitmap(enhancedBitmap, 0)
                    val enhancedResult = recognizer.process(enhancedImage).awaitResult()
                    documents += enhancedResult.toDocument()
                    enhancedBitmap.recycle()
                }
                bitmap.recycle()
            }

            val document = mergeDocuments(documents)
            ScheduleImportParseOutput(
                document = document,
                drafts = parser.parse(document)
            )
        } finally {
            recognizer.close()
        }
    }

    private fun loadBitmap(imageUri: Uri): Bitmap? =
        context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }

    private fun mergeDocuments(documents: List<OcrDocument>): OcrDocument {
        val mergedLines = mutableListOf<OcrLine>()

        documents
            .flatMap { document -> document.blocks.flatMap { it.lines } }
            .sortedWith(
                compareBy<OcrLine>(
                    { it.boundingBox?.top ?: Int.MAX_VALUE },
                    { it.boundingBox?.left ?: Int.MAX_VALUE }
                ).thenByDescending { it.text.length }
            )
            .forEach { candidate ->
                val existingIndex = mergedLines.indexOfFirst { existing ->
                    sameDetectedLine(existing, candidate)
                }

                if (existingIndex == -1) {
                    mergedLines += candidate
                } else if (candidate.text.length > mergedLines[existingIndex].text.length) {
                    mergedLines[existingIndex] = candidate
                }
            }

        val sortedLines = mergedLines.sortedWith(
            compareBy<OcrLine>(
                { it.boundingBox?.top ?: Int.MAX_VALUE },
                { it.boundingBox?.left ?: Int.MAX_VALUE }
            )
        )

        return OcrDocument(
            text = sortedLines.joinToString("\n") { it.text },
            blocks = sortedLines.map { line ->
                OcrBlock(
                    text = line.text,
                    boundingBox = line.boundingBox,
                    lines = listOf(line)
                )
            }
        )
    }

    private fun sameDetectedLine(first: OcrLine, second: OcrLine): Boolean {
        val firstBounds = first.boundingBox
        val secondBounds = second.boundingBox
        if (firstBounds == null || secondBounds == null) {
            return normalizeText(first.text) == normalizeText(second.text)
        }

        val topDistance = abs(firstBounds.top - secondBounds.top)
        val leftDistance = abs(firstBounds.left - secondBounds.left)
        val heightTolerance = maxOf(firstBounds.height(), secondBounds.height()) + 8
        val widthTolerance = (maxOf(firstBounds.width(), secondBounds.width()) * 0.15f).roundToInt() + 12

        if (topDistance > heightTolerance || leftDistance > widthTolerance) {
            return false
        }

        val firstText = normalizeText(first.text)
        val secondText = normalizeText(second.text)
        return firstText == secondText ||
            firstText.contains(secondText) ||
            secondText.contains(firstText)
    }

    private fun normalizeText(text: String): String =
        text.lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
}

private fun Text.toDocument(): OcrDocument =
    OcrDocument(
        text = text,
        blocks = textBlocks.map { block ->
            OcrBlock(
                text = block.text,
                boundingBox = block.boundingBox,
                lines = block.lines.map { line ->
                    OcrLine(
                        text = line.text,
                        boundingBox = line.boundingBox,
                        words = line.elements.map { element ->
                            OcrWord(
                                text = element.text,
                                boundingBox = element.boundingBox
                            )
                        }
                    )
                }
            )
        }
    )

private fun Bitmap.toLowContrastFriendlyBitmap(): Bitmap {
    val scaledWidth = (width * 2).coerceAtMost(4096)
    val scaledHeight = (height * 2).coerceAtMost(4096)
    val scaledBitmap = if (scaledWidth != width || scaledHeight != height) {
        Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
    } else {
        copy(Bitmap.Config.ARGB_8888, true)
    }

    val result = if (scaledBitmap.isMutable) {
        scaledBitmap
    } else {
        scaledBitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    if (result != scaledBitmap) {
        scaledBitmap.recycle()
    }

    val pixels = IntArray(result.width * result.height)
    result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)

    for (index in pixels.indices) {
        val color = pixels[index]
        val alpha = Color.alpha(color)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        val luminance = (0.299f * red) + (0.587f * green) + (0.114f * blue)
        val normalized = (luminance / 255f).coerceIn(0f, 1f)

        // Gray UI text often sits too close to white for OCR. This gamma-based
        // remap darkens near-white text while keeping the background mostly white.
        val adjusted = when {
            normalized >= 0.985f -> 255
            else -> (255f * normalized.pow(1.85f)).roundToInt().coerceIn(0, 255)
        }

        val finalGray = if (adjusted >= 242) 255 else adjusted
        pixels[index] = Color.argb(alpha, finalGray, finalGray, finalGray)
    }

    result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
    return result
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }
