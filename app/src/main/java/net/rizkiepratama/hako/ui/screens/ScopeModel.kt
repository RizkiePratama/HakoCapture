package net.rizkiepratama.hako.ui.screens

enum class ScopeType(val title: String, val shortTitle: String) {
    YUV_PARADE("YUV Parade", "YUV"),
    RGB_PARADE("RGB Parade", "PARADE"),
    LUMA("Luminance Waveform", "LUMA"),
    VECTORSCOPE("Vectorscope", "VECTOR"),
    HISTOGRAM("Luminance Histogram", "HIST"),
    COLOR_HISTOGRAM("Color Histogram", "RGB HIST")
}

data class ScopeVisibility(
    val yuvParade: Boolean = true,
    val rgbParade: Boolean = true,
    val luma: Boolean = true,
    val vectorscope: Boolean = true,
    val histogram: Boolean = true,
    val colorHistogram: Boolean = true,
    val safeGuides: Boolean = true,
    val rgbSplit: Boolean = false,
    val yuvSplit: Boolean = false,
    val histogramSplit: Boolean = true,
    val pinnedScope: ScopeType? = null
) {
    fun pin(type: ScopeType): ScopeVisibility = copy(pinnedScope = type)

    fun needsAnalysis(dockExpanded: Boolean): Boolean =
        pinnedScope != null || (dockExpanded && visibleTypes().isNotEmpty())

    fun isVisible(type: ScopeType): Boolean = when (type) {
        ScopeType.YUV_PARADE -> yuvParade
        ScopeType.RGB_PARADE -> rgbParade
        ScopeType.LUMA -> luma
        ScopeType.VECTORSCOPE -> vectorscope
        ScopeType.HISTOGRAM -> histogram
        ScopeType.COLOR_HISTOGRAM -> colorHistogram
    }

    fun toggle(type: ScopeType): ScopeVisibility = when (type) {
        ScopeType.YUV_PARADE -> copy(yuvParade = !yuvParade)
        ScopeType.RGB_PARADE -> copy(rgbParade = !rgbParade)
        ScopeType.LUMA -> copy(luma = !luma)
        ScopeType.VECTORSCOPE -> copy(vectorscope = !vectorscope)
        ScopeType.HISTOGRAM -> copy(histogram = !histogram)
        ScopeType.COLOR_HISTOGRAM -> copy(colorHistogram = !colorHistogram)
    }

    fun visibleTypes(): List<ScopeType> = ScopeType.values().filter(::isVisible)
}

fun scopeToggleLabel(settings: ScopeVisibility): String = when (settings.visibleTypes().size) {
    0 -> "SCOPES OFF"
    ScopeType.values().size -> "ALL SCOPES"
    else -> "${settings.visibleTypes().size} SCOPES"
}
