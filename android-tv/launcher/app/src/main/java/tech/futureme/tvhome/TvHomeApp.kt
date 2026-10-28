package tech.futureme.tvhome

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Coil com cache de memória contido (15% do heap em vez de 25%): numa TV de 2 GB o
 * sistema mata o launcher quando um app pesado abre, e o que vale é voltar rápido.
 * Capas ficam no cache de disco (64 MB) e recarregam sem rede.
 */
class TvHomeApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(64L * 1024 * 1024).build() }
        .crossfade(false)
        .respectCacheHeaders(false)
        .build()
}
