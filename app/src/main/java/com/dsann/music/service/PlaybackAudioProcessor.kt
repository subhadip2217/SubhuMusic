package com.dsann.music.service

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Read-only playback spectrum shared by the service and the Compose UI.
 * This uses PCM already decoded by Media3; it never opens the microphone.
 */
object PlaybackAudioVisualizer {
    const val BAND_COUNT = 24
    private val emptyLevels = List(BAND_COUNT) { 0f }
    private val _levels = MutableStateFlow(emptyLevels)
    val levels: StateFlow<List<Float>> = _levels.asStateFlow()

    @Volatile private var enabled = true
    @Volatile private var playing = false

    internal fun shouldAnalyse(): Boolean = enabled && playing

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) _levels.value = emptyLevels
    }

    fun setPlaying(value: Boolean) {
        playing = value
        if (!value || !enabled) _levels.value = emptyLevels
    }

    internal fun publish(values: FloatArray) {
        if (shouldAnalyse()) _levels.value = values.toList()
    }

    fun clear() {
        _levels.value = emptyLevels
    }
}

/**
 * A pass-through Media3 processor that calculates 24 logarithmic FFT bands.
 * Input bytes are copied unchanged to the output. Analysis failures are swallowed
 * so the visualizer can never interrupt audio playback.
 */
@UnstableApi
class PlaybackAudioProcessor : BaseAudioProcessor() {
    private companion object {
        const val FFT_SIZE = 512
        const val BAND_COUNT = PlaybackAudioVisualizer.BAND_COUNT
        const val MIN_ANALYSIS_HOP = FFT_SIZE / 3
        const val MIN_PUBLISH_INTERVAL_NS = 33_000_000L
    }

    private val sampleWindow = FloatArray(FFT_SIZE)
    private val real = FloatArray(FFT_SIZE)
    private val imaginary = FloatArray(FFT_SIZE)
    private val smoothed = FloatArray(BAND_COUNT)
    private var writeIndex = 0
    private var samplesWritten = 0
    private var samplesSinceAnalysis = 0
    private var lastPublishNs = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_FLOAT,
            C.ENCODING_PCM_8BIT -> inputAudioFormat
            else -> AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val byteCount = inputBuffer.remaining()
        if (PlaybackAudioVisualizer.shouldAnalyse()) {
            try {
                analyse(inputBuffer.duplicate().order(ByteOrder.nativeOrder()))
            } catch (_: Exception) {
                // Keep the audio path healthy if a device supplies an unexpected PCM buffer.
            }
        }

        val output = replaceOutputBuffer(byteCount)
        output.put(inputBuffer)
        output.flip()
    }

    private fun analyse(buffer: ByteBuffer) {
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val bytesPerSample = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_FLOAT -> 4
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_8BIT -> 1
            else -> return
        }
        val frameSize = channels * bytesPerSample

        while (buffer.remaining() >= frameSize) {
            var mono = 0f
            repeat(channels) {
                val sample = when (inputAudioFormat.encoding) {
                    C.ENCODING_PCM_FLOAT -> buffer.float
                    C.ENCODING_PCM_16BIT -> buffer.short.toFloat() / 32768f
                    C.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 0xFF) - 128) / 128f
                    else -> 0f
                }
                mono += if (sample.isFinite()) sample else 0f
            }

            sampleWindow[writeIndex] = (mono / channels).coerceIn(-1f, 1f)
            writeIndex = (writeIndex + 1) % FFT_SIZE
            samplesWritten = (samplesWritten + 1).coerceAtMost(FFT_SIZE)
            samplesSinceAnalysis++

            if (samplesWritten == FFT_SIZE && samplesSinceAnalysis >= MIN_ANALYSIS_HOP) {
                samplesSinceAnalysis = 0
                val now = System.nanoTime()
                if (now - lastPublishNs >= MIN_PUBLISH_INTERVAL_NS) {
                    lastPublishNs = now
                    publishSpectrum()
                }
            }
        }
    }

    private fun publishSpectrum() {
        // Read the ring buffer oldest-to-newest and apply a Hann window.
        for (i in 0 until FFT_SIZE) {
            val sample = sampleWindow[(writeIndex + i) % FFT_SIZE]
            val hann = 0.5f - 0.5f * cos((2.0 * PI * i / (FFT_SIZE - 1)).toFloat())
            real[i] = sample * hann
            imaginary[i] = 0f
        }

        fft()

        val result = FloatArray(BAND_COUNT)
        for (band in 0 until BAND_COUNT) {
            val lowBin = max(1, 2.0.pow(band.toDouble() / BAND_COUNT * 8.0).toInt())
            val highBin = max(
                lowBin + 1,
                2.0.pow((band + 1).toDouble() / BAND_COUNT * 8.0).toInt()
            ).coerceAtMost(FFT_SIZE / 2)

            var peak = 0f
            for (bin in lowBin until highBin) {
                val magnitude = sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin])
                peak = max(peak, magnitude * (2f / FFT_SIZE))
            }

            val boosted = (ln(1.0 + peak.toDouble() * 36.0) / ln(37.0))
                .toFloat()
                .coerceIn(0f, 1f)
            smoothed[band] = max(boosted, smoothed[band] * 0.72f + boosted * 0.28f)
            result[band] = smoothed[band]
        }
        PlaybackAudioVisualizer.publish(result)
    }

    private fun fft() {
        var j = 0
        for (i in 1 until FFT_SIZE) {
            var bit = FFT_SIZE shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val realValue = real[i]
                real[i] = real[j]
                real[j] = realValue

                val imaginaryValue = imaginary[i]
                imaginary[i] = imaginary[j]
                imaginary[j] = imaginaryValue
            }
        }

        var length = 2
        while (length <= FFT_SIZE) {
            val angle = -2.0 * PI / length
            val stepReal = cos(angle).toFloat()
            val stepImaginary = sin(angle).toFloat()
            val halfLength = length / 2

            var start = 0
            while (start < FFT_SIZE) {
                var twiddleReal = 1f
                var twiddleImaginary = 0f

                for (offset in 0 until halfLength) {
                    val even = start + offset
                    val odd = even + halfLength
                    val oddReal = real[odd] * twiddleReal - imaginary[odd] * twiddleImaginary
                    val oddImaginary = real[odd] * twiddleImaginary + imaginary[odd] * twiddleReal

                    val evenReal = real[even]
                    val evenImaginary = imaginary[even]
                    real[even] = evenReal + oddReal
                    imaginary[even] = evenImaginary + oddImaginary
                    real[odd] = evenReal - oddReal
                    imaginary[odd] = evenImaginary - oddImaginary

                    val nextTwiddleReal = twiddleReal * stepReal - twiddleImaginary * stepImaginary
                    twiddleImaginary = twiddleReal * stepImaginary + twiddleImaginary * stepReal
                    twiddleReal = nextTwiddleReal
                }
                start += length
            }
            length = length shl 1
        }
    }

    override fun onFlush() {
        writeIndex = 0
        samplesWritten = 0
        samplesSinceAnalysis = 0
        lastPublishNs = 0L
        sampleWindow.fill(0f)
        real.fill(0f)
        imaginary.fill(0f)
        smoothed.fill(0f)
        PlaybackAudioVisualizer.clear()
    }

    override fun onReset() {
        onFlush()
    }
}
