package io.github.soclear.songcue.hook

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class Main : XposedModule() {
    @SuppressLint("DiscouragedPrivateApi")
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        if (param.isSystemServer) return
        // 只依赖系统接口，提前注册，避免加固壳影响包就绪回调。
        hook(Application::class.java.getDeclaredMethod("attach", Context::class.java))
            .intercept { chain ->
                val result = chain.proceed()
                try {
                    with(chain.args[0] as Context) { MediaSessionMusic.install() }
                } catch (e: Exception) {
                    log(Log.ERROR, "SongCue", "Cannot install MediaSession hooks", e)
                }
                result
            }
    }
}
