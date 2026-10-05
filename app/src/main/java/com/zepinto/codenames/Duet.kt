package com.zepinto.codenames

import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

/** What a word is on ONE player's key in Duet. */
enum class DuetCard { AGENT, BYSTANDER, ASSASSIN }

enum class DuetPhase { PLAYING, SUDDEN_DEATH, WON, LOST }
enum class LossReason { ASSASSIN, SUDDEN_DEATH }
enum class DuetResult { AGENT, BYSTANDER, ASSASSIN, WRONG }

/** The word guessed most recently, what it turned out to be, and who guessed it (0 or 1). */
data class DuetLast(val index: Int, val result: DuetResult, val by: Int)

/**
 * The whole Duet game, held by the phone that created it (player 0). It knows both keys; the other phone
 * (player 1) is only ever sent its own key and what has been uncovered ([DuetView]).
 */
data class DuetState(
    val gameId: Long,
    val lang: Lang,
    val words: List<String>,
    /** keys[p][i] is what word i is on player p's key. */
    val keys: List<List<DuetCard>>,
    /** Agents that have been found: the word is covered and nobody gives clues for it any more. */
    val found: List<Boolean>,
    /** tan[p][i]: word i was guessed and turned out to be a bystander on player p's key. */
    val tan: List<List<Boolean>>,
    /** The player giving the clue this turn; the other one guesses. */
    val giver: Int,
    /** Timer tokens left = turns left, counting the turn in progress. */
    val tokens: Int,
    val maxTokens: Int,
    val guessesThisTurn: Int = 0,
    val phase: DuetPhase = DuetPhase.PLAYING,
    val lossReason: LossReason? = null,
    val seq: Int = 0,
    val last: DuetLast? = null,
) {
    val over get() = phase == DuetPhase.WON || phase == DuetPhase.LOST
    val guesser get() = 1 - giver
    fun isAgent(i: Int) = keys[0][i] == DuetCard.AGENT || keys[1][i] == DuetCard.AGENT
    val agentsTotal get() = (0 until DuetEngine.SIZE).count { isAgent(it) }
    val agentsFound get() = (0 until DuetEngine.SIZE).count { found[it] && isAgent(it) }
    val marks get() = found.count { it } + tan[0].count { it } + tan[1].count { it }
}

object DuetEngine {
    const val SIZE = 25
    const val AGENTS = 15
    const val STANDARD_TURNS = 9

    /**
     * How the 25 words look on the two keys, as (player 0's key, player 1's key). Each key ends up with
     * 9 agents, 3 assassins and 13 bystanders; 3 words are agents on both keys, 15 are agents on at least one.
     */
    private val PAIRS: List<Pair<DuetCard, DuetCard>> = run {
        val a = DuetCard.AGENT
        val b = DuetCard.BYSTANDER
        val x = DuetCard.ASSASSIN
        buildList {
            repeat(3) { add(a to a) }
            repeat(5) { add(a to b) }
            add(a to x)
            repeat(5) { add(b to a) }
            add(x to a)
            repeat(7) { add(b to b) }
            add(b to x)
            add(x to b)
            add(x to x)
        }
    }

    fun newGame(lang: Lang, words: List<String>, turns: Int, gameId: Long, rng: Random): DuetState {
        require(words.size == SIZE) { "a game needs exactly $SIZE words" }
        require(PAIRS.size == SIZE)
        require(turns >= 1) { "a game needs at least one turn" }
        val pairs = PAIRS.shuffled(rng)
        return DuetState(
            gameId = gameId,
            lang = lang,
            words = words,
            keys = listOf(pairs.map { it.first }, pairs.map { it.second }),
            found = List(SIZE) { false },
            tan = listOf(List(SIZE) { false }, List(SIZE) { false }),
            giver = rng.nextInt(2), // either player may give the first clue
            tokens = turns,
            maxTokens = turns,
        )
    }

    /**
     * [player] guesses word [index].
     * - During play only the guesser may guess, and the clue-giver's key says what the word is:
     *   agent = found, the turn goes on; bystander = marked, the turn ends; assassin = both lose.
     * - In sudden death either player may guess, judged by the other player's key; anything but an agent loses.
     */
    fun guess(g: DuetState, player: Int, index: Int): DuetState {
        if (g.over || index !in 0 until SIZE || g.found[index]) return g
        return when (g.phase) {
            DuetPhase.PLAYING -> {
                if (player != g.guesser || g.tan[g.giver][index]) return g
                when (g.keys[g.giver][index]) {
                    DuetCard.AGENT -> foundAgent(g, player, index)
                    DuetCard.BYSTANDER -> {
                        val tan = g.tan.mapIndexed { p, row -> if (p == g.giver) row.toMutableList().also { it[index] = true } else row }
                        endTurn(
                            g.copy(
                                tan = tan,
                                guessesThisTurn = g.guessesThisTurn + 1,
                                seq = g.seq + 1,
                                last = DuetLast(index, DuetResult.BYSTANDER, player),
                            )
                        )
                    }
                    DuetCard.ASSASSIN -> g.copy(
                        phase = DuetPhase.LOST, lossReason = LossReason.ASSASSIN,
                        seq = g.seq + 1, last = DuetLast(index, DuetResult.ASSASSIN, player),
                    )
                }
            }
            DuetPhase.SUDDEN_DEATH -> when (g.keys[1 - player][index]) {
                DuetCard.AGENT -> foundAgent(g, player, index)
                DuetCard.BYSTANDER -> g.copy(
                    phase = DuetPhase.LOST, lossReason = LossReason.SUDDEN_DEATH,
                    seq = g.seq + 1, last = DuetLast(index, DuetResult.WRONG, player),
                )
                DuetCard.ASSASSIN -> g.copy(
                    phase = DuetPhase.LOST, lossReason = LossReason.ASSASSIN,
                    seq = g.seq + 1, last = DuetLast(index, DuetResult.ASSASSIN, player),
                )
            }
            else -> g
        }
    }

    /** The guesser stops: allowed only after at least one guess, and it uses up the turn. */
    fun pass(g: DuetState, player: Int): DuetState {
        if (g.phase != DuetPhase.PLAYING || player != g.guesser || g.guessesThisTurn < 1) return g
        return endTurn(g.copy(seq = g.seq + 1))
    }

    private fun foundAgent(g: DuetState, player: Int, index: Int): DuetState {
        val found = g.found.toMutableList().also { it[index] = true }
        val next = g.copy(found = found, guessesThisTurn = g.guessesThisTurn + 1, seq = g.seq + 1, last = DuetLast(index, DuetResult.AGENT, player))
        return if (next.agentsFound == AGENTS) next.copy(phase = DuetPhase.WON) else next
    }

    /** One timer token is used up and the roles swap. With no tokens left, sudden death begins. */
    private fun endTurn(g: DuetState): DuetState {
        val tokens = g.tokens - 1
        return g.copy(
            tokens = tokens,
            giver = 1 - g.giver,
            guessesThisTurn = 0,
            phase = if (tokens <= 0) DuetPhase.SUDDEN_DEATH else g.phase,
        )
    }
}

/**
 * What one player's phone knows: the words, their OWN key, and what has been uncovered.
 * The other player's key is included only once the game is over.
 */
data class DuetView(
    val me: Int,
    val gameId: Long,
    val lang: Lang,
    val words: List<String>,
    val myKey: List<DuetCard>,
    val found: List<Boolean>,
    val tan: List<List<Boolean>>,
    val giver: Int,
    val tokens: Int,
    val maxTokens: Int,
    val guessesThisTurn: Int,
    val phase: DuetPhase,
    val lossReason: LossReason?,
    val agentsFound: Int,
    val agentsTotal: Int,
    val partnerKey: List<DuetCard>?,
    val seq: Int,
    val last: DuetLast?,
) {
    val over get() = phase == DuetPhase.WON || phase == DuetPhase.LOST
    val iGive get() = giver == me
    val iGuess get() = giver != me
    val marks get() = found.count { it } + tan[0].count { it } + tan[1].count { it }

    companion object {
        fun of(g: DuetState, player: Int) = DuetView(
            me = player,
            gameId = g.gameId,
            lang = g.lang,
            words = g.words,
            myKey = g.keys[player],
            found = g.found,
            tan = g.tan,
            giver = g.giver,
            tokens = g.tokens,
            maxTokens = g.maxTokens,
            guessesThisTurn = g.guessesThisTurn,
            phase = g.phase,
            lossReason = g.lossReason,
            agentsFound = g.agentsFound,
            agentsTotal = g.agentsTotal,
            partnerKey = if (g.over) g.keys[1 - player] else null,
            seq = g.seq,
            last = g.last,
        )
    }
}

fun DuetState.snapshot() = DuetSnapshot(gameId, marks, phase, last)
fun DuetView.snapshot() = DuetSnapshot(gameId, marks, phase, last)

data class DuetSnapshot(val gameId: Long, val marks: Int, val phase: DuetPhase, val last: DuetLast?)

/** The sound to play when a Duet game moves from [prev] to [next], or null for none. */
fun duetCueFor(prev: DuetSnapshot?, next: DuetSnapshot): SoundCue? {
    if (prev == null || prev.gameId != next.gameId) return null
    return when {
        next.phase == DuetPhase.WON && prev.phase != DuetPhase.WON -> SoundCue.WIN
        next.phase == DuetPhase.LOST && prev.phase != DuetPhase.LOST -> SoundCue.ASSASSIN
        next.marks > prev.marks -> when (next.last?.result) {
            DuetResult.AGENT -> SoundCue.GOOD
            DuetResult.BYSTANDER -> SoundCue.NEUTRAL
            else -> null
        }
        else -> null
    }
}

/** JSON for the Duet messages and for saving the host's game. */
object DuetCodec {
    fun stateJson(g: DuetState, player: Int): String =
        JSONObject().put("t", "dstate").put("s", viewJson(DuetView.of(g, player))).toString()

    private fun cards(list: List<DuetCard>) = JSONArray(list.map { it.ordinal })
    private fun parseCards(a: JSONArray) = List(a.length()) { DuetCard.values()[a.getInt(it)] }
    private fun bools(a: JSONArray) = List(a.length()) { a.getBoolean(it) }
    private fun lastJson(l: DuetLast?): Any = l?.let { JSONObject().put("i", it.index).put("r", it.result.ordinal).put("by", it.by) } ?: JSONObject.NULL
    private fun parseLast(o: JSONObject?) = o?.let { DuetLast(it.getInt("i"), DuetResult.values()[it.getInt("r")], it.getInt("by")) }

    private fun viewJson(v: DuetView) = JSONObject()
        .put("me", v.me).put("game", v.gameId).put("lang", v.lang.code)
        .put("words", JSONArray(v.words))
        .put("key", cards(v.myKey))
        .put("found", JSONArray(v.found))
        .put("tan0", JSONArray(v.tan[0])).put("tan1", JSONArray(v.tan[1]))
        .put("giver", v.giver).put("tokens", v.tokens).put("max", v.maxTokens).put("guesses", v.guessesThisTurn)
        .put("phase", v.phase.ordinal).put("why", v.lossReason?.ordinal ?: -1)
        .put("agents", v.agentsFound).put("total", v.agentsTotal)
        .put("partner", v.partnerKey?.let { cards(it) } ?: JSONObject.NULL)
        .put("seq", v.seq).put("last", lastJson(v.last))

    fun parseView(o: JSONObject): DuetView {
        val words = o.getJSONArray("words").let { a -> List(a.length()) { a.getString(it) } }
        val key = parseCards(o.getJSONArray("key"))
        val found = bools(o.getJSONArray("found"))
        val tan0 = bools(o.getJSONArray("tan0"))
        val tan1 = bools(o.getJSONArray("tan1"))
        require(listOf(words.size, key.size, found.size, tan0.size, tan1.size).all { it == DuetEngine.SIZE }) { "bad board size" }
        val me = o.getInt("me")
        require(me in 0..1)
        return DuetView(
            me = me,
            gameId = o.getLong("game"),
            lang = Lang.fromCode(o.getString("lang")) ?: Lang.EN,
            words = words,
            myKey = key,
            found = found,
            tan = listOf(tan0, tan1),
            giver = o.getInt("giver").also { require(it in 0..1) },
            tokens = o.getInt("tokens"),
            maxTokens = o.getInt("max"),
            guessesThisTurn = o.getInt("guesses"),
            phase = DuetPhase.values()[o.getInt("phase")],
            lossReason = o.getInt("why").takeIf { it >= 0 }?.let { LossReason.values()[it] },
            agentsFound = o.getInt("agents"),
            agentsTotal = o.getInt("total"),
            partnerKey = if (o.isNull("partner")) null else parseCards(o.getJSONArray("partner")),
            seq = o.getInt("seq"),
            last = parseLast(o.optJSONObject("last")),
        )
    }

    fun save(g: DuetState): String = JSONObject()
        .put("game", g.gameId).put("lang", g.lang.code)
        .put("words", JSONArray(g.words))
        .put("key0", cards(g.keys[0])).put("key1", cards(g.keys[1]))
        .put("found", JSONArray(g.found))
        .put("tan0", JSONArray(g.tan[0])).put("tan1", JSONArray(g.tan[1]))
        .put("giver", g.giver).put("tokens", g.tokens).put("max", g.maxTokens).put("guesses", g.guessesThisTurn)
        .put("phase", g.phase.ordinal).put("why", g.lossReason?.ordinal ?: -1)
        .put("seq", g.seq).put("last", lastJson(g.last))
        .toString()

    fun load(text: String): DuetState? = try {
        val o = JSONObject(text)
        val words = o.getJSONArray("words").let { a -> List(a.length()) { a.getString(it) } }
        val k0 = parseCards(o.getJSONArray("key0"))
        val k1 = parseCards(o.getJSONArray("key1"))
        val found = bools(o.getJSONArray("found"))
        val t0 = bools(o.getJSONArray("tan0"))
        val t1 = bools(o.getJSONArray("tan1"))
        require(listOf(words.size, k0.size, k1.size, found.size, t0.size, t1.size).all { it == DuetEngine.SIZE })
        DuetState(
            gameId = o.getLong("game"),
            lang = Lang.fromCode(o.getString("lang")) ?: Lang.EN,
            words = words,
            keys = listOf(k0, k1),
            found = found,
            tan = listOf(t0, t1),
            giver = o.getInt("giver").also { require(it in 0..1) },
            tokens = o.getInt("tokens"),
            maxTokens = o.getInt("max"),
            guessesThisTurn = o.getInt("guesses"),
            phase = DuetPhase.values()[o.getInt("phase")],
            lossReason = o.getInt("why").takeIf { it >= 0 }?.let { LossReason.values()[it] },
            seq = o.getInt("seq"),
            last = parseLast(o.optJSONObject("last")),
        )
    } catch (_: Exception) {
        null
    }
}

/**
 * The first player's side of a Duet game: it owns the game, applies the second player's guesses and passes,
 * and sends the second player only what that player may know.
 */
class DuetHostSession(
    game: DuetState,
    /** Send a message to the connected second player. */
    private val sendToGuest: (String) -> Unit,
    private val onChange: (DuetState) -> Unit,
) {
    var game: DuetState = game
        private set

    fun guestState() = DuetCodec.stateJson(game, 1)

    fun onMessage(text: String, reply: (String) -> Unit) {
        when (val m = Protocol.parse(text)) {
            // A message made for an earlier position of the game (or an earlier game) is dropped, never applied to the new one.
            is Message.DuetGuess -> if (m.gameId == game.gameId && m.seq == game.seq) apply(DuetEngine.guess(game, 1, m.index))
            is Message.DuetPass -> if (m.gameId == game.gameId && m.seq == game.seq) apply(DuetEngine.pass(game, 1))
            Message.Sync -> reply(guestState())
            else -> Unit
        }
    }

    /** The first player's own taps. */
    fun guess(index: Int) = apply(DuetEngine.guess(game, 0, index))
    fun pass() = apply(DuetEngine.pass(game, 0))

    fun startGame(newGame: DuetState) {
        game = newGame
        onChange(game)
        sendToGuest(guestState())
    }

    private fun apply(next: DuetState) {
        if (next == game) return
        game = next
        onChange(game)
        sendToGuest(guestState())
    }
}

/** The second player's side: keeps the latest view and says which sound a new state calls for. */
class DuetClientSession {
    var view: DuetView? = null
        private set

    data class Update(val view: DuetView, val cue: SoundCue?)

    fun onMessage(text: String): Update? {
        val next = (Protocol.parse(text) as? Message.DuetUpdate)?.view ?: return null
        val old = view
        if (old != null && old.gameId == next.gameId && next.seq < old.seq) return null
        view = next
        return Update(next, duetCueFor(old?.snapshot(), next.snapshot()))
    }
}
