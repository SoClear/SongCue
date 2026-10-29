package io.github.soclear.songcue.hook

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.Collections
import java.util.IdentityHashMap

private const val CONTROL = "io.github.soclear.songcue.CONTROL"

/**
 * 所有作用域共用系统媒体会话；确认暂停后播报，不依赖客户端混淆名。
 * 会话更新晚于音频起播，不能保证首帧前拦截或单曲循环。
 */
object MediaSessionMusic {
    private var installed = false

    context(xposedModule: XposedModule, context: Context)
    fun install() {
        if (installed) return
        val main = Handler(Looper.getMainLooper())
        val preferences = xposedModule.getRemotePreferences("settings")
        val sessions = IdentityHashMap<MediaSession, SessionPlayer>()
        val callbacks = Collections.synchronizedMap(IdentityHashMap<MediaSession.Callback, SessionPlayer>())
        val hooked = mutableSetOf<Method>()
        val internalControl = ThreadLocal.withInitial { false }
        val log: (String, Throwable) -> Unit = { message, error ->
            xposedModule.log(Log.ERROR, "SongCue", message, error)
        }

        fun hookCallback(callback: MediaSession.Callback) {
            val commands = setOf(
                "onPlay", "onPause", "onStop", "onSkipToNext", "onSkipToPrevious",
                "onSkipToQueueItem", "onPlayFromMediaId", "onPlayFromSearch",
                "onPlayFromUri", "onSeekTo", "onCommand", "onMediaButtonEvent",
            )
            callback.javaClass.methods.filter { it.name in commands }.forEach { target ->
                synchronized(hooked) {
                    if (!hooked.add(target)) return@forEach
                    xposedModule.hook(target).intercept { chain ->
                        val player = callbacks[chain.thisObject]
                        if (player != null && target.name == "onCommand" && chain.args[0] == CONTROL) {
                            player.control(chain.args[1] as? Bundle)
                            return@intercept null
                        }
                        if (target.name == "onCommand") return@intercept chain.proceed()
                        if (target.name == "onMediaButtonEvent") {
                            val event = (chain.args[0] as Intent).getParcelableExtra(
                                Intent.EXTRA_KEY_EVENT, KeyEvent::class.java,
                            )
                            if (player?.cue?.pending != null && event?.keyCode in setOf(
                                    KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
                                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                                )
                            ) {
                                if (event?.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                                    main.post { player.mediaButton(event.keyCode) }
                                return@intercept true
                            }
                            return@intercept chain.proceed()
                        }
                        if (player != null && internalControl.get() != true) {
                            if (target.name == "onPlay" && player.cue.pending != null) {
                                main.post { player.resumeSpeech() }
                                return@intercept null
                            }
                            if (target.name != "onPlay") main.post {
                                player.cancel(
                                    paused = target.name == "onPause",
                                    reset = target.name != "onPause" && target.name != "onSeekTo",
                                )
                            }
                        }
                        chain.proceed()
                    }
                }
            }
        }

        val setCallback = MediaSession::class.java.getDeclaredMethod(
            "setCallback", MediaSession.Callback::class.java, Handler::class.java,
        )
        xposedModule.hook(setCallback).intercept { chain ->
            val result = chain.proceed()
            val session = chain.thisObject as MediaSession
            val callback = chain.args[0] as? MediaSession.Callback
            if (callback != null) {
                try {
                    hookCallback(callback)
                } catch (e: Exception) {
                    log("Cannot hook MediaSession callback", e)
                    return@intercept result
                }
            }
            main.post {
                if (callback == null) {
                    sessions.remove(session)?.let {
                        callbacks.remove(it.callback)
                        it.close()
                    }
                } else {
                    val player = sessions.getOrPut(session) {
                        SessionPlayer(
                            session.controller, callback, main, preferences, internalControl,
                            context.applicationContext ?: context, log,
                        ).also { it.start() }
                    }
                    callbacks.remove(player.callback)
                    player.callback = callback
                    callbacks[callback] = player
                }
            }
            result
        }
        xposedModule.hook(MediaSession::class.java.getDeclaredMethod("release")).intercept { chain ->
            val session = chain.thisObject as MediaSession
            main.post {
                sessions.remove(session)?.let { player ->
                    callbacks.remove(player.callback)
                    player.close()
                }
            }
            chain.proceed()
        }
        installed = true
        xposedModule.log(Log.INFO, "SongCue", "MediaSession hooks ready (${context.packageName})")
    }
}

private class SessionPlayer(
    private val controller: MediaController,
    @Volatile var callback: MediaSession.Callback,
    private val main: Handler,
    private val preferences: SharedPreferences,
    private val internalControl: ThreadLocal<Boolean>,
    context: Context,
    private val log: (String, Throwable) -> Unit,
) : MediaController.Callback() {
    val cue = SessionCue()
    private val announcer = SongAnnouncer(context, log)
    @Volatile private var serial = 0
    private var closed = false
    private var pausing = false
    private val update = Runnable { update() }
    private val timeout = Runnable {
        pausing = false
        if (controller.playbackState?.state == PlaybackState.STATE_PAUSED) speak()
        else cancel()
    }

    fun start() {
        Log.d("SongCue", "${controller.packageName}: session attached")
        controller.registerCallback(this, main)
        scheduleUpdate()
    }

    // 合并同一轮歌曲信息和状态更新，避免拿上一首的元数据播报。
    private fun scheduleUpdate() {
        main.removeCallbacks(update)
        main.postDelayed(update, 80)
    }

    override fun onMetadataChanged(metadata: MediaMetadata?) = scheduleUpdate()
    override fun onPlaybackStateChanged(state: PlaybackState?) = scheduleUpdate()
    override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) = scheduleUpdate()
    override fun onSessionDestroyed() = close()

    private fun song(): SessionSong? {
        val metadata = controller.metadata ?: return null
        val id = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        if (metadata.getLong("android.media.metadata.ADVERTISEMENT") != 0L ||
            id?.startsWith("spotify:ad:") == true
        ) return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: return null
        val singer = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        return SessionSong(id, title, singer).normalized(controller.packageName)
    }

    private fun update() {
        if (closed) return
        val state = controller.playbackState ?: return
        if (state.state in setOf(
                PlaybackState.STATE_NONE, PlaybackState.STATE_STOPPED, PlaybackState.STATE_ERROR,
            )
        ) {
            cancel(reset = true)
            return
        }
        if (controller.playbackInfo.playbackType != MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL) {
            cancel()
            return
        }
        val song = song()
        if (song == null) {
            // 元数据被清空或切到广告后，不能保留已经失效的恢复任务。
            val resume = cue.pending != null && cue.phase != SessionCue.Phase.PAUSED
            cancel()
            if (resume) control(play = true)
            return
        }
        val playing = state.state == PlaybackState.STATE_PLAYING
        if (cue.phase == SessionCue.Phase.PAUSED && cue.pending?.key != song.key) cancel()
        if (cue.select(song, playing)) {
            serial++
            pausing = false
            main.removeCallbacks(timeout)
            announcer.cancel(paused = false)
        }
        if (cue.pending == null || cue.phase == SessionCue.Phase.PAUSED) return
        if (playing && !pausing) {
            pausing = true
            Log.d("SongCue", "${controller.packageName}: pause requested")
            control(play = false)
            main.removeCallbacks(timeout)
            main.postDelayed(timeout, 1_500)
        } else if (state.state == PlaybackState.STATE_PAUSED) {
            pausing = false
            main.removeCallbacks(timeout)
            speak()
        }
    }

    private fun speak() {
        val track = cue.speak() ?: return
        Log.d("SongCue", "${controller.packageName}: announcing")
        announcer.announce(track.text(preferences.getInt("singer_position", 0))) {
            // 切歌、用户暂停和释放会话后，旧的完成回调不能重新拉起播放器。
            if (!closed && cue.pending === track && cue.phase == SessionCue.Phase.SPEAKING &&
                song()?.key == track.key
            ) {
                cue.cancel()
                Log.d("SongCue", "${controller.packageName}: resuming")
                control(play = true)
            }
        }
    }

    fun mediaButton(keyCode: Int) {
        // 音乐已被模块暂停时，系统会忽略 PAUSE 键，必须按播报的逻辑状态处理。
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && cue.phase == SessionCue.Phase.PAUSED
        ) resumeSpeech() else {
            cancel(paused = true)
            control(play = false)
        }
    }

    fun resumeSpeech() {
        if (closed) return
        if (cue.pending?.key != song()?.key) {
            cancel()
            control(play = true)
            return
        }
        cue.resume()
        if (announcer.resumeSpeech()) cue.speak() else update()
    }

    fun cancel(paused: Boolean = false, reset: Boolean = false) {
        serial++
        pausing = false
        main.removeCallbacks(timeout)
        if (paused) cue.pause() else cue.cancel(reset)
        announcer.cancel(paused)
    }

    private fun control(play: Boolean) {
        // 经系统分发，既保留回调线程，也保留 Spotify 等客户端需要的控制器身份。
        controller.sendCommand(CONTROL, Bundle().apply {
            putInt("serial", serial)
            putBoolean("play", play)
        }, null)
    }

    fun control(args: Bundle?) {
        if (args?.getInt("serial") != serial) return
        internalControl.set(true)
        try {
            if (args.getBoolean("play")) callback.onPlay() else callback.onPause()
        } catch (e: Exception) {
            log("MediaSession playback control failed", e)
            main.post { cancel() }
        } finally {
            internalControl.set(false)
        }
    }

    fun close() {
        if (closed) return
        closed = true
        cancel()
        main.removeCallbacks(update)
        announcer.close()
        controller.unregisterCallback(this)
    }
}

