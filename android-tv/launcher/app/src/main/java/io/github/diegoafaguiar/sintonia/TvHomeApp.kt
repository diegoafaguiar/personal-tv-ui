package io.github.diegoafaguiar.sintonia

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import io.github.diegoafaguiar.sintonia.data.Prefs
import io.github.diegoafaguiar.sintonia.remote.RemoteServer

/**
 * Coil com cache de memória contido (15% do heap em vez de 25%): numa TV de 2 GB o
 * sistema mata o launcher quando um app pesado abre, e o que vale é voltar rápido.
 * Capas ficam no cache de disco (64 MB) e recarregam sem rede.
 */
class TvHomeApp : Application(), ImageLoaderFactory {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Controle pelo celular: o servidor acompanha o ajuste (liga e desliga sem reiniciar o app). */
    override fun onCreate() {
        super.onCreate()
        val server = RemoteServer(this).also { RemoteServer.instance = it }
        scope.launch {
            Prefs(this@TvHomeApp).settings.map { it.remoteEnabled }.distinctUntilChanged().collect { on ->
                if (on) server.start() else server.stop()
            }
        }
        // TV em standby (tela apagou): esquece o perfil, para ligar a TV sempre cair no seletor.
        // Aqui e não na Activity: vale mesmo com outro app (Netflix, TV) na frente.
        registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                when (intent?.action) {
                    android.content.Intent.ACTION_SCREEN_OFF -> { scope.launch { Prefs(this@TvHomeApp).setActiveProfile(null) }; screenOffs.value++ }
                    android.content.Intent.ACTION_SCREEN_ON -> screenOns.value++
                }
            }
        }, android.content.IntentFilter().apply { addAction(android.content.Intent.ACTION_SCREEN_OFF); addAction(android.content.Intent.ACTION_SCREEN_ON) })
    }

    companion object {
        /** Contadores de tela apagada/acesa (standby da TV), para o HomeViewModel reagir. */
        val screenOffs = MutableStateFlow(0)
        val screenOns = MutableStateFlow(0)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(64L * 1024 * 1024).build() }
        .crossfade(false)
        .respectCacheHeaders(false)
        .build()
}
