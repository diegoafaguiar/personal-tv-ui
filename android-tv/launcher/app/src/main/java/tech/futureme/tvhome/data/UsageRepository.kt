package tech.futureme.tvhome.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log

/**
 * Tempo de uso de cada app (UsageStatsManager), para ordenar a linha "Em alta" pelos apps
 * que a casa mais usa. Precisa do acesso especial "uso de apps", que na TV só se concede
 * por adb: `appops set tech.futureme.tvhome GET_USAGE_STATS allow`
 * (android-tv/scripts/50-launcher.sh grant faz isso). Sem ele, devolve vazio e a linha
 * usa a ordem de apps dos Ajustes.
 */
class UsageRepository(private val context: Context) {
    fun hasAccess(): Boolean = runCatching {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Milissegundos em primeiro plano por pacote nos últimos [days] dias. */
    fun foregroundTime(days: Int = 30): Map<String, Long> {
        if (!hasAccess()) return emptyMap()
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            usm.queryAndAggregateUsageStats(end - days * 24L * 3600_000L, end)
                .mapValues { it.value.totalTimeInForeground }
                .filterValues { it > 0 }
        } catch (e: Exception) {
            Log.w("TvHome", "Uso de apps indisponível: $e"); emptyMap()
        }
    }
}
