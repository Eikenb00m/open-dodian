package org.rsmod.content.other.pollbooth

internal data class Poll(
    val id: Int,
    val title: String,
    val description: String,
    val questions: List<PollQuestion>,
)

internal data class PollQuestion(val text: String, val options: List<String>)

/** Placeholder until polls are managed from the web interface. */
internal val SAMPLE_POLL =
    Poll(
        id = 1,
        title = "Uber OSRS: Welcome Poll",
        description =
            "Help us shape the server! This is a sample poll; soon polls will be set up " +
                "through the website.",
        questions =
            listOf(
                PollQuestion(
                    "Which skill should get new content first?",
                    listOf("Woodcutting", "Fishing", "Mining", "Thieving"),
                ),
                PollQuestion(
                    "Should the drop rates stay the same as on Dodian?",
                    listOf("Yes", "No"),
                ),
                PollQuestion(
                    "How did you find Uber OSRS?",
                    listOf("A friend", "A website or forum", "Discord", "Something else"),
                ),
            ),
    )
