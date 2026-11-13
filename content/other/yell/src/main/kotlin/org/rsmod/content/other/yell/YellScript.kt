package org.rsmod.content.other.yell

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** `::yell <message>` sends a coloured line to every player, with the sender's crown. */
class YellScript @Inject constructor(private val players: PlayerList) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("yell") {
            desc = "Send a message to every player online"
            cheat { yell() }
        }
    }

    private fun Cheat.yell() {
        val text = rawArgs.joinToString(" ").trim().take(MAX_LENGTH)
        if (text.isEmpty()) {
            player.mes("Use ::yell followed by your message.")
            return
        }
        val line =
            "[Yell] ${player.crown()}${player.displayName}: <col=$COLOUR>${text.escapeTags()}</col>"
        for (receiver in players) {
            receiver.mes(line)
        }
    }

    /** The chat icon matching the crown shown in public chat; the client's icons start at 0. */
    private fun Player.crown(): String {
        val icon = modLevel.clientCode
        return if (icon > 0) "<img=${icon - 1}>" else ""
    }

    private fun String.escapeTags(): String =
        buildString {
            for (char in this@escapeTags) {
                when (char) {
                    '<' -> append("<lt>")
                    '>' -> append("<gt>")
                    else -> append(char)
                }
            }
        }

    private companion object {
        const val COLOUR = "0000ff"
        const val MAX_LENGTH = 80
    }
}
