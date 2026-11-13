package org.rsmod.content.other.pollbooth

internal data class Poll(
    val id: Int,
    val title: String,
    val description: String,
    val open: Boolean,
    val year: Int,
    val questions: List<PollQuestion>,
)

internal class PollQuestion(val text: String, val options: List<String>, val votes: IntArray)

internal enum class VoteResult {
    Recorded,
    AlreadyVoted,
    Failed,
}
