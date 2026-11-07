// PATH: app/src/main/java/com/jarvis/app/Audio.kt
package com.jarvis.app

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.sqrt

object Recorder {
    const val RATE = 16000

    /** fixed=true: aynan maxMs davomida yozadi (ovoz izini yozdirish uchun).
     *  fixed=false: gap tugagach (0.9s jimlik) to'xtaydi. threshold: mikrofoningizga qarab sozlang. */
    @SuppressLint("MissingPermission")
    suspend fun record(maxMs: Int = 8000, fixed: Boolean = false, threshold: Int = 1500): ShortArray =
        withContext(Dispatchers.IO) {
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 8000))
            val chunks = ArrayList<ShortArray>()
            val buf = ShortArray(1600)
            var elapsed = 0; var silent = 0; var spoke = false
            rec.startRecording()
            try {
                while (elapsed < maxMs) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) break
                    chunks.add(buf.copyOf(n))
                    val ms = n * 1000 / RATE
                    elapsed += ms
                    if (!fixed) {
                        var s = 0.0
                        for (i in 0 until n) s += buf[i].toDouble() * buf[i]
                        if (sqrt(s / n) > threshold) { spoke = true; silent = 0 } else silent += ms
                        if (spoke && silent >= 900) break
                        if (!spoke && elapsed >= 4000) break
                    }
                }
            } finally { rec.stop(); rec.release() }
            val out = ShortArray(chunks.sumOf { it.size })
            var p = 0
            for (c in chunks) { c.copyInto(out, p); p += c.size }
            out
        }
}

/** Vosk offline STT. assets/model-uz/ papkasiga vosk-model-small-uz joylang. */
class Stt(ctx: Context) {
    @Volatile private var model: Model? = null
    init {
        StorageService.unpack(ctx, "model-uz", "model",
            { m -> model = m }, { e -> android.util.Log.e("Jarvis", "vosk", e) })
    }
    fun transcribe(pcm: ShortArray): String {
        val m = model ?: return ""
        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            .also { it.asShortBuffer().put(pcm) }.array()
        Recognizer(m, Recorder.RATE.toFloat()).use { r ->
            r.acceptWaveForm(bytes, bytes.size)
            return JSONObject(r.finalResult).optString("text")
        }
    }
}

class Tts(ctx: Context) {
    private val ready = CompletableDeferred<Boolean>()
    private var tts: TextToSpeech? = null
    init {
        tts = TextToSpeech(ctx) { st ->
            if (st == TextToSpeech.SUCCESS) {
                val r = tts!!.setLanguage(Locale("uz", "UZ"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
                    tts!!.setLanguage(Locale("ru", "RU"))
                ready.complete(true)
            } else ready.complete(false)
        }
    }
    suspend fun say(text: String) {
        if (!ready.await() || text.isBlank()) return
        suspendCancellableCoroutine<Unit> { cont ->
            val id = UUID.randomUUID().toString()
            tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(u: String?) {}
                override fun onDone(u: String?) { if (u == id && cont.isActive) cont.resume(Unit) }
                @Deprecated("deprecated") override fun onError(u: String?) { if (u == id && cont.isActive) cont.resume(Unit) }
            })
            tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }
    }
    fun shutdown() { tts?.shutdown() }
}
