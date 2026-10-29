package io.github.soclear.songcue.hook

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

/** 所有入口都在主线程调用；引擎回调也回到主线程，取消后不执行旧的完成任务。 */
internal class SongAnnouncer(
    private val context: Context,
    private val log: (String, Throwable) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var paused = false
    private var text = ""
    private var serial = 0
    private var pending: (() -> Unit)? = null
    private val timeout = Runnable { finish() }

    fun announce(text: String, done: () -> Unit) {
        cancel()
        this.text = text
        pending = done
        if (tts == null) init() else speak()
    }

    fun cancel(paused: Boolean = false) {
        serial++
        main.removeCallbacks(timeout)
        tts?.stop()
        this.paused = paused
        if (!paused) pending = null
    }

    fun resumeSpeech(): Boolean {
        if (pending == null) return false
        if (paused) {
            paused = false
            if (tts == null) init() else speak()
        }
        return true
    }

    private fun init() {
        // 初始化和播报都有限时兜底，不能一直阻塞音乐。
        main.postDelayed(timeout, 10_000)
        try {
            // 使用系统默认 TTS 引擎及其默认语音，不覆盖用户的语言设置。
            tts = TextToSpeech(context) { status ->
                main.post {
                    if (closed) return@post
                    ready = status == TextToSpeech.SUCCESS
                    if (!ready) {
                        tts?.shutdown()
                        tts = null
                        finish()
                        return@post
                    }
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) = done(utteranceId)
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) = done(utteranceId)
                        private fun done(id: String?) {
                            main.post { if (id == serial.toString()) finish() }
                        }
                    })
                    speak()
                }
            }
        } catch (e: Exception) {
            log("Cannot initialize TTS", e)
            finish()
        }
    }

    private fun speak() {
        if (paused || pending == null) return
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, 10_000)
        if (!ready) return
        try {
            if (tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, serial.toString()) ==
                TextToSpeech.ERROR
            ) finish()
        } catch (e: Exception) {
            log("Cannot announce song", e)
            finish()
        }
    }

    private fun finish() {
        if (paused) return
        main.removeCallbacks(timeout)
        tts?.stop()
        val done = pending
        pending = null
        done?.invoke()
    }

    fun close() {
        closed = true
        cancel()
        tts?.shutdown()
        tts = null
    }
}
