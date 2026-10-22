package tech.futureme.tvhome.data

enum class ProfileKind(val label: String) { ADULT("Adulto"), KIDS("Infantil") }

/**
 * Perfil da home. "allowedApps = null" significa todos os apps; uma lista
 * restringe dock, "todos os apps" e o conteúdo das seções a esses pacotes.
 */
data class Profile(
    val id: String,
    val name: String,
    val kind: ProfileKind,
    val requiresPin: Boolean,
    val allowedApps: List<String>?,
    val showTeam: Boolean,
    val showGlobo: Boolean,
    val color: Long,
) {
    fun allows(packageName: String): Boolean = allowedApps == null || packageName in allowedApps
    val isKids: Boolean get() = kind == ProfileKind.KIDS
}

object Profiles {
    const val DEFAULT_PIN = "1234"

    val DIEGO = Profile(
        id = "diego", name = "Diego", kind = ProfileKind.ADULT, requiresPin = true,
        allowedApps = null, showTeam = true, showGlobo = false, color = 0xFFC62828,
    )
    val LETICIA = Profile(
        id = "leticia", name = "Letícia", kind = ProfileKind.ADULT, requiresPin = true,
        allowedApps = null, showTeam = false, showGlobo = true, color = 0xFF6A1B9A,
    )
    val KIDS = Profile(
        id = "kids", name = "Arthur e Davi", kind = ProfileKind.KIDS, requiresPin = false,
        allowedApps = listOf(
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.apple.atve.androidtv.appletv",
            "com.wbd.stream",
        ),
        showTeam = false, showGlobo = false, color = 0xFF00897B,
    )
    val ALL = listOf(DIEGO, LETICIA, KIDS)
    fun byId(id: String?): Profile? = ALL.firstOrNull { it.id == id }
}

/** Um programa da grade (EPG) de um canal aberto. */
data class ProgramEntry(val title: String, val startMs: Long, val endMs: Long, val description: String?)
