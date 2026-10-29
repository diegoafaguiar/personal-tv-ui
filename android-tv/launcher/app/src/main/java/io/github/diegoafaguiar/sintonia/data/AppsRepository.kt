package io.github.diegoafaguiar.sintonia.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** Apps instalados, entradas HDMI e TV ao vivo. */
class AppsRepository(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    fun apps(): List<AppEntry> {
        val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()
        for (ri in pm.queryIntentActivities(leanback, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == context.packageName || !seen.add(pkg)) continue
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: continue
            val realBanner = ri.activityInfo.loadBanner(pm) ?: ri.activityInfo.applicationInfo.loadBanner(pm)
            val banner = realBanner ?: ri.activityInfo.loadIcon(pm)
            val icon = runCatching { ri.activityInfo.loadIcon(pm) }.getOrNull()
            out += AppEntry(pkg, ri.loadLabel(pm).toString(), banner, intent, icon = icon, category = ri.activityInfo.applicationInfo.category, hasBanner = realBanner != null)
        }
        return out.sortedBy { it.label.lowercase() }
    }

    /** Sintonizador da própria TV (antena): físico e digital de preferência. Nunca o "tuner" de um app. */
    fun builtInTuner(inputs: List<InputEntry>): InputEntry? =
        inputs.firstOrNull { it.isTuner && it.builtIn && !it.analog } ?: inputs.firstOrNull { it.isTuner && it.builtIn }

    fun inputs(): List<InputEntry> {
        val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        return tim.tvInputList
            .filter { it.parentId == null && !it.isHidden(context) }
            .filter { it.type == TvInputInfo.TYPE_HDMI || it.type == TvInputInfo.TYPE_TUNER }
            // Físicas primeiro (antena digital, depois analógica, HDMI); as de apps (Globoplay…) por último
            .sortedWith(compareBy({ !it.id.contains("/HW") }, { it.id.contains("Analog", ignoreCase = true) }, { it.id }))
            .map { info ->
                val label = (info.loadCustomLabel(context) ?: info.loadLabel(context))?.toString()
                    ?: if (info.type == TvInputInfo.TYPE_TUNER) "TV" else "HDMI"
                InputEntry(info.id, label, info.type == TvInputInfo.TYPE_TUNER)
            }
    }

    /**
     * HDMI e sintonizador abrem pelo URI de passthrough da entrada: o app de TV ao vivo sobe já
     * naquela entrada (na TCL, com.tcl.tv/TVActivity). Abrir o app "seco" o deixava no último
     * input usado. Sem quem atenda o passthrough, abre o app.
     */
    fun inputIntent(input: InputEntry, liveTvPackage: String): Intent? {
        val view = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id))
        if (view.resolveActivity(pm) != null) return view
        return pm.getLeanbackLaunchIntentForPackage(liveTvPackage) ?: pm.getLaunchIntentForPackage(liveTvPackage)
    }

    fun label(packageName: String): String =
        try { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() } catch (e: Exception) { packageName }
}
