package com.zepinto.codenames

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val A = DuetCard.AGENT
private val B = DuetCard.BYSTANDER
private val X = DuetCard.ASSASSIN

/**
 * A Duet game with a known layout, written as (key of player 0 / key of player 1):
 * 0-2 A/A, 3-7 A/B, 8 A/X, 9-13 B/A, 14 X/A, 15-21 B/B, 22 B/X, 23 X/B, 24 X/X.
 */
private fun duet(giver: Int = 0, tokens: Int = 9): DuetState {
    val g = DuetEngine.newGame(Lang.EN, (1..25).map { "word$it" }, tokens, 5L, Random(1))
    val key0 = listOf(A, A, A) + List(5) { A } + A + List(5) { B } + X + List(7) { B } + B + X + X
    val key1 = listOf(A, A, A) + List(5) { B } + X + List(5) { A } + A + List(7) { B } + X + B + X
    return g.copy(keys = listOf(key0, key1), giver = giver)
}

class DuetEngineTest {
    @Test
    fun aGameNeedsAtLeastOneTurn() {
        val words = (1..25).map { "w$it" }
        var refused = false
        try {
            DuetEngine.newGame(Lang.EN, words, 0, 1L, Random(1))
        } catch (_: IllegalArgumentException) {
            refused = true
        }
        assertTrue(refused)
    }

    @Test
    fun theLayoutIsTheOfficialOne() {
        repeat(60) { seed ->
            val g = DuetEngine.newGame(Lang.EN, (1..25).map { "w$it" }, 9, 1L, Random(seed))
            for (p in 0..1) {
                assertEquals(9, g.keys[p].count { it == A })
                assertEquals(3, g.keys[p].count { it == X })
                assertEquals(13, g.keys[p].count { it == B })
            }
            assertEquals("agents on both keys", 3, (0 until 25).count { g.keys[0][it] == A && g.keys[1][it] == A })
            assertEquals("assassin on both keys", 1, (0 until 25).count { g.keys[0][it] == X && g.keys[1][it] == X })
            assertEquals("agents on at least one key", 15, g.agentsTotal)
            assertEquals(9, g.tokens)
            assertEquals(0, g.agentsFound)
            assertTrue(g.giver in 0..1)
        }
    }

    @Test
    fun bothPlayersGetToGiveTheFirstClueSometimes() {
        val givers = (0 until 40).map { DuetEngine.newGame(Lang.EN, (1..25).map { "w$it" }, 9, 1L, Random(it)).giver }.toSet()
        assertEquals(setOf(0, 1), givers)
    }

    @Test
    fun anAgentOnTheGiversKeyIsFoundAndTheTurnGoesOn() {
        val g = DuetEngine.guess(duet(giver = 0), player = 1, index = 0)
        assertTrue(g.found[0])
        assertEquals(0, g.giver)
        assertEquals(9, g.tokens)
        assertEquals(1, g.guessesThisTurn)
        assertEquals(DuetLast(0, DuetResult.AGENT, 1), g.last)
    }

    @Test
    fun theGiversKeyDecidesNotTheGuessers() {
        // word 9 is a bystander on key 0 but an agent on key 1. Player 1 guessing during player 0's turn gets "bystander".
        val g = DuetEngine.guess(duet(giver = 0), 1, 9)
        assertFalse(g.found[9])
        assertTrue(g.tan[0][9])
        assertFalse(g.tan[1][9])
    }

    @Test
    fun aBystanderEndsTheTurnUsesATokenAndSwapsTheRoles() {
        val g = DuetEngine.guess(duet(giver = 0), 1, 9)
        assertEquals(1, g.giver)
        assertEquals(8, g.tokens)
        assertEquals(0, g.guessesThisTurn)
        assertEquals(DuetPhase.PLAYING, g.phase)
        // the word stays on the board: the other player can still find it, from their own key
        val next = DuetEngine.guess(g, 0, 9) // player 1 gives, player 0 guesses; word 9 is an agent on key 1
        assertTrue(next.found[9])
    }

    @Test
    fun anAssassinOnTheGiversKeyLosesAtOnce() {
        val g = DuetEngine.guess(duet(giver = 0), 1, 14)
        assertEquals(DuetPhase.LOST, g.phase)
        assertEquals(LossReason.ASSASSIN, g.lossReason)
        assertEquals(g, DuetEngine.guess(g, 1, 0)) // nothing happens after the end
        assertEquals(g, DuetEngine.pass(g, 1))
    }

    @Test
    fun onlyTheGuesserMayGuessAndNoWordTwice() {
        val g = duet(giver = 0)
        assertEquals(g, DuetEngine.guess(g, 0, 0)) // the giver may not
        val one = DuetEngine.guess(g, 1, 0)
        assertEquals(one, DuetEngine.guess(one, 1, 0)) // already found
        val tan = DuetEngine.guess(one, 1, 9) // bystander: turn ends, roles swap
        val back = tan.copy(giver = 0) // pretend it is player 0's clue again
        assertEquals(back, DuetEngine.guess(back, 1, 9)) // known bystander on that key: ignored, no token lost
        assertEquals(g, DuetEngine.guess(g, 1, -1))
        assertEquals(g, DuetEngine.guess(g, 1, 25))
    }

    @Test
    fun passingNeedsAtLeastOneGuessAndUsesAToken() {
        val g = duet(giver = 0)
        assertEquals(g, DuetEngine.pass(g, 1)) // no guess yet
        assertEquals(g, DuetEngine.pass(g, 0)) // the giver cannot pass
        val after = DuetEngine.pass(DuetEngine.guess(g, 1, 0), 1)
        assertEquals(1, after.giver)
        assertEquals(8, after.tokens)
        assertEquals(0, after.guessesThisTurn)
    }

    @Test
    fun whenTheTokensRunOutSuddenDeathBegins() {
        var g = duet(giver = 0, tokens = 1)
        g = DuetEngine.guess(g, 1, 9) // bystander on key 0: last token used
        assertEquals(0, g.tokens)
        assertEquals(DuetPhase.SUDDEN_DEATH, g.phase)
        assertEquals(g, DuetEngine.pass(g, 0)) // no passing in sudden death
    }

    @Test
    fun inSuddenDeathEitherPlayerGuessesAndTheOtherKeyJudges() {
        val sudden = duet(tokens = 1).copy(tokens = 0, phase = DuetPhase.SUDDEN_DEATH)
        // player 0 guesses word 9: agent on key 1, so correct
        val ok = DuetEngine.guess(sudden, 0, 9)
        assertTrue(ok.found[9])
        assertEquals(DuetPhase.SUDDEN_DEATH, ok.phase)
        // player 1 can guess at once, no turns: word 3 is an agent on key 0
        assertTrue(DuetEngine.guess(ok, 1, 3).found[3])
        // a bystander on the partner's key loses
        val lost = DuetEngine.guess(sudden, 0, 3) // word 3 is a bystander on key 1
        assertEquals(DuetPhase.LOST, lost.phase)
        assertEquals(LossReason.SUDDEN_DEATH, lost.lossReason)
        assertEquals(DuetResult.WRONG, lost.last!!.result)
        // an assassin on the partner's key loses too
        val dead = DuetEngine.guess(sudden, 0, 8) // word 8 is an assassin on key 1
        assertEquals(LossReason.ASSASSIN, dead.lossReason)
    }

    @Test
    fun findingAllFifteenAgentsWins() {
        var g = duet(giver = 0)
        for (i in 0..8) g = DuetEngine.guess(g, 1, i) // the 9 agents on key 0
        assertEquals(9, g.agentsFound)
        assertEquals(DuetPhase.PLAYING, g.phase)
        g = DuetEngine.pass(g, 1) // player 1 now gives the clues
        for (i in 9..13) g = DuetEngine.guess(g, 0, i)
        assertEquals(DuetPhase.PLAYING, g.phase)
        g = DuetEngine.guess(g, 0, 14) // the 15th
        assertEquals(15, g.agentsFound)
        assertEquals(DuetPhase.WON, g.phase)
        assertTrue(g.over)
    }

    @Test
    fun theLastAgentCanBeFoundInSuddenDeathToo() {
        var g = duet(giver = 0)
        for (i in 0..8) g = DuetEngine.guess(g, 1, i)
        g = DuetEngine.pass(g, 1)
        for (i in 9..13) g = DuetEngine.guess(g, 0, i)
        val sudden = g.copy(tokens = 0, phase = DuetPhase.SUDDEN_DEATH)
        assertEquals(DuetPhase.WON, DuetEngine.guess(sudden, 0, 14).phase)
    }

    @Test
    fun everyChangeBumpsTheVersion() {
        var g = duet()
        val seen = mutableListOf(g.seq)
        g = DuetEngine.guess(g, 1, 0); seen += g.seq
        g = DuetEngine.pass(g, 1); seen += g.seq
        g = DuetEngine.guess(g, 0, 9); seen += g.seq
        assertEquals(seen.sorted().distinct(), seen)
    }
}

class DuetViewTest {
    @Test
    fun aPlayerOnlySeesTheirOwnKeyUntilTheEnd() {
        val g = duet()
        val v0 = DuetView.of(g, 0)
        val v1 = DuetView.of(g, 1)
        assertEquals(g.keys[0], v0.myKey)
        assertEquals(g.keys[1], v1.myKey)
        assertNull(v0.partnerKey)
        assertNull(v1.partnerKey)
        val over = DuetEngine.guess(g, 1, 14)
        assertEquals(over.keys[1], DuetView.of(over, 0).partnerKey)
        assertEquals(over.keys[0], DuetView.of(over, 1).partnerKey)
    }

    @Test
    fun theViewSaysWhoGivesAndWhoGuesses() {
        val g = DuetEngine.guess(duet(giver = 0), 1, 0)
        assertTrue(DuetView.of(g, 1).iGuess)
        assertTrue(DuetView.of(g, 0).iGive)
        assertEquals(1, DuetView.of(g, 1).agentsFound)
        assertEquals(15, DuetView.of(g, 1).agentsTotal)
    }

    @Test
    fun noCountOfTheHostsHiddenAgentsIsEverSent() {
        // a count like that would tell the other player whether a word the host found is also an agent on the host's key
        assertFalse(DuetCodec.stateJson(duet(), 1).contains("toGuess"))
    }

    @Test
    fun theWireMessageForThePartnerHasNoTraceOfTheHostsKey() {
        val g = duet()
        val json = DuetCodec.stateJson(g, 1)
        assertFalse(json.contains("key0"))
        assertFalse(json.contains("\"partner\":["))
        val view = (Protocol.parse(json) as Message.DuetUpdate).view
        assertEquals(g.keys[1], view.myKey)
        assertNull(view.partnerKey)
    }

    @Test
    fun theViewSurvivesAJsonRoundTripInEveryPhase() {
        var g = duet(giver = 1)
        g = DuetEngine.guess(g, 0, 3)       // key 1 says bystander: marked, turn ends
        g = DuetEngine.guess(g, 1, 9)       // now player 0 gives: word 9 is a bystander on key 0 ... marked
        val sudden = g.copy(tokens = 0, phase = DuetPhase.SUDDEN_DEATH)
        val over = DuetEngine.guess(sudden, 0, 3)
        for (state in listOf(duet(), g, sudden, over)) for (p in 0..1) {
            val back = (Protocol.parse(DuetCodec.stateJson(state, p)) as Message.DuetUpdate).view
            assertEquals(DuetView.of(state, p), back)
        }
    }

    @Test
    fun aSavedGameComesBackExactly() {
        var g = DuetEngine.guess(duet(), 1, 0)
        g = DuetEngine.guess(g, 1, 9)
        assertEquals(g, DuetCodec.load(DuetCodec.save(g)))
        val over = DuetEngine.guess(g, 0, 22)
        assertEquals(over, DuetCodec.load(DuetCodec.save(over)))
        assertNull(DuetCodec.load("{}"))
        assertNull(DuetCodec.load("nope"))
    }

    @Test
    fun malformedDuetMessagesAreIgnored() {
        for (bad in listOf(
            "{\"t\":\"dstate\"}", "{\"t\":\"dstate\",\"s\":{}}", "{\"t\":\"dguess\"}",
            "{\"t\":\"dguess\",\"i\":3}", // no game or version: refused
            "{\"t\":\"dpass\"}", "{\"t\":\"dstate\",\"s\":{\"me\":5}}",
        )) {
            assertNull(bad, Protocol.parse(bad))
        }
        assertEquals(Message.DuetGuess(7, 5L, 3), Protocol.parse(Protocol.duetGuess(7, 5L, 3)))
        assertEquals(Message.DuetPass(5L, 3), Protocol.parse(Protocol.duetPass(5L, 3)))
    }
}

class DuetSoundTest {
    private fun snap(marks: Int, phase: DuetPhase = DuetPhase.PLAYING, last: DuetLast? = null, id: Long = 1) = DuetSnapshot(id, marks, phase, last)

    @Test
    fun theCueDependsOnWhatHappened() {
        val before = snap(0)
        assertEquals(SoundCue.GOOD, duetCueFor(before, snap(1, last = DuetLast(0, DuetResult.AGENT, 1))))
        assertEquals(SoundCue.NEUTRAL, duetCueFor(before, snap(1, last = DuetLast(9, DuetResult.BYSTANDER, 1))))
        assertEquals(SoundCue.ASSASSIN, duetCueFor(before, snap(0, DuetPhase.LOST, DuetLast(14, DuetResult.ASSASSIN, 1))))
        assertEquals(SoundCue.WIN, duetCueFor(before, snap(15, DuetPhase.WON, DuetLast(5, DuetResult.AGENT, 1))))
        assertNull(duetCueFor(null, snap(3)))
        assertNull(duetCueFor(before, before))
        assertNull(duetCueFor(snap(0), snap(1, last = DuetLast(0, DuetResult.AGENT, 0), id = 2)))
    }
}

class DuetSessionTest {
    private class Rig(game: DuetState) {
        val toGuest = mutableListOf<String>()
        val changes = mutableListOf<DuetState>()
        val host = DuetHostSession(game, { toGuest += it }, { changes += it })
        val guest = DuetClientSession()
        fun guestSends(text: String) = host.onMessage(text) { toGuest += it }

        /** The messages a guest phone would send while looking at the host's current position. */
        fun guessMsg(i: Int) = Protocol.duetGuess(i, host.game.gameId, host.game.seq)
        fun passMsg() = Protocol.duetPass(host.game.gameId, host.game.seq)
        fun deliver() = toGuest.toList().also { toGuest.clear() }.mapNotNull { guest.onMessage(it) }
    }

    @Test
    fun theGuestGuessesAndTheHostAnswers() {
        val r = Rig(duet(giver = 0))
        r.guestSends(Protocol.sync())
        assertEquals(0, r.deliver().last().view.agentsFound)

        r.guestSends(r.guessMsg(0))
        val update = r.deliver().last()
        assertEquals(SoundCue.GOOD, update.cue)
        assertTrue(update.view.found[0])
        assertEquals(1, update.view.me)
        assertEquals(1, r.changes.size)
    }

    @Test
    fun theGuestCannotGuessOutOfTurnOrPassEarly() {
        val r = Rig(duet(giver = 1)) // the guest gives, so the guest may not guess
        r.guestSends(r.guessMsg(0))
        r.guestSends(r.passMsg())
        r.guestSends("garbage")
        assertTrue(r.toGuest.isEmpty())
        assertTrue(r.changes.isEmpty())
    }

    @Test
    fun aGuessMadeOnAnOlderPositionOrGameIsDropped() {
        val r = Rig(duet(giver = 0))
        val old = r.guessMsg(1)                    // made while looking at seq 0
        r.guestSends(r.guessMsg(0))                // a valid guess first: the game is now at seq 1
        assertEquals(1, r.changes.size)
        r.toGuest.clear()
        r.guestSends(old)                          // the old one arrives late
        assertTrue(r.toGuest.isEmpty())
        assertEquals(1, r.changes.size)
        assertFalse(r.host.game.found[1])
        // another game with the same position number
        r.guestSends(Protocol.duetGuess(1, r.host.game.gameId + 1, r.host.game.seq))
        assertTrue(r.toGuest.isEmpty() && r.changes.size == 1)
        // a pass for an older position
        r.guestSends(Protocol.duetPass(r.host.game.gameId, r.host.game.seq - 1))
        assertTrue(r.toGuest.isEmpty() && r.changes.size == 1)
        // the current position still works
        r.guestSends(r.guessMsg(1))
        assertTrue(r.host.game.found[1])
    }

    @Test
    fun theHostsOwnGuessesReachTheGuest() {
        val r = Rig(duet(giver = 1)) // host guesses
        r.host.guess(9) // word 9: agent on key 1, the giver's key
        val update = r.deliver().last()
        assertTrue(update.view.found[9])
        r.host.pass()
        assertEquals(0, r.deliver().last().view.giver)
    }

    @Test
    fun aNewGameReplacesTheOldOneOnTheGuest() {
        val r = Rig(duet())
        r.host.guess(0); r.host.pass()
        r.deliver()
        val fresh = DuetEngine.newGame(Lang.PT, (1..25).map { "p$it" }, 10, 77L, Random(3))
        r.host.startGame(fresh)
        val update = r.deliver().last()
        assertEquals(77L, update.view.gameId)
        assertEquals(10, update.view.maxTokens)
        assertNull(update.cue)
    }

    @Test
    fun aStaleUpdateIsIgnored() {
        val r = Rig(duet(giver = 0))
        r.guestSends(r.guessMsg(0))
        r.guestSends(r.guessMsg(1))
        val sent = r.toGuest.toList()
        r.toGuest.clear()
        assertEquals(2, sent.size)
        assertTrue(r.guest.onMessage(sent[1]) != null)
        assertNull(r.guest.onMessage(sent[0]))
    }
}
