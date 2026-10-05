package com.zepinto.codenames

import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val WORDS = (1..25).map { "word$it" }

/** A game whose key is known: red agents first, then blue, then neutrals, assassin last. */
private fun fixedGame(start: Team = Team.RED): GameState {
    val g = Engine.newGame(Lang.EN, WORDS, start, 7L, Random(1))
    val first = CardType.of(start)
    val second = CardType.of(start.other)
    val key = List(9) { first } + List(8) { second } + List(7) { CardType.NEUTRAL } + CardType.ASSASSIN
    return g.copy(key = key)
}

/** The same game with an unlimited clue already given, so cards can be revealed. */
private fun playing(g: GameState = fixedGame()): GameState = Engine.giveClue(g, "clue", 0)

class EngineTest {
    @Test
    fun newGameHasTheRightMixOfCards() {
        for (start in Team.values()) repeat(50) { seed ->
            val g = Engine.newGame(Lang.EN, WORDS, start, 1L, Random(seed))
            assertEquals(9, g.key.count { it == CardType.of(start) })
            assertEquals(8, g.key.count { it == CardType.of(start.other) })
            assertEquals(7, g.key.count { it == CardType.NEUTRAL })
            assertEquals(1, g.key.count { it == CardType.ASSASSIN })
            assertEquals(start, g.turn)
            assertEquals(0, g.revealedCount)
            assertEquals(9, g.remaining(start))
            assertEquals(8, g.remaining(start.other))
        }
    }

    @Test
    fun ownAgentKeepsTheTurnGoing() {
        val g = Engine.reveal(playing(), 0)
        assertTrue(g.revealed[0])
        assertEquals(Team.RED, g.turn)
        assertEquals(8, g.remaining(Team.RED))
        assertEquals(LastReveal(0, CardType.RED, Team.RED), g.last)
    }

    @Test
    fun bystanderEndsTheTurn() {
        val g = Engine.reveal(playing(), 20)
        assertEquals(Team.BLUE, g.turn)
        assertNull(g.clue)
    }

    @Test
    fun theOtherTeamsAgentEndsTheTurnAndCountsForThem() {
        val g = Engine.reveal(playing(), 10) // a blue card, red guessing
        assertEquals(Team.BLUE, g.turn)
        assertEquals(7, g.remaining(Team.BLUE))
        assertEquals(9, g.remaining(Team.RED))
    }

    @Test
    fun theAssassinLosesTheGameForTheTeamThatPickedIt() {
        val g = Engine.reveal(playing(), 24)
        assertEquals(Team.BLUE, g.winner)
        assertEquals(WinReason.ASSASSIN, g.winReason)
        // and the same when blue picks it
        val blueTurn = playing(Engine.endTurn(fixedGame()))
        assertEquals(Team.RED, Engine.reveal(blueTurn, 24).winner)
    }

    @Test
    fun uncoveringAllOfYourAgentsWins() {
        var g = playing()
        for (i in 0 until 9) g = Engine.reveal(g, i)
        assertEquals(Team.RED, g.winner)
        assertEquals(WinReason.AGENTS, g.winReason)
    }

    @Test
    fun theOtherTeamWinsIfYouUncoverTheirLastAgent() {
        var g = playing(Engine.endTurn(fixedGame())) // blue to guess
        for (i in 9 until 16) g = Engine.reveal(g, i) // 7 blue agents, blue keeps guessing
        assertEquals(Team.BLUE, g.turn)
        g = playing(Engine.endTurn(g)) // red to guess
        g = Engine.reveal(g, 16)
        assertEquals(Team.BLUE, g.winner)
    }

    @Test
    fun nothingHappensTwiceOrAfterTheEnd() {
        var g = Engine.reveal(playing(), 0)
        assertEquals(g, Engine.reveal(g, 0))
        assertEquals(g, Engine.reveal(g, 99))
        assertEquals(g, Engine.reveal(g, -1))
        val over = Engine.reveal(playing(), 24)
        assertEquals(over, Engine.reveal(over, 1))
        assertEquals(over, Engine.endTurn(over))
        assertEquals(over, Engine.giveClue(over, "x", 2))
    }

    @Test
    fun aClueAllowsOneGuessMoreThanItsNumber() {
        var g = Engine.giveClue(fixedGame(), "  sea  ", 2)
        assertEquals(Clue("sea", 2), g.clue)
        assertEquals(3, g.guessesLeft)
        g = Engine.reveal(g, 0); assertEquals(2, g.guessesLeft); assertEquals(Team.RED, g.turn)
        g = Engine.reveal(g, 1); assertEquals(1, g.guessesLeft); assertEquals(Team.RED, g.turn)
        g = Engine.reveal(g, 2) // third and last guess, correct: the turn ends by itself
        assertEquals(Team.BLUE, g.turn)
        assertNull(g.clue)
        assertNull(g.guessesLeft)
    }

    @Test
    fun zeroAndMissingNumbersMeanUnlimitedGuesses() {
        for (n in listOf(0, null)) {
            val g = Engine.giveClue(fixedGame(), "tree", n)
            assertNull(g.clue!!.number)
            assertNull(g.guessesLeft)
            assertEquals(Team.RED, Engine.reveal(g, 0).turn)
        }
    }

    @Test
    fun onlyOneClueEachTurnAndNeverAnEmptyOne() {
        val g = Engine.giveClue(fixedGame(), "one", 1)
        assertEquals(g, Engine.giveClue(g, "two", 3))
        assertEquals(fixedGame(), Engine.giveClue(fixedGame(), "   ", 3))
        val next = Engine.endTurn(g)
        assertNull(next.clue)
        assertEquals(Clue("two", 3), Engine.giveClue(next, "two", 3).clue)
    }

    @Test
    fun noCardCanBeRevealedBeforeTheClue() {
        val g = fixedGame()
        assertEquals(g, Engine.reveal(g, 0))
        assertEquals(g, Engine.reveal(g, 24))
        // but the team may always pass
        assertEquals(Team.BLUE, Engine.endTurn(g).turn)
    }

    @Test
    fun aClueCannotBeAWordStillOnTheBoard() {
        val g = fixedGame()
        assertEquals(g, Engine.giveClue(g, "WORD3", 2))
        assertEquals(g, Engine.giveClue(g, " word3 ", 2))
        assertTrue(Engine.isOnBoard(g, "word3"))
        // once that card is face up it is a fair clue
        val after = Engine.reveal(Engine.giveClue(g, "other", 0), 2)
        val next = Engine.endTurn(after)
        assertFalse(Engine.isOnBoard(next, "word3"))
        assertEquals(Clue("word3", 1), Engine.giveClue(next, "word3", 1).clue)
    }

    @Test
    fun everyChangeBumpsTheVersion() {
        var g = fixedGame()
        val seen = mutableListOf(g.seq)
        g = Engine.giveClue(g, "a", 1); seen += g.seq
        g = Engine.reveal(g, 0); seen += g.seq
        g = Engine.endTurn(g); seen += g.seq
        assertEquals(seen.sorted().distinct(), seen)
    }
}

class ProtocolTest {
    @Test
    fun theTableViewNeverContainsTheKeyOfAHiddenCard() {
        val g = Engine.reveal(playing(), 0)
        val json = Protocol.state(g)
        assertFalse("no key array on the wire", json.contains("\"key\""))
        val view = (Protocol.parse(json) as Message.State).view
        assertEquals(CardType.RED, view.shown[0])
        for (i in 1 until 25) assertNull("card $i is still hidden", view.shown[i])
    }

    @Test
    fun theWholeKeyIsSentOnceTheGameIsOver() {
        val g = Engine.reveal(playing(), 24)
        val view = (Protocol.parse(Protocol.state(g)) as Message.State).view
        assertEquals(g.key, view.shown)
        assertEquals(Team.BLUE, view.winner)
    }

    @Test
    fun theViewSurvivesAJsonRoundTrip() {
        val withClue = Engine.reveal(Engine.giveClue(fixedGame(Team.BLUE), "ocean", 3), 2) // own agent, clue still active
        val back = (Protocol.parse(Protocol.state(withClue)) as Message.State).view
        assertEquals(BoardView.from(withClue), back)
        assertEquals(Clue("ocean", 3), back.clue)
        assertEquals(3, back.guessesLeft)

        val turnPassed = Engine.reveal(withClue, 12) // blue picks a red agent: turn ends, clue is gone
        val back2 = (Protocol.parse(Protocol.state(turnPassed)) as Message.State).view
        assertEquals(BoardView.from(turnPassed), back2)
        assertNull(back2.clue)
        assertEquals(Team.RED, back2.turn)
    }

    @Test
    fun anUnlimitedClueSurvivesTheRoundTrip() {
        val g = Engine.giveClue(fixedGame(), "x", 0)
        val back = (Protocol.parse(Protocol.state(g)) as Message.State).view
        assertNull(back.clue!!.number)
        assertNull(back.guessesLeft)
    }

    @Test
    fun aSavedGameComesBackExactly() {
        var g = Engine.giveClue(fixedGame(), "bridge", 2)
        g = Engine.reveal(g, 3)
        assertEquals(g, Protocol.loadGame(Protocol.saveGame(g)))
        val over = Engine.reveal(g, 24)
        assertEquals(over, Protocol.loadGame(Protocol.saveGame(over)))
    }

    @Test
    fun garbageIsIgnoredNotCrashedOn() {
        for (bad in listOf("", "{", "[]", "{\"t\":\"nope\"}", "{\"t\":\"guess\"}", "{\"t\":\"state\",\"s\":{}}", "hello")) {
            assertNull(bad, Protocol.parse(bad))
        }
        assertNull(Protocol.loadGame("{}"))
    }

    @Test
    fun simpleMessagesRoundTrip() {
        assertEquals(Message.Guess(12), Protocol.parse(Protocol.guess(12)))
        assertEquals(Message.EndTurn, Protocol.parse(Protocol.endTurn()))
        assertEquals(Message.Sync, Protocol.parse(Protocol.sync()))
        assertEquals(Message.Ping, Protocol.parse(Protocol.ping()))
        assertEquals(Message.Welcome, Protocol.parse(Protocol.welcome()))
        assertEquals(Message.Denied("pin"), Protocol.parse(Protocol.denied("pin")))
        assertEquals(Message.Hello("1234", "Pixel", Protocol.VERSION), Protocol.parse(Protocol.hello("1234", "Pixel")))
        assertEquals(Message.Hello(null, "Pixel", Protocol.VERSION), Protocol.parse(Protocol.hello(null, "Pixel")))
    }
}

class SoundCueTest {
    private fun snap(count: Int, last: LastReveal?, winner: Team? = null, id: Long = 1) = Snapshot(id, count, winner, last)

    @Test
    fun theCueDependsOnWhatWasUncovered() {
        val before = snap(0, null)
        assertEquals(SoundCue.GOOD, cueFor(before, snap(1, LastReveal(0, CardType.RED, Team.RED))))
        assertEquals(SoundCue.BAD, cueFor(before, snap(1, LastReveal(0, CardType.BLUE, Team.RED))))
        assertEquals(SoundCue.NEUTRAL, cueFor(before, snap(1, LastReveal(0, CardType.NEUTRAL, Team.RED))))
        assertEquals(SoundCue.ASSASSIN, cueFor(before, snap(1, LastReveal(0, CardType.ASSASSIN, Team.RED), Team.BLUE)))
        assertEquals(SoundCue.WIN, cueFor(before, snap(1, LastReveal(0, CardType.RED, Team.RED), Team.RED)))
    }

    @Test
    fun noSoundWithoutAChange() {
        val a = snap(3, LastReveal(1, CardType.RED, Team.RED))
        assertNull(cueFor(null, a))
        assertNull(cueFor(a, a))
        assertNull(cueFor(snap(0, null), snap(1, LastReveal(0, CardType.RED, Team.RED), id = 2)))
    }
}

class SessionTest {
    /** Two phones wired together in memory. */
    private class Pair(game: GameState) {
        val toClient = mutableListOf<String>()
        val changes = mutableListOf<GameState>()
        val host = HostSession(game, { toClient += it }, { changes += it })
        val client = ClientSession()

        fun clientSends(text: String) = host.onMessage(text) { toClient += it }
        fun deliver(): List<ClientSession.Update> = toClient.toList().also { toClient.clear() }.mapNotNull { client.onMessage(it) }
    }

    @Test
    fun aGuessFromTheTableReachesTheHostAndComesBack() {
        val p = Pair(playing())
        p.clientSends(Protocol.sync())
        assertEquals(0, p.deliver().last().view.revealedCount)

        p.clientSends(Protocol.guess(0))
        val updates = p.deliver()
        assertEquals(SoundCue.GOOD, updates.last().cue)
        assertTrue(p.host.game.revealed[0])
        assertEquals(p.host.game.seq, p.client.view!!.seq)
        assertEquals(1, p.changes.size)
    }

    @Test
    fun anIllegalGuessChangesNothingAndSendsNothing() {
        val p = Pair(Engine.reveal(playing(), 0))
        p.clientSends(Protocol.guess(0))
        p.clientSends(Protocol.guess(77))
        p.clientSends("garbage")
        assertTrue(p.toClient.isEmpty())
        assertTrue(p.changes.isEmpty())
    }

    @Test
    fun theTableCanEndTheTurnButCannotSetClueOrKey() {
        val p = Pair(fixedGame())
        p.clientSends(Protocol.endTurn())
        assertEquals(Team.BLUE, p.host.game.turn)
        // a "state" or "hello" message from the table is not an order
        val before = p.host.game
        p.clientSends(Protocol.state(fixedGame(Team.BLUE)))
        p.clientSends(Protocol.hello(null, "x"))
        assertEquals(before, p.host.game)
    }

    @Test
    fun theSpymastersClueShowsOnTheTable() {
        val p = Pair(fixedGame())
        p.host.giveClue("river", 3)
        p.deliver()
        assertEquals(Clue("river", 3), p.client.view!!.clue)
        assertEquals(4, p.client.view!!.guessesLeft)
    }

    @Test
    fun aStaleStateIsIgnored() {
        val p = Pair(fixedGame())
        p.host.giveClue("a", 1)
        p.clientSends(Protocol.guess(0))
        val sent = p.toClient.toList()
        p.toClient.clear()
        // deliver the newest first, then the older one
        assertEquals(2, sent.size)
        assertTrue(p.client.onMessage(sent[1]) != null)
        assertNull(p.client.onMessage(sent[0]))
        assertEquals(p.host.game.seq, p.client.view!!.seq)
    }

    @Test
    fun aNewGameIsPickedUpEvenThoughItsVersionStartsOver() {
        val p = Pair(fixedGame())
        repeat(3) { p.host.endTurn() }
        p.deliver()
        assertTrue(p.client.view!!.seq >= 3)
        val fresh = Engine.newGame(Lang.EN, WORDS, Team.BLUE, 99L, Random(5))
        p.host.startGame(fresh)
        val update = p.deliver().last()
        assertEquals(99L, update.view.gameId)
        assertNull(update.cue)
        assertEquals(Team.BLUE, update.view.turn)
    }

    @Test
    fun reconnectingGetsTheCurrentStateWithoutASound() {
        val p = Pair(playing())
        p.clientSends(Protocol.guess(0))
        p.clientSends(Protocol.guess(1))
        val late = ClientSession() // a table phone that joins after play started
        p.toClient.clear()
        p.host.onMessage(Protocol.sync()) { p.toClient += it }
        val update = late.onMessage(p.toClient.single())!!
        assertEquals(2, update.view.revealedCount)
        assertNull(update.cue)
    }
}

class WordListTest {
    private fun words(lang: Lang): List<String> {
        val f = listOf("src/main/assets/${lang.wordFile}", "app/src/main/assets/${lang.wordFile}").map(::File).first { it.exists() }
        return f.readLines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    @Test
    fun everyLanguageHasAGoodWordList() {
        for (lang in Lang.entries) {
            val w = words(lang)
            assertTrue("${lang.code} has ${w.size} words", w.size >= 400)
            assertEquals("${lang.code} duplicates", w.size, w.map { it.lowercase() }.toSet().size)
            w.forEach {
                assertTrue("${lang.code}: '$it' has a space", !it.contains(' '))
                assertTrue("${lang.code}: '$it' is too long for a card", it.length <= 11)
                assertEquals("${lang.code}: '$it' should be lowercase", it.lowercase(), it)
            }
        }
    }

    @Test
    fun theBoardNeverRepeatsAWordUntilTheListRunsOut() {
        val all = words(Lang.EN)
        val picker = WordPicker(all)
        val seen = mutableSetOf<String>()
        repeat(all.size / 25) {
            val board = picker.next(25, Random(it))
            assertEquals(25, board.toSet().size)
            assertTrue("a word came up again too soon", seen.addAll(board))
        }
        // after that it keeps working, with 25 distinct words every time
        repeat(5) { assertEquals(25, picker.next(25, Random(100 + it)).toSet().size) }
    }
}

class StringsTest {
    @Test
    fun everyLanguageIsComplete() {
        for (lang in Lang.entries) {
            val t = lang.strings
            assertEquals(7, t.rulesSections.size)
            t.rulesSections.forEach { assertTrue(it.title.isNotBlank() && it.lines.isNotEmpty()) }
            assertNotEquals(t.teamName(Team.RED), t.teamName(Team.BLUE))
            assertNotEquals(t.teamWins(Team.RED), t.teamWins(Team.BLUE))
            assertNotEquals(t.turnOf(Team.RED), t.turnOf(Team.BLUE))
            assertTrue(t.pairTitle("Ana").contains("Ana"))
            assertTrue(t.pairText("4821").contains("4821"))
            assertTrue(t.revealWord("sea").contains("sea"))
            assertTrue(t.agentsLabel.isNotBlank())
            assertTrue(t.guessesLeft(3).contains("3"))
            assertTrue(t.pairingCode("4821").contains("4821"))
        }
        assertEquals(Lang.PT, Lang.fromCode("pt"))
        assertNull(Lang.fromCode("fr"))
    }
}
