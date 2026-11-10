package com.vizionconcepts.drive

import com.vizionconcepts.drive.ambient.ElkBledom
import com.vizionconcepts.drive.dashboard.DashChannel
import com.vizionconcepts.drive.dashboard.DashboardLayout
import com.vizionconcepts.drive.dashboard.Placement
import com.vizionconcepts.drive.dashboard.Presets
import com.vizionconcepts.drive.dashboard.WidgetType
import com.vizionconcepts.drive.obd.ElmParser
import com.vizionconcepts.drive.obd.ObdPid
import com.vizionconcepts.drive.system.SpeedVolume
import com.vizionconcepts.drive.vehicle.TripComputer
import com.vizionconcepts.drive.weather.WeatherNow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdTest {
    @Test fun rpmDecodes() {
        val data = ElmParser.mode01Data("41 0C 1A F8\r\r", 0x0C)!!
        assertEquals(1726f, ObdPid.RPM.decode(data)!!, 0.01f)
    }

    @Test fun spacesOffAndEchoAreHandled() {
        val data = ElmParser.mode01Data("010D\r410D3C", 0x0D)!!
        assertEquals(60f, ObdPid.SPEED.decode(data)!!, 0f)
    }

    @Test fun coolantOffsetIsMinus40() {
        assertEquals(90f, ObdPid.COOLANT.decode(intArrayOf(130))!!, 0f)
    }

    @Test fun voltageIsMillivolts() {
        assertEquals(14.2f, ObdPid.VOLTAGE.decode(intArrayOf(0x37, 0x78))!!, 0.001f)
    }

    @Test fun noDataReturnsNull() {
        assertNull(ElmParser.mode01Data("NO DATA", 0x0C))
        assertNull(ElmParser.mode01Data("SEARCHING...\rUNABLE TO CONNECT", 0x0C))
    }

    @Test fun multiEcuTakesFirstMatchingLine() {
        val data = ElmParser.mode01Data("41 05 7B\r41 05 7C", 0x05)!!
        assertEquals(83f, ObdPid.COOLANT.decode(data)!!, 0f)
    }

    @Test fun requestFormat() {
        assertEquals("010C", ObdPid.RPM.request)
        assertEquals("0142", ObdPid.VOLTAGE.request)
    }

    @Test fun dtcCanFormatWithCountByte() {
        assertEquals(listOf("P0301", "P0420"), ElmParser.dtcs("43 02 03 01 04 20"))
    }

    @Test fun dtcLegacyFormatPaddedWithZeros() {
        assertEquals(listOf("P0301", "P0420"), ElmParser.dtcs("43 03 01 04 20 00 00"))
    }

    @Test fun dtcNoneOrNoData() {
        assertTrue(ElmParser.dtcs("43 00").isEmpty())
        assertTrue(ElmParser.dtcs("NO DATA").isEmpty())
    }

    @Test fun dtcSystemLetters() {
        assertEquals("P0171", ElmParser.decodeDtc(0x01, 0x71))
        assertEquals("C1234", ElmParser.decodeDtc(0x52, 0x34))
        assertEquals("B0001", ElmParser.decodeDtc(0x80, 0x01))
        assertEquals("U0100", ElmParser.decodeDtc(0xC1, 0x00))
        assertEquals("Network", ElmParser.systemName("U0100"))
    }

    @Test fun batteryVoltageFromAtrv() {
        assertEquals(12.6f, ElmParser.voltage("12.6V")!!, 0.001f)
    }
}

class AmbientTest {
    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it) }

    @Test fun colourFrame() {
        assertEquals("7E 00 05 03 FF 3B 30 00 EF", hex(ElkBledom.color(255, 59, 48)))
    }

    @Test fun powerFrames() {
        assertEquals("7E 00 04 F0 00 01 FF 00 EF", hex(ElkBledom.power(true)))
        assertEquals("7E 00 04 00 00 00 FF 00 EF", hex(ElkBledom.power(false)))
    }

    @Test fun brightnessClamped() {
        assertEquals(100, ElkBledom.brightness(250)[3].toInt())
        assertEquals(0, ElkBledom.brightness(-5)[3].toInt())
    }

    @Test fun rgbFromArgb() {
        assertEquals(Triple(0x0A, 0x84, 0xFF), ElkBledom.rgbOf(0xFF0A84FF))
    }

    @Test fun recognisesControllers() {
        assertTrue(ElkBledom.looksLikeController("ELK-BLEDOM0A"))
        assertTrue(!ElkBledom.looksLikeController("JBL Flip 5"))
    }

    @Test fun framesAreNineBytes() {
        listOf(ElkBledom.power(true), ElkBledom.color(1, 2, 3), ElkBledom.brightness(50), ElkBledom.effect(3), ElkBledom.effectSpeed(40))
            .forEach { assertEquals(9, it.size); assertEquals(0x7E.toByte(), it[0]); assertEquals(0xEF.toByte(), it[8]) }
    }
}

class DashboardTest {
    @Test fun roundTrip() {
        for (p in Presets.entries) {
            val back = DashboardLayout.decode(p.layout.encode())
            assertEquals(p.layout.pages.size, back.pages.size)
            p.layout.pages.zip(back.pages).forEach { (a, b) ->
                assertEquals(a.map { it.type }, b.map { it.type })
                a.zip(b).forEach { (x, y) -> assertEquals(x.x, y.x, 0.001f); assertEquals(x.y, y.y, 0.001f); assertEquals(x.w, y.w, 0.001f) }
            }
        }
    }

    @Test fun oldListFormatIsMigrated() {
        val l = DashboardLayout.decode("SPEED_ARC,NOT_A_WIDGET,CLOCK|MEDIA")
        assertEquals(listOf(WidgetType.SPEED_ARC, WidgetType.CLOCK), l.types(0))
        assertEquals(listOf(WidgetType.MEDIA), l.types(1))
        // flowed side by side on the first row
        assertEquals(0f, l.pages[0][0].x, 0f)
        assertTrue(l.pages[0][1].x > l.pages[0][0].x)
        assertEquals(l.pages[0][0].y, l.pages[0][1].y, 0f)
    }

    @Test fun flowWrapsWhenRowIsFull() {
        val f = DashboardLayout.flow(listOf(WidgetType.SPEEDO, WidgetType.CLOCK, WidgetType.MEDIA))
        assertTrue(f[2].y > f[0].y) // 0.32 + 0.32 + 0.64 > 1 → media wraps
        assertEquals(0f, f[2].x, 0f)
    }

    @Test fun resizeKeepsProportionAndStaysOnPage() {
        val p = Placement(WidgetType.SPEEDO, 0.5f, 0f, 0.3f)
        val big = p.resized(0.9f)
        assertEquals(big.w, big.h / WidgetType.SPEEDO.aspect, 0.0001f)
        assertTrue(big.x + big.w <= 1.0001f)
        assertTrue(p.resized(0.01f).w >= Placement.MIN_W)
    }

    @Test fun moveSnapsAndClamps() {
        val p = Placement(WidgetType.CLOCK, 0f, 0f, 0.32f)
        val m = p.moved(0.9f, -0.2f)
        assertEquals(1f - 0.32f, m.x, 0.03f)
        assertTrue(m.x + m.w <= 1.0001f)
        assertEquals(0f, m.y, 0f)
        val s = p.moved(0.1013f, 0.2f)
        assertEquals(0f, (s.x / Placement.GRID) - Math.round(s.x / Placement.GRID), 0.0001f)
    }

    @Test fun editing() {
        var l = DashboardLayout.of(listOf(WidgetType.CLOCK, WidgetType.WEATHER))
        l = l.addPage().addWidget(1, WidgetType.MEDIA)
        assertEquals(2, l.pages.size)
        l = l.addWidget(0, WidgetType.NOTE)
        val note = l.pages[0].last()
        assertTrue(note.y >= l.pages[0].take(2).maxOf { it.bottom }) // added below existing widgets
        l = l.removeWidget(0, 0).removePage(1)
        assertEquals(listOf(WidgetType.WEATHER, WidgetType.NOTE), l.types(0))
        assertEquals(l, l.removePage(0)) // last page can't be removed
        assertEquals(l, l.moveWidget(0, 0, 5)) // out of range is a no-op
    }
}

class TripTest {
    @Test fun haversineKnownDistance() {
        // Melbourne CBD to Pakenham ≈ 51 km in a straight line.
        val d = TripComputer.haversine(-37.8136, 144.9631, -38.0702, 145.4874)
        assertTrue("was $d", d in 50_000.0..56_000.0)
    }

    @Test fun accumulatesWhileMovingOnly() {
        val t = TripComputer()
        t.onFix(-38.0, 145.0, 0f, 0)
        t.onFix(-38.0, 145.0001, 0f, 1000)      // stationary jitter: ignored
        assertEquals(0.0, t.distanceM, 0.0)
        t.onFix(-38.0, 145.0011, 25f, 4000)     // ~88 m in 3 s while moving
        assertTrue(t.distanceM in 80.0..95.0)
        assertEquals(3000L, t.movingMs)
        assertEquals(25f, t.maxMps, 0f)
    }

    @Test fun rejectsTeleports() {
        val t = TripComputer()
        t.onFix(-38.0, 145.0, 20f, 0)
        t.onFix(-37.0, 145.0, 20f, 1000)        // 111 km in 1 s
        assertEquals(0.0, t.distanceM, 0.0)
    }

    @Test fun cardinals() {
        assertEquals("N", TripComputer.cardinal(359f))
        assertEquals("E", TripComputer.cardinal(91f))
        assertEquals("SW", TripComputer.cardinal(225f))
        assertEquals("N", TripComputer.cardinal(-10f))
    }
}

class MiscTest {
    @Test fun speedVolumeSteps() {
        assertEquals(0, SpeedVolume.stepsFor(30f))
        assertEquals(1, SpeedVolume.stepsFor(50f))
        assertEquals(2, SpeedVolume.stepsFor(75f))
        assertEquals(4, SpeedVolume.stepsFor(200f))
    }

    @Test fun weatherCodes() {
        assertEquals("Clear", WeatherNow.describe(0))
        assertEquals("Rain", WeatherNow.describe(63))
        assertEquals("Thunderstorm", WeatherNow.describe(95))
    }

    @Test fun frameBytes() {
        assertArrayEquals(byteArrayOf(0x7E, 0, 1, 50, 0, 0, 0, 0, 0xEF.toByte()), ElkBledom.brightness(50))
    }
}

class DigitalDashTest {
    @Test fun boostFromManifoldPressure() {
        assertEquals(0f, com.vizionconcepts.drive.dashboard.DashMath.boostPsi(101.325f), 0.01f)
        assertEquals(14.5f, com.vizionconcepts.drive.dashboard.DashMath.boostPsi(201.325f), 0.05f)
        assertTrue(com.vizionconcepts.drive.dashboard.DashMath.boostPsi(30f) < -10f)
    }

    @Test fun segmentsAndFractionsClamp() {
        val m = com.vizionconcepts.drive.dashboard.DashMath
        assertEquals(0, m.segmentsLit(-1f, 10))
        assertEquals(5, m.segmentsLit(0.5f, 10))
        assertEquals(10, m.segmentsLit(3f, 10))
        assertEquals(0.5f, m.fraction(80f, 40f, 120f), 0.001f)
        assertEquals(1f, m.fraction(500f, 40f, 120f), 0.001f)
        assertEquals(0f, m.fraction(5f, 10f, 10f), 0.001f)
    }

    @Test fun clockHands() {
        val (h, m, s) = com.vizionconcepts.drive.dashboard.DashMath.handAngles(15, 30, 0)
        assertEquals(105f, h, 0.01f)
        assertEquals(180f, m, 0.01f)
        assertEquals(0f, s, 0.01f)
    }

    @Test fun tachoScaleCoversShiftPoint() {
        val m = com.vizionconcepts.drive.dashboard.DashMath
        assertEquals(8, m.tachoMax(6000))
        assertEquals(9, m.tachoMax(7500))
        assertTrue(m.tachoMax(7500) * 1000 > 7500)
    }

    @Test fun channelsReadCarDataAndShowDashesWhenMissing() {
        val empty = com.vizionconcepts.drive.obd.ObdSnapshot()
        assertEquals("--", DashChannel.COOLANT.text(empty))
        assertEquals(0f, DashChannel.COOLANT.fraction(empty), 0f)
        val snap = com.vizionconcepts.drive.obd.ObdSnapshot(values = mapOf(
            com.vizionconcepts.drive.obd.ObdPid.COOLANT to 90.4f,
            com.vizionconcepts.drive.obd.ObdPid.AMBIENT to 21f,
        ))
        assertEquals("90", DashChannel.COOLANT.text(snap))
        assertEquals("21", DashChannel.OUTSIDE.text(snap))
        assertEquals("12.3", DashChannel.TRIP.text(snap, com.vizionconcepts.drive.vehicle.GpsState(tripKm = 12.34)))
        assertEquals(2, DashChannel.LEFT.size); assertEquals(2, DashChannel.RIGHT.size)
    }

    @Test fun onlyHeadUnitReadableValues() {
        // Engine-computer-only values must not appear on the dashes.
        val names = DashChannel.entries.map { it.name }.toSet()
        assertEquals(setOf("COOLANT", "FUEL", "OUTSIDE", "TRIP"), names)
    }

    @Test fun fullPageDashUsesWholeWidth() {
        val l = DashboardLayout.of(listOf(WidgetType.CLOCK)).addFullPage(WidgetType.TWIN_DASH)
        assertEquals(2, l.pages.size)
        val p = l.pages[1].single()
        assertEquals(WidgetType.TWIN_DASH, p.type)
        assertEquals(0f, p.x, 0f); assertEquals(0f, p.y, 0f); assertEquals(1f, p.w, 0f)
        assertEquals(0.56f, p.h, 0.001f)
    }

    @Test fun dashesStartFullWidthAndStayProportional() {
        val l = DashboardLayout(listOf(emptyList())).addWidget(0, WidgetType.RACE_DASH)
        val p = l.pages[0][0]
        assertEquals(1f, p.w, 0.001f)
        assertEquals(0.6f, p.h, 0.001f)
        val smaller = p.resized(0.5f)
        assertEquals(0.3f, smaller.h, 0.01f)
    }
}

