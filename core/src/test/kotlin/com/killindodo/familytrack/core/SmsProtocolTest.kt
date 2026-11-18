package com.killindodo.familytrack.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsProtocolTest {

    @Test
    fun roundTripFix() {
        val m = FtMessage.FixMsg(1710000000000L, 12.9716, 77.5946, 8.0, 1.5, 87, 1710000000000L)
        assertEquals(m, SmsProtocol.parse(SmsProtocol.encode(m)))
    }

    @Test
    fun roundTripCheck() {
        val m = FtMessage.CheckMsg(1710000000000L, 1710000000000L)
        assertEquals(m, SmsProtocol.parse(SmsProtocol.encode(m)))
    }

    @Test
    fun roundTripSosWithoutCoords() {
        val m = FtMessage.SosMsg(null, null, null, 42, 1710000000000L)
        assertEquals(m, SmsProtocol.parse(SmsProtocol.encode(m)))
    }

    @Test
    fun roundTripSosWithCoords() {
        val m = FtMessage.SosMsg(12.9716, 77.5946, 8.0, 87, 1710000000000L)
        assertEquals(m, SmsProtocol.parse(SmsProtocol.encode(m)))
    }

    @Test
    fun roundTripPair() {
        val m = FtMessage.PairMsg("482913", "Ramesh Uncle")
        assertEquals(m, SmsProtocol.parse(SmsProtocol.encode(m)))
    }

    @Test
    fun roundTripGeof() {
        val m = FtMessage.GeofMsg("home", 12.9716, 77.5946, 100.0, "Home sweet home")
        assertEquals(m, SmsProtocol.parse(SmsProtocol.encode(m)))
    }

    @Test
    fun roundTripGeofAck() {
        val m = FtMessage.GeofAckMsg("home", 66, 1710000000000L)
        assertEquals(m, SmsProtocol.parse(SmsProtocol.encode(m)))
    }

    @Test
    fun roundTripGeofEvt() {
        val m = FtMessage.GeofEvtMsg("home", GeofDirection.OUT, 12.9716, 77.5946, 66, 1710000000000L)
        assertEquals(m, SmsProtocol.parse(SmsProtocol.encode(m)))
    }

    @Test
    fun roundTripNoFix() {
        val m = FtMessage.NoFixMsg(1710000000000L, FixReason.GPS_OFF, 50, 1710000000000L)
        assertEquals(m, SmsProtocol.parse(SmsProtocol.encode(m)))
    }

    @Test
    fun roundTripLowBat() {
        val m = FtMessage.LowBatMsg(12, 1710000000000L)
        assertEquals(m, SmsProtocol.parse(SmsProtocol.encode(m)))
    }

    @Test
    fun garbageIsNullNeverCrashes() {
        val junk = listOf(null, "", "   ", "hello world", "FT1|WHAT|1", "FT1|CHECK|1", "FT1|FIX|1|12.0", "FT1|SOS|abc|def|ghi|jkl|mn")
        junk.forEach { assertNull(SmsProtocol.parse(it)) }
    }

    @Test
    fun wrongVersionIsNull() {
        assertNull(SmsProtocol.parse("FT9|CHECK|1|2"))
    }

    @Test
    fun encodeStripsPipesFromFreeText() {
        val encoded = SmsProtocol.encode(FtMessage.PairMsg("123456", "A|B"))
        assertTrue(encoded.contains("A B"))
    }

    @Test
    fun sosFitsInOneSmsPart() {
        val s = SmsProtocol.encode(FtMessage.SosMsg(12.9716, 77.5946, 8.0, 87, 1710000000000L))
        assertTrue("length ${s.length}", s.length <= 160)
    }

    @Test
    fun fixFitsInOneSmsPart() {
        val s = SmsProtocol.encode(FtMessage.FixMsg(1710000000000L, -12.9716, 77.5946, 8.0, 1.5, 87, 1710000000000L))
        assertTrue("length ${s.length}", s.length <= 160)
    }

    @Test
    fun geofFitsInOneSmsPart() {
        val s = SmsProtocol.encode(FtMessage.GeofMsg("home", 12.9716, 77.5946, 100.0, "Home"))
        assertTrue("length ${s.length}", s.length <= 160)
    }
}
