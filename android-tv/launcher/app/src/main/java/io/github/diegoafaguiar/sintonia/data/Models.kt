package io.github.diegoafaguiar.sintonia.data

import android.content.Intent
import android.graphics.drawable.Drawable

/** Um app instalado com entrada LEANBACK_LAUNCHER (ou LAUNCHER, como fallback). */
data class AppEntry(
    val packageName: String,
    val label: String,
    val banner: Drawable?,
    val launchIntent: Intent,
    /** Ícone quadrado (android:icon), para os tiles do estilo vidro. */
    val icon: Drawable? = null,
    /** ApplicationInfo.category (CATEGORY_GAME, CATEGORY_AUDIO…), -1 = não declarado. */
    val category: Int = -1,
    /** O app publica um banner 16:9 de verdade (senão [banner] é o ícone). */
    val hasBanner: Boolean = true,
)

/** Uma entrada de TV (HDMI ou sintonizador) vinda do TvInputManager. */
data class InputEntry(
    val id: String,
    val label: String,
    val isTuner: Boolean,
    /** Entrada física da TV (id ".../HWn"): antena, HDMI. Apps como o Globoplay registram "tuners" próprios sem HW. */
    val builtIn: Boolean = id.contains("/HW"),
    /** Sintonizador analógico (cabo antigo); o digital (TV aberta ISDB-T) tem preferência. */
    val analog: Boolean = id.contains("Analog", ignoreCase = true),
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
    /** Quanto já foi assistido (0..1), quando o app publica posição e duração no Watch Next. */
    val progress: Float? = null,
    val seasonNumber: String? = null,
    val episodeNumber: String? = null,
    /** Minutos que faltam (duração - posição), quando o app publica os dois. */
    val remainingMin: Int? = null,
) {
    /** "T4 E7 · 32 min restantes", ou o título do episódio, ou nada. */
    val progressLabel: String? get() = listOfNotNull(
        if (!seasonNumber.isNullOrBlank() && !episodeNumber.isNullOrBlank()) "T$seasonNumber E$episodeNumber" else episodeTitle,
        remainingMin?.let { if (it >= 60) "${it / 60}h${(it % 60).toString().padStart(2, '0')} restantes" else "$it min restantes" },
    ).joinToString(" · ").ifBlank { null }
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
    EM_ALTA("Em alta"),
    FUTEBOL("Futebol"),
    FILMES("Filmes"),
    SERIES("Séries"),
    OUTROS("Para você"),
    LANCAMENTOS("Lançamentos"),
    TECNOLOGIA("Tecnologia"),
}

/** Estado da TV para o widget do hero. */
data class DeviceStatus(
    val network: String = "",        // "Wi-Fi", "Ethernet" ou "Sem rede"
    val bluetoothOn: Boolean? = null,
    val freeRamMb: Long = 0,
    val freeStorageGb: Double = 0.0,
)

/** Seção do time: próximo jogo, último resultado, tabela e vídeos. */
data class TeamContent(
    val info: FlamengoRepository.TeamInfo? = null,
    val videos: List<MediaItem> = emptyList(),
    val related: List<MediaItem> = emptyList(),   // o que os apps publicam com o nome do time
    val errors: List<String> = emptyList(),
    val fetchedAt: Long = 0L,
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
    val globo: TunerChannel? = null,
    val globoSchedule: List<ProgramEntry> = emptyList(),
    /** Em alta: destaques dos apps, os mais usados primeiro (ver HomeViewModel.refresh). */
    val hot: List<MediaItem> = emptyList(),
    /** A ordem do Em alta veio do tempo de uso real (acesso a "uso de apps" concedido). */
    val usageRanked: Boolean = false,
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
