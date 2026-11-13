package org.rsmod.content.other.starterkit

import org.rsmod.api.enums.NamedEnums.dodian_starter_kit
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private val Player.newAccount: Boolean by boolVarBit("varbit.new_player_account")

/** Gives every new account the Dodian starter kit from `enum.dodian_starter_kit`. */
class StarterKitScript : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { if (player.newAccount) player.giveStarterKit() }
    }

    private fun Player.giveStarterKit() {
        for ((obj, count) in dodian_starter_kit) {
            if (count != null) {
                invAdd(inv, obj.id, count)
            }
        }
    }
}
