package com.towerduel.game

import com.towerduel.game.data.Difficulty
import com.towerduel.game.data.GameData
import com.towerduel.game.ui.Cup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The cup's bracket: who meets whom, who goes through, and what survives being saved. */
class CupTest {

    @Test
    fun aFreshCupHoldsThePlayerAndSevenDifferentRivals() {
        for (seed in 1..50) {
            val cup = Cup.start(Difficulty.MEDIUM, Random(seed))
            assertEquals(8, cup.entrants.size)
            assertEquals("nobody twice", 8, cup.entrants.toSet().size)
            assertEquals(1, cup.entrants.count { it == Cup.PLAYER })
            assertTrue(cup.entrants.all { id -> id == Cup.PLAYER || GameData.RIVALS.any { it.id == id } })
            assertEquals(0, cup.round)
            assertFalse(cup.isOver)
            // The first opponent is the player's neighbour in the bracket.
            val slot = cup.entrants.indexOf(Cup.PLAYER)
            assertEquals(cup.entrants[slot xor 1], cup.opponentId)
        }
    }

    @Test
    fun threeWinsTakeTheCup_andTheFinalIsOneStepHarder() {
        for (seed in 1..50) {
            val rng = Random(seed)
            var cup = Cup.start(Difficulty.MEDIUM, rng)
            val met = HashSet<String>()
            for (round in 0 until Cup.ROUNDS) {
                assertEquals(round, cup.round)
                assertEquals(if (round == Cup.FINAL) Difficulty.HARD else Difficulty.MEDIUM, cup.matchDifficulty)
                assertTrue("never the same rival twice", met.add(cup.opponentId!!))
                cup = cup.afterPlayerMatch(won = true, rng)
                // Half the field goes out every round, and whoever goes through was in it.
                assertEquals(8 shr (round + 1), cup.alive.size)
            }
            assertTrue(cup.playerWon)
            assertTrue(cup.isOver)
            assertNull(cup.opponentId)
            assertNull(cup.knockedOutIn)
        }
        assertEquals(Difficulty.HARD, Cup.start(Difficulty.HARD, Random(1)).copy(rounds = listOf(listOf(), listOf())).matchDifficulty)
    }

    @Test
    fun aLossEndsTheCup_andTheBracketStillFindsAChampion() {
        for (seed in 1..50) {
            val rng = Random(seed)
            var cup = Cup.start(Difficulty.EASY, rng)
            val wins = seed % 3
            repeat(wins) { cup = cup.afterPlayerMatch(won = true, rng) }
            val beatenBy = cup.opponentId
            cup = cup.afterPlayerMatch(won = false, rng)

            assertTrue(cup.isOver)
            assertFalse(cup.playerWon)
            assertEquals(wins, cup.knockedOutIn)
            assertEquals("every round is played out", Cup.ROUNDS, cup.rounds.size)
            assertNotNull(cup.champion)
            assertTrue(cup.champion != Cup.PLAYER)
            assertTrue("the rival who won went through", beatenBy in cup.rounds[wins])
            // Nothing more can happen to a finished cup.
            assertEquals(cup, cup.afterPlayerMatch(won = true, rng))
        }
    }

    @Test
    fun aSavedCupReadsBackTheSame_andABrokenOneIsDropped() {
        val rng = Random(11)
        var cup = Cup.start(Difficulty.HARD, rng)
        assertEquals(cup, Cup.decode(cup.encode()))
        cup = cup.afterPlayerMatch(won = true, rng)
        assertEquals(cup, Cup.decode(cup.encode()))
        cup = cup.afterPlayerMatch(won = false, rng)
        assertEquals(cup, Cup.decode(cup.encode()))

        assertNull(Cup.decode(null))
        assertNull(Cup.decode(""))
        assertNull(Cup.decode("cup1|MEDIUM|you,dash|"))
        assertNull("an unknown rival", Cup.decode(cup.encode().replace(cup.entrants.first { it != Cup.PLAYER }, "nobody")))
        assertNull("an unknown difficulty", Cup.decode(cup.encode().replace("HARD", "NIGHTMARE")))
        // A winner who was not in that match cannot have gone through.
        val fresh = Cup.start(Difficulty.EASY, Random(5))
        val impostors = fresh.entrants.take(2) + fresh.entrants.take(2)
        assertNull(Cup.decode(fresh.copy(rounds = listOf(impostors)).encode()))
    }
}
