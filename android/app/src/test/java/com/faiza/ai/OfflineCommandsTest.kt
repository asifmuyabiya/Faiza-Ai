package com.faiza.ai

import com.faiza.ai.ai.offline.OfflineCommands
import com.faiza.ai.ai.offline.OfflineCommands.Cmd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineCommandsTest {

    @Test fun banglaCommands() {
        assertEquals(Cmd.Time, OfflineCommands.parse("এখন কয়টা বাজে?"))
        assertEquals(Cmd.Torch(true), OfflineCommands.parse("টর্চ জ্বালাও"))
        assertEquals(Cmd.Torch(false), OfflineCommands.parse("টর্চ বন্ধ করো"))
        assertEquals(Cmd.Timer(300), OfflineCommands.parse("৫ মিনিটের টাইমার দাও"))
        assertEquals(Cmd.Timer(3600), OfflineCommands.parse("১ ঘণ্টার টাইমার"))
        assertEquals(Cmd.Alarm(6, 30), OfflineCommands.parse("সকাল ৬:৩০ এ অ্যালার্ম দাও"))
        assertEquals(Cmd.Alarm(22, 0), OfflineCommands.parse("রাত ১০টায় অ্যালার্ম"))
        assertEquals(Cmd.Alarm(2, 0), OfflineCommands.parse("রাত ২টায় অ্যালার্ম"))
        assertEquals(Cmd.Alarm(17, 30), OfflineCommands.parse("বিকাল সাড়ে ৫টায় অ্যালার্ম"))
        assertEquals(Cmd.Call("আম্মু"), OfflineCommands.parse("আম্মুকে কল দাও"))
        assertEquals(Cmd.OpenApp("ইউটিউব"), OfflineCommands.parse("ইউটিউব খোলো"))
        assertEquals(Cmd.Prayer, OfflineCommands.parse("আসরের আজান কখন?"))
        assertEquals(Cmd.Battery, OfflineCommands.parse("চার্জ কত আছে"))
    }

    @Test fun englishCommands() {
        assertEquals(Cmd.Timer(30), OfflineCommands.parse("set a 30 second timer"))
        assertEquals(Cmd.Alarm(7, 15), OfflineCommands.parse("alarm at 7:15 am"))
        assertEquals(Cmd.Call("mom"), OfflineCommands.parse("call mom"))
        assertEquals(Cmd.OpenApp("camera"), OfflineCommands.parse("open camera"))
        assertEquals(Cmd.Volume(80), OfflineCommands.parse("volume up"))
    }

    @Test fun otherThingsNeedTheAi() {
        assertNull(OfflineCommands.parse("আজকের খবর কী"))
        assertNull(OfflineCommands.parse("tell me a joke"))
    }
}
