package com.faiza.ai

import com.faiza.ai.ai.game.GameKnowledge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamingTest {

    @Test fun freeFireGetsItsOwnPack() {
        val ff = GameKnowledge.prompt("Free Fire", true, GameKnowledge.Talk.CHATTY, true, "বস")
        assertTrue(ff.contains("FREE FIRE MASTER COACH"))
        assertTrue(ff.contains("Gloo Wall:"))
        assertFalse(ff.contains("GAME UNDERSTANDING (any game)"))
        val bus = GameKnowledge.prompt("Bus Simulator", false, GameKnowledge.Talk.CHATTY, true, "বস")
        assertTrue(bus.contains("GAME UNDERSTANDING (any game)"))
        assertTrue(bus.contains("DRIVING"))
        assertFalse(bus.contains("FREE FIRE MASTER"))
    }

    @Test fun jokesAndTalkLevelsChangeThePrompt() {
        val fun_ = GameKnowledge.prompt("X", false, GameKnowledge.Talk.CHATTY, true, "বস")
        val serious = GameKnowledge.prompt("X", false, GameKnowledge.Talk.URGENT, false, "বস")
        assertTrue(fun_.contains("FUN (between calls"))
        assertFalse(serious.contains("FUN (between calls"))
        assertTrue(serious.contains("Speak ONLY for urgent"))
        assertTrue(fun_.contains("Call them \"বস\""))
    }

    @Test fun neverInventsEnemiesRuleIsAlwaysThere() {
        GameKnowledge.Talk.values().forEach { t ->
            val p = GameKnowledge.prompt("X", true, t, true, "Boss")
            assertTrue(p.contains("Never claim a hidden enemy as fact"))
            assertTrue(p.contains("CALL AHEAD"))
            assertTrue(p.contains("[GAME TICK]"))
        }
    }

    @Test fun talkLevelsAndPackages() {
        assertEquals(GameKnowledge.Talk.NORMAL, GameKnowledge.Talk.from("normal"))
        assertEquals(GameKnowledge.Talk.CHATTY, GameKnowledge.Talk.from(null))
        assertTrue(GameKnowledge.isFreeFire("com.dts.freefireth"))
        assertTrue(GameKnowledge.isFreeFire("com.dts.freefiremax"))
        assertFalse(GameKnowledge.isFreeFire("com.tencent.ig"))
        assertTrue(GameKnowledge.Talk.CHATTY.tickMs < GameKnowledge.Talk.NORMAL.tickMs)
    }

    @Test fun drivingGamesGetTheBusHelper() {
        assertTrue(GameKnowledge.isDriving("Bus Simulator Indonesia", "com.maleo.bussimulatorid"))
        assertFalse(GameKnowledge.isDriving("Free Fire", "com.dts.freefireth"))
        val bus = GameKnowledge.prompt("Bus Simulator Indonesia", false, GameKnowledge.Talk.CHATTY, true, "বস", driving = true)
        assertTrue(bus.contains("DRIVING COACH"))
        assertTrue(bus.contains("BUS HELPER STYLE"))
        val noJokes = GameKnowledge.prompt("Bus Simulator Indonesia", false, GameKnowledge.Talk.CHATTY, false, "বস", driving = true)
        assertFalse(noJokes.contains("BUS HELPER STYLE"))
    }

    @Test fun rawStringsHaveNoStrayBackslashQuotes() {
        val p = GameKnowledge.prompt("Free Fire", true, GameKnowledge.Talk.CHATTY, true, "বস")
        assertFalse(p.contains("\\\""))
    }

    @Test fun freeFireLookupFindsGunsCharactersAndCategories() {
        val ff = com.faiza.ai.ai.game.FreeFireData
        assertTrue(ff.lookup("MP40 vs UMP").contains("MP40"))
        assertTrue(ff.lookup("MP40 vs UMP").contains("UMP"))
        assertTrue(ff.lookup("m82b").contains("THROUGH gloo"))
        assertTrue(ff.lookup("কোন শটগান ভালো").contains("M1887"))
        assertTrue(ff.lookup("alok skill").contains("Drop the Beat"))
        assertTrue(ff.lookup("gloo wall").contains("Gloo Wall grenade"))
        assertTrue(ff.lookup("zzzz").startsWith("No exact entry"))
        assertTrue(ff.ENTRIES.size > 90)
    }

    @Test fun ownersBanterAndZoomAreInThePrompt() {
        val p = GameKnowledge.prompt("Free Fire", true, GameKnowledge.Talk.CHATTY, true, "বস")
        assertTrue(p.contains("আউকাত বাতাইয়া দেন"))
        assertTrue(p.contains("ZOOM PICTURE"))
        assertTrue(p.contains("ff_info"))
    }
}
