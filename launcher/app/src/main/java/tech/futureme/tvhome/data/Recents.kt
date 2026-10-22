package tech.futureme.tvhome.data

/** Um app ou conteúdo aberto pela home, para a linha "Abertos recentemente". */
data class RecentEntry(val packageName: String, val title: String, val intentUri: String?, val at: Long) {
    fun encode(): String = listOf(packageName, title.replace('|', '/'), intentUri ?: "", at.toString()).joinToString("|")

    companion object {
        const val MAX = 12
        fun decode(s: String): RecentEntry? {
            val p = s.split('|'); if (p.size < 4) return null
            return RecentEntry(p[0], p[1], p[2].ifBlank { null }, p[3].toLongOrNull() ?: 0L)
        }
    }
}
