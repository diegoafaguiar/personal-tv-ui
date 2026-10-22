package tech.futureme.tvhome.data

import android.media.tv.TvContract

/**
 * Decide em qual seção um item publicado cai. Regras, em ordem:
 *  1. Watch Next → "Continuar assistindo" (independente do tipo).
 *  2. Gênero SPORTS ou texto com cara de futebol → "Futebol".
 *  3. TYPE_MOVIE → "Filmes".
 *  4. TYPE_TV_SERIES / SEASON / EPISODE → "Séries".
 *  5. Resto (clipes, eventos, canais, música) → "Para você".
 *
 * Os apps nem sempre preenchem gênero; por isso o fallback por palavras-chave,
 * ajustado para o Brasil. Ajuste a lista com o que aparecer no diagnóstico.
 */
object Classifier {
    private val footballWords = Regex(
        "\\b(futebol|brasileir[ãa]o|libertadores|sul-?americana|copa do brasil|copa do mundo|" +
            "champions|premier league|la ?liga|bundesliga|serie a|ligue 1|campeonato|rodada|gol(s|aço)?|" +
            "paulist[ãa]o|carioca|mineiro|ga[úu]cho|flamengo|palmeiras|corinthians|s[ãa]o paulo fc|" +
            "santos fc|gr[êe]mio|internacional|fluminense|botafogo|vasco|cruzeiro|atl[ée]tico|" +
            "sele[çc][ãa]o brasileira|fifa|uefa|conmebol|cbf)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val versusWords = Regex("\\b\\S+\\s+(x|vs\\.?)\\s+\\S+\\b", RegexOption.IGNORE_CASE)

    fun section(item: MediaItem): Section {
        if (item.isWatchNext) return Section.CONTINUE
        val text = listOfNotNull(item.title, item.description, item.channelName, item.episodeTitle).joinToString(" ")
        if (item.genres.any { it.equals(TvContract.Programs.Genres.SPORTS, ignoreCase = true) } ||
            footballWords.containsMatchIn(text) ||
            (versusWords.containsMatchIn(item.title) && item.type == TvContract.PreviewPrograms.TYPE_EVENT)
        ) return Section.FUTEBOL
        return when (item.type) {
            TvContract.PreviewPrograms.TYPE_MOVIE -> Section.FILMES
            TvContract.PreviewPrograms.TYPE_TV_SERIES,
            TvContract.PreviewPrograms.TYPE_TV_SEASON,
            TvContract.PreviewPrograms.TYPE_TV_EPISODE -> Section.SERIES
            else -> Section.OUTROS
        }
    }

    fun typeName(type: Int): String = when (type) {
        TvContract.PreviewPrograms.TYPE_MOVIE -> "MOVIE"
        TvContract.PreviewPrograms.TYPE_TV_SERIES -> "TV_SERIES"
        TvContract.PreviewPrograms.TYPE_TV_SEASON -> "TV_SEASON"
        TvContract.PreviewPrograms.TYPE_TV_EPISODE -> "TV_EPISODE"
        TvContract.PreviewPrograms.TYPE_CLIP -> "CLIP"
        TvContract.PreviewPrograms.TYPE_EVENT -> "EVENT"
        TvContract.PreviewPrograms.TYPE_CHANNEL -> "CHANNEL"
        TvContract.PreviewPrograms.TYPE_TRACK -> "TRACK"
        TvContract.PreviewPrograms.TYPE_ALBUM -> "ALBUM"
        TvContract.PreviewPrograms.TYPE_ARTIST -> "ARTIST"
        TvContract.PreviewPrograms.TYPE_PLAYLIST -> "PLAYLIST"
        TvContract.PreviewPrograms.TYPE_STATION -> "STATION"
        TvContract.PreviewPrograms.TYPE_GAME -> "GAME"
        else -> "TYPE_$type"
    }
}
