package org.rsmod.content.other.pollbooth

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.db.gateway.model.isOk
import org.rsmod.game.entity.Player

/**
 * Polls live in the `dodian` schema, managed by the Uber OSRS portal (uber-osrs-portal,
 * `server/migrations/001_polls.sql`). This keeps a game-thread copy of every non-draft poll with
 * its tallies, refreshed when a booth is used, and records votes through `dodian.cast_poll_vote`,
 * which allows one vote per account across all worlds.
 */
@Singleton
internal class PollRepository @Inject constructor(private val db: GameDbManager) {
    private val logger = InlineLogger()

    var polls: List<Poll> = emptyList()
        private set

    private val votedByAccount = HashMap<String, MutableSet<Int>>()
    private var lastRefreshMillis = 0L
    private var refreshing = false
    private var loggedFailure = false

    fun poll(id: Int): Poll? = polls.firstOrNull { it.id == id }

    fun hasVoted(player: Player, pollId: Int): Boolean =
        votedByAccount[player.accountKey]?.contains(pollId) == true

    fun hasUnvotedOpenPoll(player: Player): Boolean =
        polls.any { it.open && !hasVoted(player, it.id) }

    fun refreshIfStale() {
        if (System.currentTimeMillis() - lastRefreshMillis >= STALE_MILLIS) {
            refresh()
        }
    }

    fun refresh() {
        if (refreshing) {
            return
        }
        refreshing = true
        db.request(
            request = { connection -> GameDbResult.Ok(loadPolls(connection)) },
            response = { result ->
                refreshing = false
                lastRefreshMillis = System.currentTimeMillis()
                if (result.isOk()) {
                    polls = result.value
                    loggedFailure = false
                } else if (!loggedFailure) {
                    loggedFailure = true
                    logger.warn { "Could not load polls (has the portal created schema dodian?): $result" }
                }
            },
        )
    }

    fun loadVotes(player: Player, onLoaded: () -> Unit) {
        val key = player.accountKey
        db.request(
            request = { connection -> GameDbResult.Ok(loadVotedPolls(connection, key)) },
            response = { result ->
                if (result.isOk()) {
                    votedByAccount[key] = result.value.toMutableSet()
                    onLoaded()
                }
            },
        )
    }

    fun forget(player: Player) {
        votedByAccount.remove(player.accountKey)
    }

    /**
     * Counts the vote locally right away so the overview shows it, then confirms it in the database;
     * a refused or failed vote is taken back out again.
     */
    fun vote(player: Player, poll: Poll, answers: List<Int>, onResult: (VoteResult) -> Unit) {
        val key = player.accountKey
        votedByAccount.getOrPut(key) { mutableSetOf() } += poll.id
        poll.questions.forEachIndexed { index, question -> question.votes[answers[index]]++ }
        val name = player.username
        val accountId = player.accountId
        db.request(
            request = { connection ->
                GameDbResult.Ok(castVote(connection, poll.id, name, accountId, answers))
            },
            response = { result ->
                when {
                    result.isOk() && result.value -> onResult(VoteResult.Recorded)
                    result.isOk() -> {
                        refresh()
                        onResult(VoteResult.AlreadyVoted)
                    }
                    else -> {
                        logger.warn { "Could not record a vote on poll ${poll.id}: $result" }
                        votedByAccount[key]?.remove(poll.id)
                        poll.questions.forEachIndexed { index, question ->
                            question.votes[answers[index]]--
                        }
                        onResult(VoteResult.Failed)
                    }
                }
            },
        )
    }

    private fun loadPolls(connection: DatabaseConnection): List<Poll> {
        val texts = HashMap<Pair<Int, Int>, String>()
        connection.prepareStatement(SELECT_QUESTIONS).use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    texts[rs.getInt("poll_id") to rs.getInt("position")] = rs.getString("text")
                }
            }
        }
        val options = HashMap<Int, MutableMap<Int, MutableList<Pair<String, Int>>>>()
        connection.prepareStatement(SELECT_RESULTS).use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    options
                        .getOrPut(rs.getInt("poll_id")) { sortedMapOf() }
                        .getOrPut(rs.getInt("question_position")) { mutableListOf() }
                        .add(rs.getString("option_text") to rs.getInt("votes"))
                }
            }
        }
        return connection.prepareStatement(SELECT_POLLS).use { statement ->
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val id = rs.getInt("id")
                        val questions =
                            options[id].orEmpty().map { (position, rows) ->
                                PollQuestion(
                                    text = texts[id to position].orEmpty(),
                                    options = rows.map { it.first },
                                    votes = rows.map { it.second }.toIntArray(),
                                )
                            }
                        add(
                            Poll(
                                id = id,
                                title = rs.getString("title"),
                                description = rs.getString("description"),
                                open = rs.getString("status") == "open",
                                year = rs.getInt("year"),
                                questions = questions,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun loadVotedPolls(connection: DatabaseConnection, key: String): Set<Int> =
        connection.prepareStatement(SELECT_VOTED).use { statement ->
            statement.setString(1, key)
            statement.executeQuery().use { rs ->
                buildSet {
                    while (rs.next()) {
                        add(rs.getInt(1))
                    }
                }
            }
        }

    private fun castVote(
        connection: DatabaseConnection,
        pollId: Int,
        name: String,
        accountId: Int,
        answers: List<Int>,
    ): Boolean =
        connection.prepareStatement(CAST_VOTE).use { statement ->
            statement.setInt(1, pollId)
            statement.setString(2, name)
            statement.setInt(3, accountId)
            val positions = answers.map { it.toShort() }.toTypedArray<Any>()
            statement.setArray(4, statement.connection.createArrayOf("int2", positions))
            statement.executeQuery().use { rs ->
                rs.next()
                rs.getBoolean(1)
            }
        }

    private val Player.accountKey: String
        get() = username.lowercase()

    private companion object {
        const val STALE_MILLIS = 15_000L

        const val SELECT_POLLS =
            "SELECT id, title, description, status, " +
                "extract(year FROM listed_at)::int AS year " +
                "FROM dodian.poll_list ORDER BY listed_at DESC, id DESC"

        const val SELECT_QUESTIONS =
            "SELECT q.poll_id, q.position, q.text FROM dodian.poll_questions q " +
                "JOIN dodian.poll_list l ON l.id = q.poll_id"

        const val SELECT_RESULTS =
            "SELECT r.poll_id, r.question_position, r.option_text, r.votes " +
                "FROM dodian.poll_results r JOIN dodian.poll_list l ON l.id = r.poll_id " +
                "ORDER BY r.poll_id, r.question_position, r.option_position"

        const val SELECT_VOTED = "SELECT poll_id FROM dodian.poll_votes WHERE voter_key = ?"

        const val CAST_VOTE = "SELECT dodian.cast_poll_vote(?, ?, ?, NULL, ?)"
    }
}
