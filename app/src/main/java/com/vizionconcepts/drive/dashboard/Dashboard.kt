package com.vizionconcepts.drive.dashboard

/**
 * Every widget the dashboard can show.
 * [span] is the default width class (1 = third of the page, 2 = two thirds).
 * [aspect] = height / width; it is fixed so resizing always keeps proportions.
 */
enum class WidgetType(val title: String, val span: Int, val needsObd: Boolean = false, val aspect: Float = if (span == 2) 0.42f else 1f) {
    SPEEDO("Speedometer (your gauge style)", 1),
    TACHO("Tachometer (your gauge style)", 1, needsObd = true),
    CLUSTER_DASH("Twin-gauge cluster – tacho, speedo, temps", 2, aspect = 0.5f),
    TWIN_DASH("Twin-dial dash – speed + clock", 2, aspect = 0.56f),
    SPEED_ARC("Speed – arc", 1),
    SPEED_DIGITAL("Speed – digital", 1, aspect = 0.8f),
    SPEED_BAR("Speed – bar", 2),
    RPM_ARC("RPM – arc", 1, needsObd = true),
    RPM_BAR("RPM – bar + shift light", 2, needsObd = true),
    OBD_SINGLE("OBD – coolant", 1, needsObd = true),
    OBD_QUAD("OBD – quad", 2, needsObd = true, aspect = 0.55f),
    CLOCK("Clock", 1, aspect = 0.75f),
    WEATHER("Weather", 1, aspect = 0.75f),
    COMPASS("Compass", 1, aspect = 0.75f),
    MEDIA("Now playing", 2),
    NAVIGATION("Navigation", 1, aspect = 0.75f),
    APP_SHORTCUTS("Dock apps", 2, aspect = 0.3f),
    TRIP("Trip computer", 1, aspect = 0.75f),
    DTC("Engine codes", 1, needsObd = true, aspect = 0.85f),
    AMBIENT("Ambient light", 1, aspect = 0.85f),
    VOLUME("Volume", 1, aspect = 0.75f),
    NOTE("Note", 1, aspect = 0.5f);

    /** Whole-screen digital dashes start at full page width. */
    val isFullDash: Boolean get() = this == TWIN_DASH || this == CLUSTER_DASH
}

/**
 * One widget on a page. All units are fractions of the page width (so the layout
 * scales with the screen); height = w * type.aspect, which keeps proportions.
 */
data class Placement(val type: WidgetType, val x: Float, val y: Float, val w: Float) {
    val h: Float get() = w * type.aspect
    val bottom: Float get() = y + h

    /** Move/resize with grid snapping and staying inside the page width. */
    fun moved(nx: Float, ny: Float) = copy(x = snap(nx).coerceIn(0f, 1f - w), y = snap(ny).coerceAtLeast(0f))
    fun resized(nw: Float) = snap(nw).coerceIn(MIN_W, 1f).let { cw -> copy(w = cw, x = x.coerceAtMost(1f - cw)) }

    fun overlaps(o: Placement): Boolean =
        x < o.x + o.w - EPS && o.x < x + w - EPS && y < o.bottom - EPS && o.y < bottom - EPS

    fun encode() = "${type.name}@${fmt(x)},${fmt(y)},${fmt(w)}"

    companion object {
        const val GRID = 1f / 48f
        const val MIN_W = 0.16f
        fun snap(v: Float) = Math.round(v / GRID) * GRID
        private fun fmt(v: Float) = "%.4f".format(java.util.Locale.US, v)
        private const val EPS = 0.0005f

        /** Rounds up to the grid, so a widget pushed below another never snaps back into it. */
        fun snapUp(v: Float) = kotlin.math.ceil(v / GRID - 0.001f) * GRID

        /**
         * Resolves overlaps after [anchor] was moved or resized: the anchor stays exactly where it is;
         * every other widget, top to bottom, keeps its spot unless it hits something already placed,
         * in which case it slides straight down just below it. Order and horizontal positions are kept.
         */
        fun settle(list: List<Placement>, anchor: Int): List<Placement> {
            if (anchor !in list.indices) return list
            val out = list.toMutableList()
            val placed = mutableListOf(list[anchor])
            val order = list.indices.filter { it != anchor }.sortedWith(compareBy({ list[it].y }, { list[it].x }))
            for (i in order) {
                var p = list[i]
                while (true) {
                    val hits = placed.filter { it.overlaps(p) }
                    if (hits.isEmpty()) break
                    p = p.copy(y = snapUp(hits.maxOf { it.bottom } + GRID))
                }
                placed += p
                out[i] = p
            }
            return out
        }

        fun defaultWidth(t: WidgetType) = when {
            t.isFullDash -> 1f
            t.span == 2 -> 0.64f
            else -> 0.32f
        }
    }
}

/**
 * Pages of freely placed widgets.
 * Encoded as `SPEEDO@0,0,0.32;CLOCK@0.333,0,0.32|MEDIA@...` (pages split by `|`).
 * The older list format (`SPEEDO,CLOCK|MEDIA`) is still read and auto-arranged.
 */
data class DashboardLayout(val pages: List<List<Placement>>) {

    fun encode(): String = pages.joinToString("|") { page -> page.joinToString(";") { it.encode() } }

    fun withPage(index: Int, widgets: List<Placement>): DashboardLayout =
        copy(pages = pages.mapIndexed { i, p -> if (i == index) widgets else p })

    fun addPage(): DashboardLayout = copy(pages = pages + listOf(emptyList()))

    fun removePage(index: Int): DashboardLayout =
        if (pages.size <= 1) this else copy(pages = pages.filterIndexed { i, _ -> i != index })

    /** Adds a new page holding just [type] at full width — the biggest it can be while keeping its shape. */
    fun addFullPage(type: WidgetType): DashboardLayout =
        copy(pages = pages.filter { it.isNotEmpty() } + listOf(listOf(Placement(type, 0f, 0f, 1f))))

    /** Adds a widget below everything else on the page, at its default size. */
    fun addWidget(page: Int, type: WidgetType): DashboardLayout {
        val list = pages[page]
        val y = list.maxOfOrNull { it.bottom }?.let { Placement.snap(it + GAP) } ?: 0f
        return withPage(page, list + Placement(type, 0f, y, Placement.defaultWidth(type)))
    }

    /**
     * Drops widget [position] at [p]; anything it now overlaps shuffles down (cascading), and the
     * dropped widget is drawn on top.
     */
    fun place(page: Int, position: Int, p: Placement): DashboardLayout {
        val list = pages[page].toMutableList().also { it[position] = p }
        val settled = Placement.settle(list, position)
        return withPage(page, settled).moveWidget(page, position, settled.lastIndex)
    }

    fun removeWidget(page: Int, position: Int) =
        withPage(page, pages[page].filterIndexed { i, _ -> i != position })

    fun update(page: Int, position: Int, p: Placement) =
        withPage(page, pages[page].mapIndexed { i, old -> if (i == position) p else old })

    /** Reorders drawing order (later = on top) — used by the list editor's up/down buttons. */
    fun moveWidget(page: Int, from: Int, to: Int): DashboardLayout {
        val list = pages[page].toMutableList()
        if (from !in list.indices || to !in list.indices) return this
        list.add(to, list.removeAt(from))
        return withPage(page, list)
    }

    fun types(page: Int): List<WidgetType> = pages.getOrElse(page) { emptyList() }.map { it.type }

    companion object {
        const val GAP = 1f / 48f

        fun decode(text: String): DashboardLayout {
            val pages = text.split('|').map { page ->
                if ('@' in page) {
                    page.split(';').mapNotNull { item ->
                        val (name, nums) = item.split('@').takeIf { it.size == 2 } ?: return@mapNotNull null
                        val type = WidgetType.entries.firstOrNull { it.name == name.trim() } ?: return@mapNotNull null
                        val v = nums.split(',').mapNotNull { it.trim().toFloatOrNull() }
                        if (v.size != 3) null else Placement(type, v[0], v[1], v[2].coerceIn(Placement.MIN_W, 1f))
                    }
                } else {
                    flow(page.split(',').mapNotNull { n -> WidgetType.entries.firstOrNull { it.name == n.trim() } })
                }
            }
            return if (pages.isEmpty()) Presets.CLASSIC.layout else DashboardLayout(pages)
        }

        /** Arranges widgets left-to-right, top-to-bottom at their default sizes. */
        fun flow(types: List<WidgetType>): List<Placement> {
            val out = mutableListOf<Placement>()
            var x = 0f
            var y = 0f
            var rowH = 0f
            for (t in types) {
                val w = Placement.defaultWidth(t)
                if (x > 0f && x + w > 1.001f) { x = 0f; y += rowH + GAP; rowH = 0f }
                val p = Placement(t, Placement.snap(x), Placement.snap(y), w)
                out += p
                x += w + GAP
                rowH = maxOf(rowH, p.h)
            }
            return out
        }

        fun of(vararg pages: List<WidgetType>) = DashboardLayout(pages.map { flow(it) })
    }
}

enum class Presets(val title: String, val description: String, val layout: DashboardLayout) {
    CLASSIC(
        "Classic", "Speed, clock, music and navigation",
        DashboardLayout.of(
            listOf(WidgetType.SPEEDO, WidgetType.CLOCK, WidgetType.WEATHER, WidgetType.MEDIA, WidgetType.NAVIGATION),
            listOf(WidgetType.TRIP, WidgetType.COMPASS, WidgetType.VOLUME, WidgetType.APP_SHORTCUTS, WidgetType.AMBIENT),
        )
    ),
    PERFORMANCE(
        "Performance", "Engine data first",
        DashboardLayout.of(
            listOf(WidgetType.TACHO, WidgetType.SPEEDO, WidgetType.OBD_SINGLE, WidgetType.OBD_QUAD, WidgetType.DTC),
            listOf(WidgetType.RPM_BAR, WidgetType.TRIP, WidgetType.MEDIA),
        )
    ),
    DIGITAL(
        "Digital dash", "Twin-gauge cluster and twin-dial dash, one per page",
        DashboardLayout.of(
            listOf(WidgetType.CLUSTER_DASH, WidgetType.MEDIA, WidgetType.CLOCK),
            listOf(WidgetType.TWIN_DASH, WidgetType.MEDIA, WidgetType.WEATHER),
        )
    ),
    MINIMAL(
        "Minimal HUD", "Big speed, nothing else",
        DashboardLayout(listOf(listOf(Placement(WidgetType.SPEEDO, 0.125f, 0f, 0.75f))))
    ),
    MEDIA(
        "Media", "Music and apps up front",
        DashboardLayout.of(
            listOf(WidgetType.MEDIA, WidgetType.CLOCK, WidgetType.APP_SHORTCUTS, WidgetType.VOLUME),
            listOf(WidgetType.WEATHER, WidgetType.NOTE, WidgetType.AMBIENT),
        )
    ),
}
