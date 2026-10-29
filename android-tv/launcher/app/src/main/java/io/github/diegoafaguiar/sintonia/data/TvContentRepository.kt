package io.github.diegoafaguiar.sintonia.data

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.tv.TvContract
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.diegoafaguiar.sintonia.READ_TV_LISTINGS

/**
 * Lê o que os apps publicam no TvProvider (content://android.media.tv/...):
 *  - watch_next_program: "continuar assistindo" de cada app;
 *  - channel (TYPE_PREVIEW) + preview_program: as linhas de sugestões;
 *  - channel (demais tipos) + program: canais do sintonizador e a grade (EPG).
 *
 * Regra do TvProvider para apps sem permissão de sistema: NENHUMA cláusula
 * "selection" é aceita ("Selection not allowed"); só filtros por parâmetros
 * de URI. Por isso tudo aqui consulta sem selection e filtra em memória.
 * Sem READ_TV_LISTINGS o provedor devolve só as linhas do próprio app.
 */
class TvContentRepository(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, READ_TV_LISTINGS) == PackageManager.PERMISSION_GRANTED

    data class Loaded(val items: List<MediaItem>, val tuner: List<TunerChannel>, val diagnostics: Diagnostics)

    fun load(): Loaded {
        if (!hasPermission()) return Loaded(emptyList(), emptyList(), Diagnostics(error = "Sem READ_TV_LISTINGS"))
        var error: String? = null
        var watchNext = emptyList<MediaItem>()
        var channels = emptyMap<Long, Channel>()
        var programs = emptyList<MediaItem>()
        var tuner = emptyList<TunerChannel>()
        try { watchNext = queryWatchNext() } catch (e: Exception) { error = "watch_next: $e"; Log.w(TAG, "Falha lendo Watch Next", e) }
        try { channels = queryChannels() } catch (e: Exception) { error = "channel: $e"; Log.w(TAG, "Falha lendo canais", e) }
        val preview = channels.filterValues { it.isPreview }
        try { programs = queryPreviewPrograms(preview) } catch (e: Exception) { error = "preview_program: $e"; Log.w(TAG, "Falha lendo programas de prévia", e) }
        try {
            tuner = channels.values.filter { !it.isPreview && it.browsable }
                .map { c -> TunerChannel(c.id, c.number ?: "", c.displayName ?: "", c.inputId ?: "", c.type ?: "") }
                .sortedWith(compareBy({ it.number.substringBefore('.').toIntOrNull() ?: 9999 }, { it.number }))
                .map { if (it.isBroadcast) withNowNext(it) else it }   // EPG só na TV aberta (os FAST não têm)
        } catch (e: Exception) { error = "program: $e"; Log.w(TAG, "Falha lendo EPG", e) }

        val all = watchNext + programs
        val epgSample = tuner.filter { it.isBroadcast }.take(8).map { "  ${it.number} ${it.name}: agora ${it.nowTitle ?: "—"} · depois ${it.nextTitle ?: "—"}" }
        return Loaded(
            all, tuner,
            Diagnostics(
                watchNextByApp = watchNext.groupingBy { it.packageName }.eachCount(),
                channelsByApp = preview.values.groupingBy { it.packageName }.eachCount(),
                programsByApp = programs.groupingBy { it.packageName }.eachCount(),
                byType = all.groupingBy { Classifier.typeName(it.type) }.eachCount(),
                genresSeen = all.flatMap { it.genres }.toSet(),
                tunerChannels = tuner.map { "${it.number} ${it.name} (${it.inputId.substringBefore('/')}, ${it.type}${if (it.isBroadcast) ", TV aberta" else ", internet"})" },
                epgSample = epgSample,
                error = error,
            ),
        )
    }

    private data class Channel(
        val id: Long, val packageName: String, val displayName: String?, val number: String?,
        val type: String?, val inputId: String?, val browsable: Boolean,
    ) { val isPreview get() = type == TvContract.Channels.TYPE_PREVIEW }

    private fun queryChannels(): Map<Long, Channel> {
        val out = HashMap<Long, Channel>()
        val projection = arrayOf(
            TvContract.Channels._ID, TvContract.Channels.COLUMN_PACKAGE_NAME, TvContract.Channels.COLUMN_DISPLAY_NAME,
            TvContract.Channels.COLUMN_DISPLAY_NUMBER, TvContract.Channels.COLUMN_TYPE, TvContract.Channels.COLUMN_INPUT_ID,
            TvContract.Channels.COLUMN_BROWSABLE,
        )
        resolver.query(TvContract.Channels.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(TvContract.Channels._ID))
                out[id] = Channel(
                    id,
                    c.getStringOrNull(TvContract.Channels.COLUMN_PACKAGE_NAME) ?: "",
                    c.getStringOrNull(TvContract.Channels.COLUMN_DISPLAY_NAME),
                    c.getStringOrNull(TvContract.Channels.COLUMN_DISPLAY_NUMBER),
                    c.getStringOrNull(TvContract.Channels.COLUMN_TYPE),
                    c.getStringOrNull(TvContract.Channels.COLUMN_INPUT_ID),
                    c.getIntOrZero(TvContract.Channels.COLUMN_BROWSABLE) != 0,
                )
            }
        }
        return out
    }

    /** Grade de um canal nas próximas horas (para a seção TV Globo). */
    fun programs(ch: TunerChannel, hours: Int): List<ProgramEntry> {
        val now = System.currentTimeMillis()
        val uri = TvContract.buildProgramsUriForChannel(ch.id, now - 30 * 60_000L, now + hours * 3600_000L)
        val projection = arrayOf(TvContract.Programs.COLUMN_TITLE, TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS, TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS, TvContract.Programs.COLUMN_SHORT_DESCRIPTION)
        val out = ArrayList<ProgramEntry>()
        resolver.query(uri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out += ProgramEntry(
                    c.getStringOrNull(TvContract.Programs.COLUMN_TITLE) ?: "",
                    c.getLongOrZero(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS),
                    c.getLongOrZero(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS),
                    c.getStringOrNull(TvContract.Programs.COLUMN_SHORT_DESCRIPTION),
                )
            }
        }
        return out.filter { it.endMs > now }.sortedBy { it.startMs }
    }

    /** Programa de agora e o seguinte, pela URI com intervalo (parâmetros são permitidos). */
    fun withNowNext(ch: TunerChannel): TunerChannel {
        val now = System.currentTimeMillis()
        val uri = TvContract.buildProgramsUriForChannel(ch.id, now - 5 * 60_000L, now + 6 * 3600_000L)
        val projection = arrayOf(TvContract.Programs.COLUMN_TITLE, TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS, TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS)
        val progs = ArrayList<Triple<String, Long, Long>>()
        resolver.query(uri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                progs += Triple(
                    c.getStringOrNull(TvContract.Programs.COLUMN_TITLE) ?: "",
                    c.getLongOrZero(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS),
                    c.getLongOrZero(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS),
                )
            }
        }
        progs.sortBy { it.second }
        val current = progs.firstOrNull { it.second <= now && it.third > now }
        val next = progs.firstOrNull { it.second > now }
        return ch.copy(nowTitle = current?.first, nowEnd = current?.third ?: 0L, nextTitle = next?.first)
    }

    private val programProjection = arrayOf(
        TvContract.PreviewPrograms._ID,
        TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME,
        TvContract.PreviewPrograms.COLUMN_CHANNEL_ID,
        TvContract.PreviewPrograms.COLUMN_TITLE,
        TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,
        TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
        TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI,
        TvContract.PreviewPrograms.COLUMN_TYPE,
        TvContract.PreviewPrograms.COLUMN_CANONICAL_GENRE,
        TvContract.PreviewPrograms.COLUMN_INTENT_URI,
        TvContract.PreviewPrograms.COLUMN_BROWSABLE,
        TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE,
        TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
        TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
    )

    /**
     * Programas de prévia, canal a canal: a listagem geral devolve vazio para
     * apps comuns; com "?channel=<id>" (parâmetro de URI permitido) funciona.
     */
    private fun queryPreviewPrograms(channels: Map<Long, Channel>): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        for (channel in channels.values) {
            if (!channel.browsable) continue                     // escondido pelo usuário na home do Google
            val uri = TvContract.buildPreviewProgramsUriForChannel(channel.id)
            resolver.query(uri, programProjection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    if (c.getIntOrZero(TvContract.PreviewPrograms.COLUMN_BROWSABLE) == 0) continue
                    out += c.toMediaItem(isWatchNext = false, channelName = channel.displayName, lastEngagement = 0L)
                }
            }
        }
        return out
    }

    private val watchNextProjection = arrayOf(
        TvContract.WatchNextPrograms._ID,
        TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
        TvContract.WatchNextPrograms.COLUMN_TITLE,
        TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION,
        TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
        TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
        TvContract.WatchNextPrograms.COLUMN_TYPE,
        TvContract.WatchNextPrograms.COLUMN_CANONICAL_GENRE,
        TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
        TvContract.WatchNextPrograms.COLUMN_BROWSABLE,
        TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
        TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
        TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
        TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
        TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
        TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
    )

    private fun queryWatchNext(): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        resolver.query(TvContract.WatchNextPrograms.CONTENT_URI, watchNextProjection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getIntOrZero(TvContract.WatchNextPrograms.COLUMN_BROWSABLE) == 0) continue
                val last = c.getLongOrZero(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS)
                out += c.toMediaItem(isWatchNext = true, channelName = null, lastEngagement = last)
            }
        }
        return out.sortedByDescending { it.lastEngagementMs }
    }

    private fun Cursor.toMediaItem(isWatchNext: Boolean, channelName: String?, lastEngagement: Long): MediaItem {
        val genreRaw = getStringOrNull(TvContract.PreviewPrograms.COLUMN_CANONICAL_GENRE)
        val genres = if (genreRaw.isNullOrBlank()) emptyList() else TvContract.Programs.Genres.decode(genreRaw).toList()
        val position = if (isWatchNext) getLongOrZero(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS) else 0L
        val duration = if (isWatchNext) getLongOrZero(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS) else 0L
        val progress = if (position > 0 && duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null
        return MediaItem(
            id = getLong(getColumnIndexOrThrow(TvContract.PreviewPrograms._ID)),
            packageName = getStringOrNull(TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME) ?: "",
            title = getStringOrNull(TvContract.PreviewPrograms.COLUMN_TITLE) ?: "",
            description = getStringOrNull(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION),
            posterUri = getStringOrNull(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI),
            thumbnailUri = getStringOrNull(TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI),
            type = getIntOrZero(TvContract.PreviewPrograms.COLUMN_TYPE),
            genres = genres,
            intentUri = getStringOrNull(TvContract.PreviewPrograms.COLUMN_INTENT_URI),
            lastEngagementMs = lastEngagement,
            isWatchNext = isWatchNext,
            channelName = channelName,
            episodeTitle = getStringOrNull(TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE),
            progress = progress,
            seasonNumber = getStringOrNull(TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER),
            episodeNumber = getStringOrNull(TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER),
            remainingMin = if (position > 0 && duration > position) ((duration - position) / 60_000L).toInt().coerceAtLeast(1) else null,
        )
    }

    private fun Cursor.getStringOrNull(column: String): String? {
        val i = getColumnIndex(column); return if (i < 0 || isNull(i)) null else getString(i)
    }
    private fun Cursor.getIntOrZero(column: String): Int {
        val i = getColumnIndex(column); return if (i < 0 || isNull(i)) 0 else getInt(i)
    }
    private fun Cursor.getLongOrZero(column: String): Long {
        val i = getColumnIndex(column); return if (i < 0 || isNull(i)) 0L else getLong(i)
    }

    companion object { const val TAG = "TvHome" }
}
