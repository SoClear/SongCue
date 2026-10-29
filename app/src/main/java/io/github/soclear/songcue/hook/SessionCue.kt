package io.github.soclear.songcue.hook

internal data class SessionSong(val id: String?, val title: String, val singer: String?) {
    // 有媒体 ID 时只按 ID 去重，通知栏歌词可能不断改写歌名和歌手。
    val key get() = id?.takeIf { it.isNotBlank() }?.let { listOf(it) }
        ?: listOf(title, singer.orEmpty())

    fun normalized(packageName: String): SessionSong =
        if (packageName == "com.luna.music" && singer?.contains(" — ") == true)
            copy(title = singer.substringBeforeLast(" — "), singer = singer.substringAfterLast(" — "))
        else this

    fun text(singerPosition: Int): String = when {
        singer.isNullOrBlank() || singerPosition == 0 -> title
        singerPosition == 1 -> "$singer，$title"
        else -> "$title，$singer"
    }
}

/** 仅处理切歌去重和待播报状态，不依赖 Android，便于回归测试。 */
internal class SessionCue {
    enum class Phase { WAITING, SPEAKING, PAUSED }

    @Volatile var pending: SessionSong? = null
        private set
    var phase: Phase? = null
        private set
    private var lastKey: List<String>? = null

    fun select(song: SessionSong, playing: Boolean): Boolean {
        if (song.key == lastKey) return false
        if (!playing && (pending == null || phase == Phase.PAUSED)) return false
        lastKey = song.key
        pending = song
        phase = Phase.WAITING
        return true
    }

    fun speak(): SessionSong? {
        if (phase != Phase.WAITING) return null
        phase = Phase.SPEAKING
        return pending
    }

    fun pause() {
        if (pending != null) phase = Phase.PAUSED
    }

    fun resume() {
        if (phase == Phase.PAUSED) phase = Phase.WAITING
    }

    fun cancel(reset: Boolean = false) {
        pending = null
        phase = null
        if (reset) lastKey = null
    }
}
