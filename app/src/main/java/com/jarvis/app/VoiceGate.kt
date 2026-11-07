// PATH: app/src/main/java/com/jarvis/app/VoiceGate.kt
package com.jarvis.app

import android.content.Context
import android.util.Base64
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Ovoz izi tekshiruvi. assets/ecapa.tflite kerak: kirish [1, 48000] float32 (16 kHz, 3 s xom to'lqin),
 * chiqish [1, D] embedding. Modelingiz boshqa kirish formatida bo'lsa, embed() ni moslang.
 */
class VoiceGate(ctx: Context, private val threshold: Float = 0.72f) {
    private val interp: Interpreter

    init {
        val fd = ctx.assets.openFd("ecapa.tflite")
        val map = FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        interp = Interpreter(map)
    }

    fun embed(pcm: ShortArray): FloatArray {
        val input = FloatArray(N)
        for (i in 0 until minOf(N, pcm.size)) input[i] = pcm[i] / 32768f
        val dim = interp.getOutputTensor(0).shape().last()
        val out = Array(1) { FloatArray(dim) }
        interp.run(arrayOf(input), out)
        return out[0]
    }

    fun isOwner(pcm: ShortArray, enrolled: FloatArray): Boolean = cosine(embed(pcm), enrolled) > threshold

    fun enroll(clips: List<ShortArray>): FloatArray {
        val embs = clips.map { embed(it) }
        val avg = FloatArray(embs[0].size)
        for (e in embs) for (i in avg.indices) avg[i] += e[i] / embs.size
        val norm = sqrt(avg.fold(0f) { a, x -> a + x * x })
        return FloatArray(avg.size) { avg[it] / norm }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var d = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { d += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return d / (sqrt(na) * sqrt(nb))
    }

    companion object {
        const val N = 48000
        fun encode(f: FloatArray): String =
            Base64.encodeToString(ByteBuffer.allocate(f.size * 4).also { it.asFloatBuffer().put(f) }.array(), Base64.NO_WRAP)
        fun decode(s: String): FloatArray {
            val fb = ByteBuffer.wrap(Base64.decode(s, Base64.NO_WRAP)).asFloatBuffer()
            return FloatArray(fb.remaining()).also { fb.get(it) }
        }
    }
}
