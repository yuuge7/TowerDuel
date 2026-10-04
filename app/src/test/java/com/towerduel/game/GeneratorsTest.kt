package com.towerduel.game

import com.towerduel.game.data.GameData
import com.towerduel.game.engine.MapGenerator
import com.towerduel.game.engine.WaveGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The random parts of a match must stay fair however the dice fall. */
class GeneratorsTest {

    @Test
    fun namedMapsAndTheirMirrorsArePlayable() {
        for (map in GameData.MAPS) {
            assertNull("${map.name}", MapGenerator.problemWith(map.pathPoints))
            assertNull("${map.name} mirrored", MapGenerator.problemWith(MapGenerator.flipped(map).pathPoints))
        }
    }

    @Test
    fun generatedMapsArePlayable_andTheGeneratorRarelyGivesUp() {
        var made = 0
        val names = HashSet<String>()
        for (seed in 1..200) {
            val map = MapGenerator.generate(Random(seed)) ?: continue
            made++
            names.add(map.name)
            assertTrue("seed $seed made an unplayable map", MapGenerator.isPlayable(map.pathPoints))
            assertEquals("a wild map must start off the left edge", -8f, map.pathPoints.first().first)
        }
        println("generated maps: $made of 200, ${names.size} different names")
        assertTrue("the generator gave up too often: $made of 200", made >= 190)
    }

    @Test
    fun rostersAlwaysHaveTheBasicsAndAFinisher() {
        for (seed in 1..100) {
            val roster = GameData.randomRoster(Random(seed))
            assertEquals(GameData.ROSTER_SIZE, roster.size)
            assertEquals("no unit twice", roster.size, roster.toSet().size)
            assertTrue(roster.any { it.id == "runner" } && roster.any { it.id == "grunt" })
            assertEquals("exactly one of the two finishers", 1, roster.count { it.id == "boss" || it.id == "juggernaut" })
            assertTrue(roster.all { it.sendable })
        }
    }

    @Test
    fun wavesUseOnlyUnlockedRosterUnits_andStayNearTheirBudget() {
        for (seed in 1..60) {
            val rng = Random(seed)
            val roster = GameData.randomRoster(rng)
            for (level in 1..GameData.WAVE_LEVELS) {
                val wave = WaveGenerator.generate(level, roster, rng)
                assertTrue("empty wave at level $level", wave.groups.isNotEmpty())
                for (group in wave.groups) {
                    val unit = GameData.unit(group.unitId)
                    assertTrue("${unit.name} is not in the roster", unit in roster)
                    assertTrue("${unit.name} at level $level is not unlocked yet", unit.unlockRound <= level)
                    assertTrue(group.count >= 1)
                }
                val hp = WaveGenerator.totalHp(wave)
                val budget = WaveGenerator.budget(level)
                // Tough units (flyers, armour) count for more than their health, so a wave of them is lighter.
                assertTrue("level $level seed $seed: $hp health against a budget of $budget", hp in budget * 0.3f..budget * 1.6f)
            }
        }
    }

    @Test
    fun everyFifthLevelFromTheTenthIsABossRound() {
        for (seed in 1..30) {
            val rng = Random(seed)
            val roster = GameData.randomRoster(rng)
            for (level in listOf(10, 15, 20)) {
                val wave = WaveGenerator.generate(level, roster, rng)
                assertEquals("BOSS ROUND", wave.title)
                assertNotNull(wave.groups.firstOrNull { GameData.unit(it.unitId).maxHp >= 500f })
            }
        }
    }

    @Test
    fun aSecondRuleNeverContradictsTheFirst() {
        val rng = Random(3)
        var doubles = 0
        repeat(400) {
            val rules = GameData.randomRules(rng)
            assertTrue(rules.size in 1..2)
            if (rules.size == 2) {
                doubles++
                assertTrue("${rules[0].name} + ${rules[1].name}", rules[0].compatibleWith(rules[1]))
            }
        }
        assertTrue("two rules should come up now and then", doubles in 60..180)
    }
}
