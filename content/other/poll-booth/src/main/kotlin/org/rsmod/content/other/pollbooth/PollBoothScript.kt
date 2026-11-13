package org.rsmod.content.other.pollbooth

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import java.time.LocalDate
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.IfScriptArgs
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onIfScriptTrigger
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private var Player.shouldVote: Int by intVarBit("varbit.poll_playershouldvote")

/**
 * The poll booths at the banks, driving Jagex's `ballot` interface: a poll list per year, the
 * overview with results and the voting page. The client builds every page from clientscripts the
 * server runs and reports clicks back through if-script triggers. Polls come from the database
 * through [PollRepository].
 */
internal class PollBoothScript @Inject constructor(private val polls: PollRepository) :
    PluginScript() {
    override fun ScriptContext.startup() {
        polls.refresh()
        for (booth in BOOTHS) {
            onOpLoc1(booth) { openBooth() }
        }
        onPlayerLogin {
            polls.loadVotes(player) {
                if (player.isSlotAssigned) {
                    player.shouldVote = if (polls.hasUnvotedOpenPoll(player)) 1 else 0
                }
            }
        }
        onPlayerLogout { polls.forget(player) }
        onIfOverlayButton("component.ballot:close") { ifCloseSub(INTERFACE) }
        onIfOpen(INTERFACE) {
            for (trigger in TRIGGERS) {
                player.ifSetEvents(trigger, -1..-1, IfEvent.ScriptTrigger)
            }
        }

        onIfScriptTrigger<IntArg>("component.ballot:list_years_content") { showList(it.value) }
        onIfScriptTrigger<IntArg>("component.ballot:list") { args ->
            polls.poll(args.value)?.let { showOverview(it, fresh = true) }
        }
        onIfScriptTrigger<FilterArgs>("component.ballot:list_filters") { toggleFilter(it) }
        onIfScriptTrigger<NoArgs>("component.ballot:exit_overview") {
            showList(viewedPoll()?.year ?: currentYear())
        }
        onIfScriptTrigger<IntArg>("component.ballot:frame") {
            val poll = viewedPoll() ?: return@onIfScriptTrigger
            when (it.value) {
                FRAME_VOTE -> showVoting(poll)
                FRAME_SUBMIT -> submit(poll)
            }
        }
        onIfScriptTrigger<ChoiceArgs>("component.ballot:single_choice") {
            viewedPoll()?.let { poll -> selectChoice(poll, it.question, it.choice) }
        }
    }

    private fun ProtectedAccess.openBooth() {
        polls.refreshIfStale()
        ifOpenOverlay(INTERFACE)
        showList(polls.polls.firstOrNull()?.year ?: currentYear())
    }

    private fun ProtectedAccess.viewedPoll(): Poll? = polls.poll(vars[VIEWING])

    private fun ProtectedAccess.showList(year: Int) {
        val shown = polls.polls.filter { it.year == year }
        val years = polls.polls.map { it.year } + currentYear()
        val yearMask =
            years.filter { it - FIRST_POLL_YEAR in 0..30 }.fold(0) { mask, y -> mask or (1 shl (y - FIRST_POLL_YEAR)) }
        runClientScript(
            script("clientscript.ballot_list_build"),
            year,
            yearMask,
            shown.map { it.title }.toTypedArray(),
            shown.map { subtitle(it) }.toTypedArray(),
            shown.map { it.id }.toIntArray(),
            shown.map { CATEGORY_OPINION or (listStatus(it) shl 8) }.toIntArray(),
        )
    }

    private fun ProtectedAccess.subtitle(poll: Poll): String =
        when {
            !poll.open -> "Closed"
            polls.hasVoted(player, poll.id) -> "Open - you have voted"
            else -> "Open - you may vote"
        }

    private fun ProtectedAccess.listStatus(poll: Poll): Int =
        when {
            !poll.open -> STATUS_NOT_OPEN
            polls.hasVoted(player, poll.id) -> STATUS_HAS_VOTED
            else -> STATUS_CAN_VOTE
        }

    private fun ProtectedAccess.toggleFilter(args: FilterArgs) {
        val varbit = if (args.category == 1) FILTER_CATEGORIES else FILTER_STATUSES
        val bit = 1 shl args.id
        vars[varbit] = if (args.hidden == 1) vars[varbit] or bit else vars[varbit] and bit.inv()
    }

    private fun ProtectedAccess.showOverview(poll: Poll, fresh: Boolean = false) {
        if (fresh && vars[VIEWING] != poll.id) {
            vars[VIEWING] = poll.id
            for (answer in ANSWERS) {
                vars[answer] = 0
            }
        }
        val voted = polls.hasVoted(player, poll.id)
        val showResults = voted || !poll.open
        vars[VOTE_PAGE_ACTIVE] = 0
        vars[VOTE_STATUS] =
            when {
                voted -> VOTE_STATUS_VOTED
                poll.open && hasAnyAnswer(poll) -> VOTE_STATUS_UNSUBMITTED
                else -> VOTE_STATUS_NOT_VOTED
            }
        runClientScript(script("clientscript.ballot_overview_build_start"), poll.title)
        runClientScript(script("clientscript.ballot_overview_intro_open"), CATEGORY_OPINION, -1, "")
        addDescription(poll)
        for ((index, question) in poll.questions.withIndex()) {
            runClientScript(script("clientscript.ballot_add_question"), index, index, 0, question.text)
            val votes = question.votes
            val total = votes.sum()
            val permille = IntArray(votes.size) { if (total == 0) 0 else votes[it] * 1000 / total }
            runClientScript(
                script("clientscript.ballot_overview_build_single_choice"),
                index,
                if (showResults) 1 else 0,
                -1,
                if (voted) answer(poll, index) else -1,
                0,
                -1,
                question.options.toTypedArray(),
                votes.copyOf(),
                permille,
            )
        }
        runClientScript(script("clientscript.ballot_overview_build_end"))
    }

    private fun ProtectedAccess.showVoting(poll: Poll) {
        if (!poll.open || polls.hasVoted(player, poll.id)) {
            if (!poll.open) mes("This poll is closed.")
            showOverview(poll)
            return
        }
        vars[VOTE_PAGE_ACTIVE] = 1
        vars[VOTE_STATUS] = if (hasAnyAnswer(poll)) VOTE_STATUS_UNSUBMITTED else VOTE_STATUS_VOTING
        runClientScript(script("clientscript.ballot_voting_build_start"), poll.title)
        addDescription(poll)
        for ((index, question) in poll.questions.withIndex()) {
            runClientScript(script("clientscript.ballot_add_question"), index, index, 0, question.text)
            runClientScript(
                script("clientscript.ballot_voting_build_single_choice"),
                index,
                0,
                answer(poll, index),
                0,
                0,
                0,
                0,
                question.options.toTypedArray(),
            )
        }
        runClientScript(script("clientscript.ballot_voting_build_end"))
    }

    private fun ProtectedAccess.addDescription(poll: Poll) {
        runClientScript(script("clientscript.ballot_add_text"), -1, 0, 0, 0, 0, poll.description, "", "")
    }

    private fun ProtectedAccess.selectChoice(poll: Poll, question: Int, choice: Int) {
        if (!poll.open || polls.hasVoted(player, poll.id) || vars[VOTE_PAGE_ACTIVE] != 1) {
            return
        }
        val options = poll.questions.getOrNull(question)?.options ?: return
        if (choice !in options.indices || question !in ANSWERS.indices) {
            return
        }
        vars[ANSWERS[question]] = choice + 1
        vars[VOTE_STATUS] = VOTE_STATUS_UNSUBMITTED
        runClientScript(script("clientscript.ballot_voting_single_choice_select"), question, 0, choice)
        runClientScript(script("clientscript.ballot_voting_question_error"), -1, 0, "")
    }

    private fun ProtectedAccess.submit(poll: Poll) {
        if (!poll.open || polls.hasVoted(player, poll.id) || vars[VOTE_PAGE_ACTIVE] != 1) {
            return
        }
        val unanswered = poll.questions.indices.firstOrNull { answer(poll, it) == -1 }
        if (unanswered != null) {
            runClientScript(
                script("clientscript.ballot_voting_question_error"),
                unanswered,
                0,
                "Please answer this question.",
            )
            return
        }
        val answers = poll.questions.indices.map { answer(poll, it) }
        val voter = player
        polls.vote(voter, poll, answers) { result ->
            when (result) {
                VoteResult.Recorded -> voter.mes("Thank you, your vote has been recorded.")
                VoteResult.AlreadyVoted -> voter.mes("You have already voted in this poll.")
                VoteResult.Failed -> voter.mes("Your vote could not be saved. Please try again later.")
            }
            if (voter.isSlotAssigned) {
                voter.shouldVote = if (polls.hasUnvotedOpenPoll(voter)) 1 else 0
            }
        }
        showOverview(poll)
    }

    private fun ProtectedAccess.answer(poll: Poll, question: Int): Int {
        if (question !in ANSWERS.indices) return -1
        val choice = vars[ANSWERS[question]] - 1
        return if (choice in poll.questions[question].options.indices) choice else -1
    }

    private fun ProtectedAccess.hasAnyAnswer(poll: Poll): Boolean =
        poll.questions.indices.any { answer(poll, it) != -1 }

    private fun currentYear(): Int = LocalDate.now().year

    private fun script(internal: String): Int = internal.asRSCM(RSCMType.CLIENTSCRIPT)

    internal data class IntArg(val value: Int) : IfScriptArgs

    internal class NoArgs : IfScriptArgs

    internal data class ChoiceArgs(val question: Int, val choice: Int) : IfScriptArgs

    internal data class FilterArgs(val category: Int, val id: Int, val hidden: Int) : IfScriptArgs

    private companion object {
        const val INTERFACE = "interface.ballot"
        const val VIEWING = "varp.dodian_poll_viewing"
        const val VOTE_STATUS = "varbit.poll_vote_status"
        const val VOTE_PAGE_ACTIVE = "varbit.poll_vote_page_active"
        const val FILTER_CATEGORIES = "varbit.poll_filters_categories"
        const val FILTER_STATUSES = "varbit.poll_filters_statuses"

        const val VOTE_STATUS_NOT_VOTED = 0
        const val VOTE_STATUS_VOTED = 2
        const val VOTE_STATUS_VOTING = 3
        const val VOTE_STATUS_UNSUBMITTED = 4

        const val FRAME_VOTE = 1
        const val FRAME_SUBMIT = 2

        const val FIRST_POLL_YEAR = 2013
        const val CATEGORY_OPINION = 2

        // dbtable poll_filters: poll_status_notopen, _hasvoted, _ineligible, _canvote.
        const val STATUS_NOT_OPEN = 0
        const val STATUS_HAS_VOTED = 1
        const val STATUS_CAN_VOTE = 3

        val TRIGGERS =
            listOf(
                "component.ballot:list_years_content",
                "component.ballot:list",
                "component.ballot:list_filters",
                "component.ballot:exit_overview",
                "component.ballot:frame",
                "component.ballot:single_choice",
            )

        val ANSWERS =
            listOf(
                "varbit.dodian_poll_answer_1",
                "varbit.dodian_poll_answer_2",
                "varbit.dodian_poll_answer_3",
                "varbit.dodian_poll_answer_4",
            )

        val BOOTHS =
            listOf(
                "loc.pollbooth_blue_standard",
                "loc.pollbooth_blue_highlight",
                "loc.pollbooth_brimstone_standard",
                "loc.pollbooth_brimstone_highlight",
                "loc.pollbooth_brown_standard",
                "loc.pollbooth_brown_highlight",
                "loc.pollbooth_green_standard",
                "loc.pollbooth_green_highlight",
                "loc.pollbooth_grey_standard",
                "loc.pollbooth_grey_highlight",
                "loc.pollbooth_greyframe_standard",
                "loc.pollbooth_greyframe_highlight",
                "loc.pollbooth_red_standard",
                "loc.pollbooth_red_highlight",
                "loc.pollbooth_rotten_standard",
                "loc.pollbooth_rotten_highlight",
                "loc.pollbooth_sanguine_standard",
                "loc.pollbooth_sanguine_highlight",
                "loc.pollbooth_swamp_standard",
                "loc.pollbooth_swamp_highlight",
                "loc.pollbooth_tzhaar_standard",
                "loc.pollbooth_tzhaar_highlight",
            )
    }
}
