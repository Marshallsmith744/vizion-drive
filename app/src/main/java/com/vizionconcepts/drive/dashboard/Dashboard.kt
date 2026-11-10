package com.vizionconcepts.drive.dashboard

/** Every widget the dashboard can show. `span` = grid columns used on a 2-column page. */
enum class WidgetType(val title: String, val span: Int, val needsObd: Boolean = false) {
    SPEEDO("Speedometer (your gauge style)", 1),
    TACHO("Tachometer (your gauge style)", 1, needsObd = true),
    SPEED_ARC("Speed – arc", 1),
    SPEED_DIGITAL("Speed – digital", 1),
    SPEED_BAR("Speed – bar", 2),
    RPM_ARC("RPM – arc", 1, needsObd = true),
    RPM_BAR("RPM – bar + shift light", 2, needsObd = true),
    OBD_SINGLE("OBD – coolant", 1, needsObd = true),
    OBD_QUAD("OBD – quad", 2, needsObd = true),
    CLOCK("Clock", 1),
    WEATHER("Weather", 1),
    COMPASS("Compass", 1),
    MEDIA("Now playing", 2),
    NAVIGATION("Navigation", 1),
    APP_SHORTCUTS("Dock apps", 2),
    TRIP("Trip computer", 1),
    DTC("Engine codes", 1, needsObd = true),
    AMBIENT("Ambient light", 1),
    VOLUME("Volume", 1),
    NOTE("Note", 1),
}

/**
 * Pages of widgets, in display order.
 * Encoded as `SPEED_ARC,CLOCK|MEDIA,WEATHER` (pages separated by `|`).
 */
data class DashboardLayout(val pages: List<List<WidgetType>>) {

    fun encode(): String = pages.joinToString("|") { page -> page.joinToString(",") { it.name } }

    fun withPage(index: Int, widgets: List<WidgetType>): DashboardLayout =
        copy(pages = pages.mapIndexed { i, p -> if (i == index) widgets else p })

    fun addPage(): DashboardLayout = copy(pages = pages + listOf(emptyList()))

    fun removePage(index: Int): DashboardLayout =
        if (pages.size <= 1) this else copy(pages = pages.filterIndexed { i, _ -> i != index })

    fun addWidget(page: Int, type: WidgetType) = withPage(page, pages[page] + type)

    fun removeWidget(page: Int, position: Int) =
        withPage(page, pages[page].filterIndexed { i, _ -> i != position })

    fun moveWidget(page: Int, from: Int, to: Int): DashboardLayout {
        val list = pages[page].toMutableList()
        if (from !in list.indices || to !in list.indices) return this
        list.add(to, list.removeAt(from))
        return withPage(page, list)
    }

    companion object {
        fun decode(text: String): DashboardLayout {
            val pages = text.split('|').map { page ->
                page.split(',').mapNotNull { name ->
                    name.trim().takeIf { it.isNotEmpty() }
                        ?.let { n -> WidgetType.entries.firstOrNull { it.name == n } }
                }
            }
            return if (pages.isEmpty()) Presets.CLASSIC.layout else DashboardLayout(pages)
        }

        /** Groups a page's widgets into rows of at most 2 grid columns. */
        fun rows(widgets: List<WidgetType>): List<List<WidgetType>> {
            val rows = mutableListOf<MutableList<WidgetType>>()
            var used = 2
            for (w in widgets) {
                if (used + w.span > 2) {
                    rows += mutableListOf(w); used = w.span
                } else {
                    rows.last() += w; used += w.span
                }
            }
            return rows
        }
    }
}

enum class Presets(val title: String, val description: String, val layout: DashboardLayout) {
    CLASSIC(
        "Classic", "Speed, clock, music and navigation",
        DashboardLayout(
            listOf(
                listOf(WidgetType.SPEEDO, WidgetType.CLOCK, WidgetType.MEDIA, WidgetType.NAVIGATION, WidgetType.WEATHER),
                listOf(WidgetType.TRIP, WidgetType.COMPASS, WidgetType.APP_SHORTCUTS, WidgetType.VOLUME, WidgetType.AMBIENT),
            )
        )
    ),
    PERFORMANCE(
        "Performance", "Engine data first",
        DashboardLayout(
            listOf(
                listOf(WidgetType.RPM_BAR, WidgetType.TACHO, WidgetType.SPEEDO, WidgetType.OBD_QUAD),
                listOf(WidgetType.DTC, WidgetType.TRIP, WidgetType.MEDIA),
            )
        )
    ),
    MINIMAL(
        "Minimal HUD", "Big speed, nothing else",
        DashboardLayout(listOf(listOf(WidgetType.SPEED_BAR, WidgetType.CLOCK, WidgetType.NAVIGATION)))
    ),
    MEDIA(
        "Media", "Music and apps up front",
        DashboardLayout(
            listOf(
                listOf(WidgetType.MEDIA, WidgetType.APP_SHORTCUTS, WidgetType.VOLUME, WidgetType.CLOCK),
                listOf(WidgetType.WEATHER, WidgetType.NOTE, WidgetType.AMBIENT),
            )
        )
    ),
}
