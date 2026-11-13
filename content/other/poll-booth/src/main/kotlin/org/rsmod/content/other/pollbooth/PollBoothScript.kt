package org.rsmod.content.other.pollbooth

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.time.LocalDate
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.IfScriptArgs
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onIfScriptTrigger
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private var Player.votedPoll: Int by intVarp("varp.dodian_poll_voted")
private var Player.shouldVote: Int by intVarBit("varbit.poll_playershouldvote")

/**
 * The poll booths at the banks, driving Jagex's `ballot` interface: a poll list per year, the
 * overview with results and the voting page. The client builds every page from clientscripts the
 * server runs and reports clicks back through if-script triggers.
 */
class PollBoothScript : PluginScript() {
    private val poll = SAMPLE_POLL
    private val tallies = poll.questions.map { IntArray(it.options.size) }

    override fun ScriptContext.startup() {
        for (booth in BOOTHS) {
            onOpLoc1(booth) { openBooth() }
        }
        onPlayerLogin { player.shouldVote = if (player.votedPoll == poll.id) 0 else 1 }
        onIfOverlayButton("component.ballot:close") { ifCloseSub(INTERFACE) }
        onIfOpen(INTERFACE) {
            for (trigger in TRIGGERS) {
                player.ifSetEvents(trigger, -1..-1, IfEvent.ScriptTrigger)
            }
        }

        onIfScriptTrigger<IntArg>("component.ballot:list_years_content") { showList(it.value) }
        onIfScriptTrigger<IntArg>("component.ballot:list") {
            if (it.value == poll.id) showOverview()
        }
        onIfScriptTrigger<FilterArgs>("component.ballot:list_filters") { toggleFilter(it) }
        onIfScriptTrigger<NoArgs>("component.ballot:exit_overview") { showList(currentYear()) }
        onIfScriptTrigger<IntArg>("component.ballot:frame") {
            when (it.value) {
                FRAME_VOTE -> showVoting()
                FRAME_SUBMIT -> submit()
            }
        }
        onIfScriptTrigger<ChoiceArgs>("component.ballot:single_choice") {
            selectChoice(it.question, it.choice)
        }
    }

    private fun ProtectedAccess.openBooth() {
        ifOpenOverlay(INTERFACE)
        showList(currentYear())
    }

    private fun ProtectedAccess.showList(year: Int) {
        val polls = if (year == currentYear()) listOf(poll) else emptyList()
        val voted = player.votedPoll == poll.id
        val status = if (voted) STATUS_HAS_VOTED else STATUS_CAN_VOTE
        val subtitle = if (voted) "Open - you have voted" else "Open - you may vote"
        runClientScript(
            script("clientscript.ballot_list_build"),
            year,
            1 shl (currentYear() - FIRST_POLL_YEAR),
            polls.map { it.title }.toTypedArray(),
            polls.map { subtitle }.toTypedArray(),
            polls.map { it.id }.toIntArray(),
            polls.map { CATEGORY_OPINION or (status shl 8) }.toIntArray(),
        )
    }

    private fun ProtectedAccess.toggleFilter(args: FilterArgs) {
        val varbit = if (args.category == 1) FILTER_CATEGORIES else FILTER_STATUSES
        val bit = 1 shl args.id
        vars[varbit] = if (args.hidden == 1) vars[varbit] or bit else vars[varbit] and bit.inv()
    }

    private fun ProtectedAccess.showOverview() {
        val voted = player.votedPoll == poll.id
        vars[VOTE_PAGE_ACTIVE] = 0
        vars[VOTE_STATUS] =
            when {
                voted -> VOTE_STATUS_VOTED
                hasAnyAnswer() -> VOTE_STATUS_UNSUBMITTED
                else -> VOTE_STATUS_NOT_VOTED
            }
        runClientScript(script("clientscript.ballot_overview_build_start"), poll.title)
        runClientScript(script("clientscript.ballot_overview_intro_open"), CATEGORY_OPINION, -1, "")
        addDescription()
        for ((index, question) in poll.questions.withIndex()) {
            runClientScript(script("clientscript.ballot_add_question"), index, index, 0, question.text)
            val votes = tallies[index]
            val total = votes.sum()
            val permille = IntArray(votes.size) { if (total == 0) 0 else votes[it] * 1000 / total }
            runClientScript(
                script("clientscript.ballot_overview_build_single_choice"),
                index,
                if (voted) 1 else 0,
                -1,
                if (voted) answer(index) else -1,
                0,
                -1,
                question.options.toTypedArray(),
                votes.copyOf(),
                permille,
            )
        }
        runClientScript(script("clientscript.ballot_overview_build_end"))
    }

    private fun ProtectedAccess.showVoting() {
        if (player.votedPoll == poll.id) {
            showOverview()
            return
        }
        vars[VOTE_PAGE_ACTIVE] = 1
        vars[VOTE_STATUS] = if (hasAnyAnswer()) VOTE_STATUS_UNSUBMITTED else VOTE_STATUS_VOTING
        runClientScript(script("clientscript.ballot_voting_build_start"), poll.title)
        addDescription()
        for ((index, question) in poll.questions.withIndex()) {
            runClientScript(script("clientscript.ballot_add_question"), index, index, 0, question.text)
            runClientScript(
                script("clientscript.ballot_voting_build_single_choice"),
                index,
                0,
                answer(index),
                0,
                0,
                0,
                0,
                question.options.toTypedArray(),
            )
        }
        runClientScript(script("clientscript.ballot_voting_build_end"))
    }

    private fun ProtectedAccess.addDescription() {
        runClientScript(script("clientscript.ballot_add_text"), -1, 0, 0, 0, 0, poll.description, "", "")
    }

    private fun ProtectedAccess.selectChoice(question: Int, choice: Int) {
        if (player.votedPoll == poll.id || vars[VOTE_PAGE_ACTIVE] != 1) {
            return
        }
        val options = poll.questions.getOrNull(question)?.options ?: return
        if (choice !in options.indices) {
            return
        }
        vars[ANSWERS[question]] = choice + 1
        vars[VOTE_STATUS] = VOTE_STATUS_UNSUBMITTED
        runClientScript(script("clientscript.ballot_voting_single_choice_select"), question, 0, choice)
        runClientScript(script("clientscript.ballot_voting_question_error"), -1, 0, "")
    }

    private fun ProtectedAccess.submit() {
        if (player.votedPoll == poll.id || vars[VOTE_PAGE_ACTIVE] != 1) {
            return
        }
        val unanswered = poll.questions.indices.firstOrNull { answer(it) == -1 }
        if (unanswered != null) {
            runClientScript(
                script("clientscript.ballot_voting_question_error"),
                unanswered,
                0,
                "Please answer this question.",
            )
            return
        }
        for (index in poll.questions.indices) {
            tallies[index][answer(index)]++
        }
        player.votedPoll = poll.id
        player.shouldVote = 0
        mes("Thank you, your vote has been recorded.")
        showOverview()
    }

    private fun ProtectedAccess.answer(question: Int): Int {
        val choice = vars[ANSWERS[question]] - 1
        return if (choice in poll.questions[question].options.indices) choice else -1
    }

    private fun ProtectedAccess.hasAnyAnswer(): Boolean =
        poll.questions.indices.any { answer(it) != -1 }

    private fun currentYear(): Int = LocalDate.now().year

    private fun script(internal: String): Int = internal.asRSCM(RSCMType.CLIENTSCRIPT)

    internal data class IntArg(val value: Int) : IfScriptArgs

    internal class NoArgs : IfScriptArgs

    internal data class ChoiceArgs(val question: Int, val choice: Int) : IfScriptArgs

    internal data class FilterArgs(val category: Int, val id: Int, val hidden: Int) : IfScriptArgs

    private companion object {
        const val INTERFACE = "interface.ballot"
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
