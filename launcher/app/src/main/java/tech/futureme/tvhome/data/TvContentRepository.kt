package tech.futureme.tvhome.data

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.tv.TvContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Lê o que os apps publicam no TvProvider (content://android.media.tv/...):
 *  - watch_next_program: "continuar assistindo" de cada app;
 *  - channel (TYPE_PREVIEW) + preview_program: as linhas de sugestões.
 * Sem READ_TV_LISTINGS o provedor devolve só as linhas do próprio app (nada),
 * sem erro: por isso o diagnóstico mostra a permissão explicitamente.
 */
class TvContentRepository(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_TV_LISTINGS) ==
            PackageManager.PERMISSION_GRANTED

    data class Loaded(val items: List<MediaItem>, val diagnostics: Diagnostics)

    fun load(): Loaded {
        val diag = Diagnostics()
        if (!hasPermission()) return Loaded(emptyList(), diag.copy(error = "Sem READ_TV_LISTINGS"))
        return try {
            val watchNext = queryWatchNext()
            val channels = queryPreviewChannels()
            val programs = queryPreviewPrograms(channels)
            val all = watchNext + programs
            Loaded(
                all,
                Diagnostics(
                    watchNextByApp = watchNext.groupingBy { it.packageName }.eachCount(),
                    channelsByApp = channels.values.groupingBy { it.packageName }.eachCount(),
                    programsByApp = programs.groupingBy { it.packageName }.eachCount(),
                    byType = all.groupingBy { Classifier.typeName(it.type) }.eachCount(),
                    genresSeen = all.flatMap { it.genres }.toSet(),
                ),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Falha lendo TvContract", e)
            Loaded(emptyList(), diag.copy(error = e.toString()))
        }
    }

    private data class PreviewChannel(val id: Long, val packageName: String, val displayName: String?)

    private fun queryPreviewChannels(): Map<Long, PreviewChannel> {
        val out = HashMap<Long, PreviewChannel>()
        val projection = arrayOf(
            TvContract.Channels._ID,
            TvContract.Channels.COLUMN_PACKAGE_NAME,
            TvContract.Channels.COLUMN_DISPLAY_NAME,
            TvContract.Channels.COLUMN_BROWSABLE,
        )
        resolver.query(
            TvContract.Channels.CONTENT_URI, projection,
            "${TvContract.Channels.COLUMN_TYPE}=?", arrayOf(TvContract.Channels.TYPE_PREVIEW), null,
        )?.use { c ->
            while (c.moveToNext()) {
                // Canais não "browsable" foram escondidos pelo usuário na home do Google; respeitamos.
                if (c.getIntOrZero(TvContract.Channels.COLUMN_BROWSABLE) == 0) continue
                val id = c.getLong(c.getColumnIndexOrThrow(TvContract.Channels._ID))
                out[id] = PreviewChannel(
                    id,
                    c.getStringOrNull(TvContract.Channels.COLUMN_PACKAGE_NAME) ?: "",
                    c.getStringOrNull(TvContract.Channels.COLUMN_DISPLAY_NAME),
                )
            }
        }
        return out
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
    )

    private fun queryPreviewPrograms(channels: Map<Long, PreviewChannel>): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        resolver.query(TvContract.PreviewPrograms.CONTENT_URI, programProjection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getIntOrZero(TvContract.PreviewPrograms.COLUMN_BROWSABLE) == 0) continue
                val channelId = c.getLong(c.getColumnIndexOrThrow(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID))
                val channel = channels[channelId] ?: continue   // canal escondido ou inexistente
                out += c.toMediaItem(isWatchNext = false, channelName = channel.displayName, lastEngagement = 0L)
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
    )

    private fun queryWatchNext(): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        resolver.query(
            TvContract.WatchNextPrograms.CONTENT_URI, watchNextProjection, null, null,
            "${TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getIntOrZero(TvContract.WatchNextPrograms.COLUMN_BROWSABLE) == 0) continue
                val last = c.getLongOrZero(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS)
                out += c.toMediaItem(isWatchNext = true, channelName = null, lastEngagement = last)
            }
        }
        return out
    }

    private fun Cursor.toMediaItem(isWatchNext: Boolean, channelName: String?, lastEngagement: Long): MediaItem {
        val genreRaw = getStringOrNull(TvContract.PreviewPrograms.COLUMN_CANONICAL_GENRE)
        val genres = if (genreRaw.isNullOrBlank()) emptyList() else TvContract.Programs.Genres.decode(genreRaw).toList()
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
