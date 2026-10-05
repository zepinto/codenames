package com.zepinto.codenames

import kotlin.random.Random

enum class Team {
    RED, BLUE;

    val other get() = if (this == RED) BLUE else RED
}

/** What a card is on the secret key. */
enum class CardType {
    RED, BLUE, NEUTRAL, ASSASSIN;

    val team: Team?
        get() = when (this) {
            RED -> Team.RED
            BLUE -> Team.BLUE
            else -> null
        }

    companion object {
        fun of(team: Team) = if (team == Team.RED) RED else BLUE
    }
}

enum class WinReason { AGENTS, ASSASSIN }

/** The card revealed most recently, and the team that was guessing when it was revealed. */
data class LastReveal(val index: Int, val type: CardType, val by: Team)

/** A spymaster's clue. [number] null means unlimited guesses (the 0 and infinity clues). */
data class Clue(val word: String, val number: Int?)

/**
 * The complete game, held by the spymaster phone, which is the only place that knows the key.
 * Everything the table phone needs is derived from it by [BoardView.from].
 */
data class GameState(
    val gameId: Long,
    val lang: Lang,
    val words: List<String>,
    val key: List<CardType>,
    val revealed: List<Boolean>,
    val startTeam: Team,
    val turn: Team,
    val clue: Clue? = null,
    /** Guesses the team may still make this turn; null when there is no limit. */
    val guessesLeft: Int? = null,
    val winner: Team? = null,
    val winReason: WinReason? = null,
    /** Goes up on every change, so a phone can ignore a stale message. */
    val seq: Int = 0,
    val last: LastReveal? = null,
) {
    val over get() = winner != null
    val revealedCount get() = revealed.count { it }
    fun remaining(team: Team) = key.indices.count { key[it].team == team && !revealed[it] }
}

object Engine {
    const val SIZE = 25
    const val AGENTS_FIRST = 9
    const val AGENTS_SECOND = 8
    const val NEUTRALS = 7

    /** A fresh game: the team that starts has 9 agents, the other 8, plus 7 bystanders and 1 assassin. */
    fun newGame(lang: Lang, words: List<String>, startTeam: Team, gameId: Long, rng: Random): GameState {
        require(words.size == SIZE) { "a game needs exactly $SIZE words" }
        val key = buildList {
            repeat(AGENTS_FIRST) { add(CardType.of(startTeam)) }
            repeat(AGENTS_SECOND) { add(CardType.of(startTeam.other)) }
            repeat(NEUTRALS) { add(CardType.NEUTRAL) }
            add(CardType.ASSASSIN)
        }.shuffled(rng)
        return GameState(
            gameId = gameId,
            lang = lang,
            words = words,
            key = key,
            revealed = List(SIZE) { false },
            startTeam = startTeam,
            turn = startTeam,
        )
    }

    /**
     * Give a clue. Only one clue per turn, never after the game is over, and a clue may not be
     * one of the words still face down on the board.
     */
    fun giveClue(g: GameState, word: String, number: Int?): GameState {
        val clean = word.trim().replace(Regex("\\s+"), " ")
        if (g.over || g.clue != null || clean.isEmpty() || isOnBoard(g, clean)) return g
        val unlimited = number == null || number <= 0
        return g.copy(
            clue = Clue(clean, if (unlimited) null else number),
            guessesLeft = if (unlimited) null else number!! + 1,
            seq = g.seq + 1,
        )
    }

    /** True when [word] is one of the cards that are still face down. */
    fun isOnBoard(g: GameState, word: String) =
        g.words.indices.any { !g.revealed[it] && g.words[it].equals(word.trim(), ignoreCase = true) }

    /**
     * The team in play reveals one card, once the spymaster has given the clue.
     * - Assassin: the team in play loses at once.
     * - A team has no agents left: that team wins, whoever uncovered the last one.
     * - Own agent: the turn goes on, unless the guesses allowed by the clue are used up.
     * - Bystander or the other team's agent: the turn ends.
     */
    fun reveal(g: GameState, index: Int): GameState {
        if (g.over || g.clue == null || index !in 0 until SIZE || g.revealed[index]) return g
        val type = g.key[index]
        val revealed = g.revealed.toMutableList().also { it[index] = true }
        val next = g.copy(revealed = revealed, seq = g.seq + 1, last = LastReveal(index, type, g.turn))

        if (type == CardType.ASSASSIN) {
            return next.copy(winner = g.turn.other, winReason = WinReason.ASSASSIN, guessesLeft = null)
        }
        for (team in Team.values()) {
            if (next.remaining(team) == 0) return next.copy(winner = team, winReason = WinReason.AGENTS, guessesLeft = null)
        }
        if (type.team == g.turn) {
            val left = g.guessesLeft?.minus(1)
            return if (left != null && left <= 0) switchTurn(next) else next.copy(guessesLeft = left)
        }
        return switchTurn(next)
    }

    /** The team in play stops guessing. */
    fun endTurn(g: GameState): GameState = if (g.over) g else switchTurn(g.copy(seq = g.seq + 1))

    private fun switchTurn(g: GameState) = g.copy(turn = g.turn.other, clue = null, guessesLeft = null)
}

/** Hands out words, never repeating one until the whole list has been used. */
class WordPicker(private val all: List<String>) {
    private val pool = ArrayDeque<String>()

    fun next(count: Int, rng: Random): List<String> {
        require(count <= all.size) { "word list too short" }
        val picked = mutableListOf<String>()
        while (picked.size < count) {
            if (pool.isEmpty()) pool.addAll(all.shuffled(rng))
            val word = pool.removeFirst()
            if (word !in picked) picked += word
        }
        return picked
    }
}
