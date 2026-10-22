package tech.futureme.tvhome.data

import android.content.Intent
import android.graphics.drawable.Drawable

/** Um app instalado com entrada LEANBACK_LAUNCHER (ou LAUNCHER, como fallback). */
data class AppEntry(
    val packageName: String,
    val label: String,
    val banner: Drawable?,
    val launchIntent: Intent,
)

/** Uma entrada de TV (HDMI ou sintonizador) vinda do TvInputManager. */
data class InputEntry(
    val id: String,
    val label: String,
    val isTuner: Boolean,
)

/**
 * Um item publicado por um app no TvContract: um programa do Watch Next
 * (continuar assistindo) ou de um canal de prévia (sugestões).
 * Os tipos e gêneros seguem TvContract.PreviewPrograms.
 */
data class MediaItem(
    val id: Long,
    val packageName: String,
    val title: String,
    val description: String?,
    val posterUri: String?,
    val thumbnailUri: String?,
    val type: Int,
    val genres: List<String>,
    val intentUri: String?,
    val lastEngagementMs: Long,
    val isWatchNext: Boolean,
    val channelName: String?,
    val episodeTitle: String?,
) {
    /** Melhor imagem 16:9 disponível. */
    val imageUri: String? get() = thumbnailUri ?: posterUri
}

/** Canal do sintonizador (TV aberta) gravado no TvContract pelo app de TV ao vivo. */
data class TunerChannel(
    val id: Long,
    val number: String,
    override val name: String,
    val inputId: String,
    val type: String = "",
    override val nowTitle: String? = null,
    val nowEnd: Long = 0L,
    val nextTitle: String? = null,
) : Classifier.MediaChannelLike {
    /** TV aberta de verdade (ISDB-T, DVB, ATSC…); o resto são canais por internet (FAST). */
    val isBroadcast: Boolean get() = type.startsWith("TYPE_ISDB") || type.startsWith("TYPE_DVB") || type.startsWith("TYPE_ATSC") || type.startsWith("TYPE_NTSC") || type.startsWith("TYPE_PAL") || type == "TYPE_OTHER" && inputId.contains("Tuner", ignoreCase = true)
}

/** Seções da home. A ordem aqui é a ordem na tela. */
enum class Section(val title: String) {
    CONTINUE("Continuar assistindo"),
    FUTEBOL("Futebol"),
    FILMES("Filmes"),
    SERIES("Séries"),
    OUTROS("Para você"),
}

/** Estado da TV para o widget do hero. */
data class DeviceStatus(
    val network: String = "",        // "Wi-Fi", "Ethernet" ou "Sem rede"
    val bluetoothOn: Boolean? = null,
    val freeRamMb: Long = 0,
    val freeStorageGb: Double = 0.0,
)

/** Tudo que a home precisa, carregado de uma vez. */
data class HomeContent(
    val apps: List<AppEntry> = emptyList(),
    val inputs: List<InputEntry> = emptyList(),
    val sections: Map<Section, List<MediaItem>> = emptyMap(),
    val perApp: Map<String, List<MediaItem>> = emptyMap(),
    val tunerChannels: List<TunerChannel> = emptyList(),    // TV aberta
    val fastChannels: List<TunerChannel> = emptyList(),     // canais por internet (TCL Channel+ etc.)
    val sportsChannels: List<TunerChannel> = emptyList(),   // os de esporte, para a seção Futebol
    val hasPermission: Boolean = false,
    val diagnostics: Diagnostics = Diagnostics(),
    val status: DeviceStatus = DeviceStatus(),
) {
    val allItems: List<MediaItem> get() = sections.values.flatten() + perApp.values.flatten()
}

/** Contagens para a tela de diagnóstico: o que cada app publica de fato. */
data class Diagnostics(
    val watchNextByApp: Map<String, Int> = emptyMap(),
    val channelsByApp: Map<String, Int> = emptyMap(),
    val programsByApp: Map<String, Int> = emptyMap(),
    val byType: Map<String, Int> = emptyMap(),
    val genresSeen: Set<String> = emptySet(),
    val tunerChannels: List<String> = emptyList(),
    val epgSample: List<String> = emptyList(),
    val error: String? = null,
)
