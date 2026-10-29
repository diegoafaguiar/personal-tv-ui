package io.github.diegoafaguiar.sintonia.data

/**
 * Estilos prontos: um clique muda papel de parede, vidro e todos os ajustes de Aparência.
 * Depois dá para mexer em qualquer ajuste; a tela mostra "Personalizado" até voltar a bater
 * com um estilo. Aplicar: Prefs.applyStyle. Detectar: [StylePreset.current]. A fonte fica de
 * fora (escolha à parte, vale para qualquer estilo).
 */
enum class StylePreset(
    val label: String,
    val hint: String,
    val background: Background,
    val look: Look,
    val transparency: Float = 0.6f,
    val fx: Float = 1f,
    val animations: Boolean = true,
    /** Só o estilo Leve liga o modo leve; os outros não mexem nele. */
    val lite: Boolean = false,
) {
    VIDRO(
        "Vidro", "o padrão: vidro sobre a foto",
        Background.HORIZONTE, Look(),
    ),
    ICONES(
        "Ícones coloridos", "apps na cor da marca, maiores",
        Background.NOITE,
        Look(cardScale = 1.1f, tileStyle = TileStyle.MARCA, focusStyle = FocusStyle.BRILHO, focusScale = 1.1f, focusBorder = 0),
        fx = 0.5f,
    ),
    MINIMALISTA(
        "Minimalista", "fundo preto, só ícones, sem reflexos",
        Background.PRETO,
        Look(labelMode = LabelMode.ICONE, tileStyle = TileStyle.ESCURO, rowIcons = false, unselectedAlpha = 0.6f, focusScale = 1.04f, focusBorder = 2, cardSpacing = 16, greeting = false),
        transparency = 0.8f, fx = 0f,
    ),
    CINEMA(
        "Cinema", "cards grandes, linha focada fixa",
        Background.HORIZONTE,
        Look(cardScale = 1.15f, fixedFocusRow = true, hideRowsAbove = true, focusStyle = FocusStyle.BORDA_BRILHO, accent = Accent.AMBAR, focusScale = 1.06f),
    ),
    LEVE(
        "Leve", "para TV fraca: degradê, sem animações",
        Background.GRADIENTE,
        Look(tileStyle = TileStyle.ESCURO, focusScale = 1.04f),
        fx = 0f, animations = false, lite = true,
    );

    /** Os ajustes atuais são exatamente este estilo? (modo leve só conta no estilo Leve) */
    fun matches(s: Settings): Boolean =
        s.background == background && s.look.copy(font = FontChoice.PADRAO) == look && s.glass.enabled &&
            s.glass.transparency == transparency && s.glass.fx == fx && s.glass.animations == animations &&
            (!lite || s.glass.lite)

    companion object {
        /** Estilo em uso, ou null quando o usuário personalizou. */
        fun current(s: Settings): StylePreset? = entries.firstOrNull { it.matches(s) }
    }
}
