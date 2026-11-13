package org.rsmod.content.other.bosskills

import jakarta.inject.Inject
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.enums.DodianEnums.dodian_bosses
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.table.HiscoresBossesInfoRow
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.module.PluginModule

public class BossKillsModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(BossKillBroadcast::class.java)
    }
}

/**
 * Tells every player online when a boss dies. A boss is one of the Dodian bosses in
 * `enum.dodian_bosses`, or any npc whose kill count varp is one of the hiscores' boss kill counts.
 */
public class BossKillBroadcast @Inject constructor(private val players: PlayerList) :
    NpcDeathKillHook {
    private val bossVarps: Set<Int> by lazy {
        HiscoresBossesInfoRow.all().map { it.bossvarp }.toSet()
    }

    private val dodianBosses: Set<Int> by lazy {
        dodian_bosses.backing.keys.map { it.id }.toSet()
    }

    override fun onKill(context: NpcDeathKillContext) {
        if (!context.isBoss()) return
        val hero = context.hero
        val line =
            "${context.npc.visType.name} has been slain by ${hero.displayName} " +
                "(Combat ${hero.appearance.combatLevel})"
        for (player in players) {
            player.mes(line, ChatType.Broadcast)
        }
    }

    private fun NpcDeathKillContext.isBoss(): Boolean {
        if (npc.id in dodianBosses) return true
        val varp = npc.paramOrNull(BaseParams.killcount_varp) ?: return false
        return varp.id in bossVarps
    }
}
