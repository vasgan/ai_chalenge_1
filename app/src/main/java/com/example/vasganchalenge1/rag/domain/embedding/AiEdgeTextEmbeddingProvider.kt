package com.example.vasganchalenge1.rag.domain.embedding

import android.content.Context
import android.util.Log
import com.example.vasganchalenge1.rag.model.EmbeddingProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiEdgeTextEmbeddingProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : EmbeddingProvider {

    private val tag = "AiEdgeEmbedding"
    private val initMutex = Mutex()

    @Volatile
    private var embedder: Any? = null

    @Volatile
    private var mediapipeAvailable = true

    private val mediapipeOptIn: Boolean by lazy { isMediapipeOptInEnabled() }

    @Volatile
    override var modelName: String = "FallbackHashEmbedding"
        private set

    override val providerType: EmbeddingProviderType = EmbeddingProviderType.LOCAL

    override suspend fun embed(texts: List<String>): List<FloatArray> = withContext(Dispatchers.Default) {
        if (texts.isEmpty()) return@withContext emptyList()

        val localEmbedder = ensureEmbedder()
        if (localEmbedder == null) {
            modelName = "FallbackHashEmbedding"
            return@withContext texts.map { fallbackEmbedding(it) }
        }

        val vectors = texts.map { text ->
            runCatching {
                val result = localEmbedder.javaClass.methods.firstOrNull {
                    it.name == "embed" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
                }?.invoke(localEmbedder, text)

                extractFloatEmbedding(result) ?: fallbackEmbedding(text)
            }.getOrElse {
                Log.w(tag, "MediaPipe embed failed, fallback used: ${it.message}")
                fallbackEmbedding(text)
            }
        }
        vectors
    }

    private suspend fun ensureEmbedder(): Any? {
        if (!mediapipeOptIn) {
            mediapipeAvailable = false
            modelName = "FallbackHashEmbedding"
            return null
        }
        if (!mediapipeAvailable) return null
        embedder?.let { return it }

        return initMutex.withLock {
            embedder?.let { return@withLock it }
            runCatching {
                val baseOptionsClass = Class.forName("com.google.mediapipe.tasks.core.BaseOptions")
                val textEmbedderClass = Class.forName("com.google.mediapipe.tasks.text.textembedder.TextEmbedder")
                val optionsClass = Class.forName("com.google.mediapipe.tasks.text.textembedder.TextEmbedder\$TextEmbedderOptions")

                val baseOptionsBuilder = baseOptionsClass.getMethod("builder").invoke(null)
                baseOptionsBuilder.javaClass.getMethod("setModelAssetPath", String::class.java)
                    .invoke(baseOptionsBuilder, MODEL_ASSET_PATH)
                val baseOptions = baseOptionsBuilder.javaClass.getMethod("build").invoke(baseOptionsBuilder)

                val optionsBuilder = optionsClass.getMethod("builder").invoke(null)
                optionsBuilder.javaClass.methods.firstOrNull {
                    it.name == "setBaseOptions" && it.parameterTypes.size == 1
                }?.invoke(optionsBuilder, baseOptions)
                    ?: error("setBaseOptions not found")

                val options = optionsBuilder.javaClass.getMethod("build").invoke(optionsBuilder)

                val created = textEmbedderClass.getMethod(
                    "createFromOptions",
                    Context::class.java,
                    optionsClass
                ).invoke(null, context, options)

                modelName = "MediaPipeTextEmbedder"
                created
            }.onFailure { throwable ->
                mediapipeAvailable = false
                Log.w(
                    tag,
                    "MediaPipe TextEmbedder init failed. Fallback embedding enabled: ${throwable.message}"
                )
            }.getOrNull().also { created ->
                embedder = created
            }
        }
    }

    private fun extractFloatEmbedding(result: Any?): FloatArray? {
        if (result == null) return null
        if (result is FloatArray) return result
        if (result is List<*>) {
            result.forEach { item ->
                val embedded = extractFloatEmbedding(item)
                if (embedded != null) return embedded
            }
            return null
        }

        val directMethodNames = listOf("floatEmbedding", "getFloatEmbedding")
        directMethodNames.forEach { name ->
            val direct = runCatching {
                result.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
                    ?.invoke(result)
            }.getOrNull()
            if (direct is FloatArray) return direct
        }

        val hopMethodNames = listOf(
            "embeddings",
            "getEmbeddings",
            "embeddingResult",
            "getEmbeddingResult",
            "results",
            "getResults"
        )
        hopMethodNames.forEach { name ->
            val nested = runCatching {
                result.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
                    ?.invoke(result)
            }.getOrNull()
            val embedded = extractFloatEmbedding(nested)
            if (embedded != null) return embedded
        }

        return null
    }

    private fun fallbackEmbedding(text: String, dims: Int = 256): FloatArray {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return FloatArray(dims)

        val vector = FloatArray(dims)
        normalized.forEachIndexed { index, char ->
            val bucket = (char.code + index * 31) % dims
            vector[bucket] += 1f
        }

        var norm = 0.0
        vector.forEach { value ->
            norm += (value * value).toDouble()
        }
        val scale = kotlin.math.sqrt(norm).toFloat().takeIf { it > 0f } ?: 1f
        return FloatArray(dims) { i -> vector[i] / scale }
    }

    private fun isMediapipeOptInEnabled(): Boolean {
        val hasModel = runCatching {
            context.assets.open(MODEL_ASSET_PATH).use { true }
        }.getOrDefault(false)
        if (!hasModel) {
            Log.i(tag, "MediaPipe model asset not found at $MODEL_ASSET_PATH. Using fallback embedding.")
            return false
        }

        val optIn = runCatching {
            context.assets.open(ENABLE_MEDIAPIPE_FLAG_ASSET).use { stream ->
                stream.bufferedReader().use { reader ->
                    reader.readText().trim().equals("true", ignoreCase = true)
                }
            }
        }.getOrDefault(false)

        if (!optIn) {
            Log.i(
                tag,
                "MediaPipe disabled by default. To enable, add asset $ENABLE_MEDIAPIPE_FLAG_ASSET with text 'true'."
            )
        }
        return optIn
    }

    companion object {
        private const val MODEL_ASSET_PATH = "models/universal_sentence_encoder.tflite"
        private const val ENABLE_MEDIAPIPE_FLAG_ASSET = "models/enable_mediapipe.flag"
    }
}
