// PATH: app/src/main/java/com/jarvis/app/VoiceGate.kt
package com.jarvis.app

import android.content.Context
import android.util.Base64
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Modelsiz ovoz izi (MFCC). ECAPA'dan ancha zaif: yaqin qarindoshlar o'tib ketishi,
 * shovqinda esa egasi rad etilishi mumkin. Chegara (threshold) ni sinab sozlang:
 * kichik = qattiqroq, katta = yumshoqroq.
 */
class VoiceGate(@Suppress("UNUSED_PARAMETER") ctx: Context, private val threshold: Float = 2.5f) {

    fun isOwner(pcm: ShortArray, enrolled: FloatArray): Boolean {
        val f = Mfcc.features(pcm) ?: return false
        if (enrolled.size != 2 * L) return false
        var s = 0f
        for (i in 0 until L) {
            val z = (f[i] - enrolled[i]) / max(enrolled[L + i], 1f)
            s += z * z
        }
        return sqrt(s / L) < threshold
    }

    fun enroll(clips: List<ShortArray>): FloatArray {
        val feats = clips.mapNotNull { Mfcc.features(it) }
        if (feats.size < 3) throw IllegalStateException("Ovoz yetarli emas, balandroq gapirib qayta urining")
        val mu = FloatArray(L) { i -> feats.sumOf { it[i].toDouble() }.toFloat() / feats.size }
        val sd = FloatArray(L) { i ->
            sqrt(feats.sumOf { (it[i] - mu[i]).toDouble().pow(2) } / feats.size).toFloat()
        }
        return mu + sd
    }

    companion object {
        const val L = 24
        fun encode(f: FloatArray): String =
            Base64.encodeToString(ByteBuffer.allocate(f.size * 4).also { it.asFloatBuffer().put(f) }.array(), Base64.NO_WRAP)
        fun decode(s: String): FloatArray {
            val fb = ByteBuffer.wrap(Base64.decode(s, Base64.NO_WRAP)).asFloatBuffer()
            return FloatArray(fb.remaining()).also { fb.get(it) }
        }
    }
}

private object Mfcc {
    private const val SR = 16000
    private const val FRAME = 400
    private const val HOP = 160
    private const val NFFT = 512
    private const val NMEL = 26
    private const val NC = 13

    private val window = FloatArray(FRAME) { (0.54 - 0.46 * cos(2 * PI * it / (FRAME - 1))).toFloat() }
    private val dct = Array(NC) { k -> FloatArray(NMEL) { n -> cos(PI * k * (n + 0.5) / NMEL).toFloat() } }
    private val fb: Array<FloatArray> = run {
        fun mel(f: Double) = 2595 * log10(1 + f / 700)
        fun inv(m: Double) = 700 * (10.0.pow(m / 2595) - 1)
        val lo = mel(100.0); val hi = mel(7000.0)
        val bins = IntArray(NMEL + 2) { ((NFFT + 1) * inv(lo + (hi - lo) * it / (NMEL + 1)) / SR).toInt() }
        Array(NMEL) { m ->
            FloatArray(NFFT / 2 + 1).also { row ->
                for (k in bins[m] until bins[m + 1]) row[k] = (k - bins[m]).toFloat() / max(1, bins[m + 1] - bins[m])
                for (k in bins[m + 1] until bins[m + 2]) row[k] = (bins[m + 2] - k).toFloat() / max(1, bins[m + 2] - bins[m + 1])
            }
        }
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val a = re[i]; re[i] = re[j]; re[j] = a; val b = im[i]; im[i] = im[j]; im[j] = b }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** 24 ta son: c1..c12 o'rtachasi va c1..c12 og'ishi (faqat ovozli kadrlar). */
    fun features(pcm: ShortArray): FloatArray? {
        if (pcm.size < FRAME * 20) return null
        val frames = ArrayList<FloatArray>()
        val energies = ArrayList<Float>()
        var s = 0
        while (s + FRAME <= pcm.size) {
            val re = FloatArray(NFFT); val im = FloatArray(NFFT)
            var e = 0.0
            for (i in 0 until FRAME) {
                val x = pcm[s + i].toFloat() - 0.97f * (if (i > 0) pcm[s + i - 1].toFloat() else 0f)
                re[i] = x * window[i]; e += x * x
            }
            fft(re, im)
            val pw = FloatArray(NFFT / 2 + 1) { re[it] * re[it] + im[it] * im[it] }
            val logmel = FloatArray(NMEL) { m ->
                var a = 0f
                for (k in pw.indices) a += fb[m][k] * pw[k]
                ln(max(a, 1e-10f))
            }
            frames.add(FloatArray(NC) { c -> var a = 0f; for (m in 0 until NMEL) a += dct[c][m] * logmel[m]; a })
            energies.add(ln(e.toFloat() + 1f))
            s += HOP
        }
        val median = energies.sorted()[energies.size / 2]
        val voiced = frames.filterIndexed { i, _ -> energies[i] >= median }
        if (voiced.size < 10) return null
        val out = FloatArray(24)
        for (c in 1..12) {
            val mean = voiced.sumOf { it[c].toDouble() }.toFloat() / voiced.size
            val sd = sqrt(voiced.sumOf { (it[c] - mean).toDouble().pow(2) } / voiced.size).toFloat()
            out[c - 1] = mean; out[12 + c - 1] = sd
        }
        return out
    }
}
