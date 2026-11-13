package org.rsmod.content.areas.city.rimmington.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc4
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The Makeover Mage, who opens the full character designer for free, as on Dodian. */
class MakeoverMageScript @Inject constructor(private val random: GameRandom) : PluginScript() {
    override fun ScriptContext.startup() {
        for (mage in MAGES) {
            onOpNpc1(mage) { startDialogue(it.npc) { makeoverMage() } }
            onOpNpc4(mage) { openMakeover() }
            onAiTimer(mage) { npc.queue(SWITCH_QUEUE, 1) }
            onNpcQueue(npcType(mage), SWITCH_QUEUE) { switchForm() }
        }
    }

    private suspend fun StandardNpcAccess.switchForm() {
        anim("seq.human_castentangle_staff")
        spotanim("spotanim.curse_impact", delay = 15, height = 124)
        delay(2)
        val female = npc.visType.id == "npc.makeover_mage_female".asRSCM(RSCMType.NPC)
        npc.transmog(npcType(if (female) MAGES[0] else MAGES[1]), Int.MAX_VALUE)
        say(if (random.randomBoolean()) "Ahah!" else "Ooh!")
        aiTimer(random.of(SWITCH_MIN_TICKS, SWITCH_MAX_TICKS))
    }

    private fun npcType(internal: String): NpcServerType =
        ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $internal")

    private suspend fun Dialogue.makeoverMage() {
        chatNpc(
            happy,
            "Hello there! I am known as the Makeover Mage! I have spent many years researching " +
                "magics that can change your physical appearance!",
        )
        chatNpc(
            quiz,
            "I can alter your physical form with my magic, there's no fee. Would you like me to " +
                "perform my magics upon you?",
        )
        makeoverOptions()
    }

    private suspend fun Dialogue.makeoverOptions() {
        val option =
            choice4(
                "Tell me more about this 'makeover'.",
                Option.TellMore,
                "Sure, I'll have a makeover.",
                Option.Makeover,
                "Cool amulet! Can I have one?",
                Option.Amulet,
                "No thanks.",
                Option.Decline,
            )
        when (option) {
            Option.TellMore -> tellMore()
            Option.Makeover -> haveMakeover()
            Option.Amulet -> buyAmulet()
            Option.Decline -> {
                chatPlayer(angry, "No thanks.")
                chatNpc(sad, "Ehhh... suit yourself.")
            }
        }
    }

    private suspend fun Dialogue.tellMore() {
        chatPlayer(quiz, "Tell me more about this 'make-over'.")
        chatNpc(
            happy,
            "Why, of course! Basically, and I will try and explain this so that you will " +
                "understand it correctly,",
        )
        chatNpc(
            happy,
            "I use my secret magical technique to melt your body down into a puddle of its elements.",
        )
        chatNpc(
            happy,
            "When I have broken down all trace of your body, I then rebuild it into the form I am " +
                "thinking of!",
        )
        chatNpc(neutral, "Or, you know, somewhere vaguely close enough anyway.")
        chatPlayer(worried, "Uh... that doesn't sound particularly safe to me...")
        chatNpc(
            happy,
            "It's as safe as houses! Why, I have only had thirty-six major accidents this month!",
        )
        chatNpc(quiz, "So what do you say? Feel like a change? There's no fee.")
        makeoverOptions()
    }

    private suspend fun Dialogue.haveMakeover() {
        chatPlayer(happy, "Sure, I'll have a makeover.")
        chatNpc(
            happy,
            "Good choice, good choice. You wouldn't want to carry on looking like that, I'm sure!",
        )
        access.openMakeover()
    }

    private suspend fun Dialogue.buyAmulet() {
        chatPlayer(quiz, "Cool amulet! Can I have one?")
        chatNpc(
            neutral,
            "No problem, but please remember that the amulet I will sell you is only a copy of my " +
                "own. It contains no magical powers, and as such will only cost you 100 coins.",
        )
        when {
            access.inv.count("obj.coins") < AMULET_PRICE -> {
                chatPlayer(sad, "Oh, I don't have enough money for that.")
            }
            access.inv.isFull() && access.inv.count("obj.coins") != AMULET_PRICE -> {
                chatPlayer(neutral, "No way! That's far too expensive.")
                chatNpc(neutral, "That's fair enough, my jewellery is not to everyone's taste.")
            }
            choice2("Sure, here you go.", true, "No way! That's far too expensive.", false) -> {
                chatPlayer(happy, "Sure, here you go.")
                access.invDel(access.inv, "obj.coins", AMULET_PRICE)
                access.invAdd(access.inv, AMULET, 1)
                objbox(AMULET, "You receive an amulet in exchange for 100 coins.")
            }
            else -> chatPlayer(neutral, "I don't have room to hold it. Maybe another time.")
        }
        chatNpc(
            quiz,
            "Anyway, would you like me to alter your physical form? For you, I'll do it for free!",
        )
        makeoverOptions()
    }

    private enum class Option {
        TellMore,
        Makeover,
        Amulet,
        Decline,
    }

    private fun ProtectedAccess.openMakeover() {
        ifOpenMainModal(DESIGN_INTERFACE)
    }

    private companion object {
        const val DESIGN_INTERFACE = "interface.player_design"
        const val AMULET = "obj.postie_pete_yin_yang"
        const val AMULET_PRICE = 100

        const val SWITCH_QUEUE = "queue.generic_queue1"
        const val SWITCH_MIN_TICKS = 100
        const val SWITCH_MAX_TICKS = 150

        val MAGES = listOf("npc.makeover_mage", "npc.makeover_mage_female")
    }
}
