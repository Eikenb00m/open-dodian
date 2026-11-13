package org.rsmod.content.other.serverbanner

import jakarta.inject.Inject
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ServerBannerScript
@Inject
constructor(private val players: PlayerList, private val config: ServerConfig) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfOpen("interface.server_banner") { player.sendBanner(onlineCount()) }
        onPlayerLogin { refreshAll(onlineCount()) }
        onPlayerLogout { refreshAll(onlineCount(leaving = player), except = player) }
    }

    private fun onlineCount(leaving: Player? = null): Int = players.count { it !== leaving }

    private fun refreshAll(online: Int, except: Player? = null) {
        for (player in players) {
            if (player !== except) {
                player.sendBanner(online)
            }
        }
    }

    private fun Player.sendBanner(online: Int) {
        ifSetText(TEXT, "$title - $online ${if (online == 1) "player" else "players"} online")
    }

    private val title: String
        get() = listOfNotNull(config.serverName ?: config.name, config.version).joinToString(" ")

    private companion object {
        private const val TEXT = "component.server_banner:text"
    }
}
