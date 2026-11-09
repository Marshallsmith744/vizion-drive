package com.vizionconcepts.drive

import com.vizionconcepts.drive.ambient.ElkBledom
import com.vizionconcepts.drive.dashboard.DashboardLayout
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
        for (p in Presets.entries) assertEquals(p.layout, DashboardLayout.decode(p.layout.encode()))
    }

    @Test fun unknownWidgetsAreDropped() {
        val l = DashboardLayout.decode("SPEED_ARC,NOT_A_WIDGET,CLOCK|MEDIA")
        assertEquals(listOf(listOf(WidgetType.SPEED_ARC, WidgetType.CLOCK), listOf(WidgetType.MEDIA)), l.pages)
    }

    @Test fun rowsRespectTwoColumns() {
        val rows = DashboardLayout.rows(listOf(WidgetType.SPEED_ARC, WidgetType.MEDIA, WidgetType.CLOCK, WidgetType.WEATHER, WidgetType.TRIP))
        assertEquals(listOf(listOf(WidgetType.SPEED_ARC), listOf(WidgetType.MEDIA), listOf(WidgetType.CLOCK, WidgetType.WEATHER), listOf(WidgetType.TRIP)), rows)
    }

    @Test fun editing() {
        var l = DashboardLayout(listOf(listOf(WidgetType.CLOCK, WidgetType.WEATHER)))
        l = l.moveWidget(0, 1, 0)
        assertEquals(listOf(WidgetType.WEATHER, WidgetType.CLOCK), l.pages[0])
        l = l.addPage().addWidget(1, WidgetType.MEDIA)
        assertEquals(2, l.pages.size)
        l = l.removeWidget(0, 0).removePage(1)
        assertEquals(listOf(listOf(WidgetType.CLOCK)), l.pages)
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
