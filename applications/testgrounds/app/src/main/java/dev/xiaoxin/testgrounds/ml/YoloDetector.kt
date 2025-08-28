package dev.xiaoxin.testgrounds.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Locale

data class Detection(
    val boundingBox: RectF,
    val confidence: Float,
    val classId: Int,
    val className: String
)

@Singleton
class YoloDetector @Inject constructor(
    private val context: Context
) {
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val labels = mutableListOf<String>()

    private val inputSize = 640
    private val numClasses = 80
    private val enableDebug = true

    @Volatile private var confidenceThreshold = 0.25f
    @Volatile private var iouThreshold = 0.45f
    @Volatile private var censoredClasses: Set<String> = setOf("person","car","bicycle","motorcycle","airplane","bus","train","truck")

    data class PreprocessMeta(val scale: Float, val padX: Float, val padY: Float, val newW: Int, val newH: Int)

    suspend fun initialize(modelPath: String = "yolo11n_float32.tflite") {
        withContext(Dispatchers.IO) {
            try {
                loadLabels()
                val modelBuffer = loadModelFile(modelPath)

                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                    setUseXNNPACK(true)
                }

                val compatibilityList = CompatibilityList()
                if (compatibilityList.isDelegateSupportedOnThisDevice) {
                    gpuDelegate = GpuDelegate(compatibilityList.bestOptionsForThisDevice)
                    options.addDelegate(gpuDelegate)
                }

                interpreter = Interpreter(modelBuffer, options)
                if (enableDebug) Log.d("YoloDetector", "Interpreter initialized.")
            } catch (e: Exception) {
                throw RuntimeException("Failed to initialize YOLO detector: ${e.message}", e)
            }
        }
    }

    private fun loadLabels() {
        labels.clear()
        context.assets.open("coco_labels.txt").bufferedReader().useLines { lines ->
            lines.forEach { labels.add(it) }
        }
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val afd = context.assets.openFd(modelPath)
        return afd.createInputStream().channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
    }

    suspend fun detectObjects(bitmap: Bitmap): List<Detection> = withContext(Dispatchers.Default) {
        val interpreter = this@YoloDetector.interpreter ?: throw IllegalStateException("YOLO not initialized.")

        // Letterbox input
        val (inputBitmap, meta) = letterbox(bitmap, inputSize, inputSize)

        // Preprocess: RGB + 0-1 normalization
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(inputBitmap)
        val processor = ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 1f))
            .build()
        val inputBuffer = processor.process(tensorImage).buffer

        // Output: channels-first [1, 84, 8400]
        val outputTensor = interpreter.getOutputTensor(0)
        val shape = outputTensor.shape()
        if (enableDebug) Log.d("YoloDetector", "Output shape: ${shape.joinToString()} uint8=${outputTensor.dataType()==DataType.UINT8} meta=$meta")

        val numBoxes = shape[2] // 8400
        val numFeatures = shape[1] // 84
        val outputBuffer = Array(1) { Array(numFeatures) { FloatArray(numBoxes) } }

        interpreter.run(inputBuffer, outputBuffer)

        val detections = mutableListOf<Detection>()
        for (i in 0 until numBoxes) {
            val cx = outputBuffer[0][0][i]
            val cy = outputBuffer[0][1][i]
            val w = outputBuffer[0][2][i]
            val h = outputBuffer[0][3][i]
            val objectness = outputBuffer[0][4][i]
            val classScores = outputBuffer[0].sliceArray(5 until 85).map { it[i] * objectness }

            val maxScore = classScores.maxOrNull() ?: 0f
            val classId = classScores.indexOfFirst { it == maxScore }

            if (maxScore < confidenceThreshold) continue

            // Reverse letterbox to original image coordinates
            val x = (cx - meta.padX) / meta.scale
            val y = (cy - meta.padY) / meta.scale
            val bw = w / meta.scale
            val bh = h / meta.scale

            val left = (x - bw/2f).coerceIn(0f, bitmap.width.toFloat())
            val top = (y - bh/2f).coerceIn(0f, bitmap.height.toFloat())
            val right = (x + bw/2f).coerceIn(0f, bitmap.width.toFloat())
            val bottom = (y + bh/2f).coerceIn(0f, bitmap.height.toFloat())

            detections.add(Detection(RectF(left, top, right, bottom), maxScore, classId, labels.getOrNull(classId) ?: "N/A"))
        }

        val filtered = applyNMS(detections)
        if (enableDebug) {
            val top = filtered.sortedByDescending { it.confidence }.take(5)
            Log.d("YoloDetector", "Top detections: ${top.joinToString { "${it.className}=${String.format(Locale.US,"%.2f",it.confidence)}" }}")
            Log.d("YoloDetector", "Detections after NMS: ${filtered.size}")
        }

        filtered
    }

    private fun letterbox(src: Bitmap, targetW: Int, targetH: Int, color: Int = Color.BLACK): Pair<Bitmap, PreprocessMeta> {
        val w = src.width
        val h = src.height
        val scale = minOf(targetW.toFloat()/w, targetH.toFloat()/h)
        val newW = (w*scale).toInt()
        val newH = (h*scale).toInt()
        val resized = src.scale(newW,newH)
        val out = createBitmap(targetW, targetH)
        val canvas = Canvas(out)
        canvas.drawColor(color)
        val padX = (targetW - newW)/2f
        val padY = (targetH - newH)/2f
        canvas.drawBitmap(resized, padX, padY, null)
        return out to PreprocessMeta(scale, padX, padY, newW, newH)
    }

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        val picked = mutableListOf<Detection>()
        val sorted = detections.sortedByDescending { it.confidence }
        for (candidate in sorted) {
            var keep = true
            for (selected in picked) {
                if (selected.classId == candidate.classId) {
                    val iou = calculateIoU(selected.boundingBox, candidate.boundingBox)
                    if (iou > iouThreshold) { keep = false; break }
                }
            }
            if (keep) picked.add(candidate)
        }
        return picked
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val interLeft = maxOf(box1.left, box2.left)
        val interTop = maxOf(box1.top, box2.top)
        val interRight = minOf(box1.right, box2.right)
        val interBottom = minOf(box1.bottom, box2.bottom)
        if (interRight <= interLeft || interBottom <= interTop) return 0f
        val interArea = (interRight-interLeft)*(interBottom-interTop)
        val union = (box1.right-box1.left)*(box1.bottom-box1.top) +
                (box2.right-box2.left)*(box2.bottom-box2.top) -
                interArea
        return interArea/union
    }

    fun shouldCensorImage(detections: List<Detection>): Boolean =
        detections.any { it.confidence >= confidenceThreshold && censoredClasses.contains(it.className.lowercase()) }

    fun updateConfig(confidence: Float, iou: Float, censored: Set<String>) {
        confidenceThreshold = confidence
        iouThreshold = iou
        censoredClasses = censored.map { it.lowercase() }.toSet()
    }

    fun release() {
        interpreter?.close()
        gpuDelegate?.close()
        interpreter = null
        gpuDelegate = null
    }
}
