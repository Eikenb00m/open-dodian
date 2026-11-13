package org.rsmod.content.other.playerdesign

import dev.openrune.definition.type.widget.IfEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.api.table.FacialHairStylesRow
import org.rsmod.api.table.HairStylesRow
import org.rsmod.api.table.HandStylesRow
import org.rsmod.api.table.LeggingStylesRow
import org.rsmod.api.table.ShoeStylesRow
import org.rsmod.api.table.SleeveStylesRow
import org.rsmod.api.table.TorsoStylesRow
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.Appearance
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private val Player.newAccount: Boolean by boolVarBit("varbit.new_player_account")
private var Player.designBodyType: Int by intVarBit("varbit.player_design_bodytype")

/**
 * The full character designer from Tutorial Island, free to use. It opens on a new account's first
 * login and from the Makeover Mage. Every arrow edits the live appearance, which the interface
 * previews through the player model.
 */
class PlayerDesignScript : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { if (player.newAccount) player.strongQueue(OPEN_QUEUE, 1) }
        onPlayerQueue(OPEN_QUEUE) { ifOpenMainModal(INTERFACE) }
        onIfOpen(INTERFACE) {
            player.designBodyType = player.appearance.bodyType
            player.ifSetEvents(PRONOUN_BUTTONS, PRONOUN_SLOTS, IfEvent.Op1)
        }

        for ((slot, buttons) in KIT_BUTTONS.withIndex()) {
            onIfModalButton(buttons.first) { cycleKit(slot, -1) }
            onIfModalButton(buttons.second) { cycleKit(slot, 1) }
        }
        for ((slot, buttons) in COLOUR_BUTTONS.withIndex()) {
            onIfModalButton(buttons.first) { cycleColour(slot, -1) }
            onIfModalButton(buttons.second) { cycleColour(slot, 1) }
        }
        onIfModalButton("component.player_design:gender_male") {
            selectBodyType(Appearance.BODY_TYPE_A)
        }
        onIfModalButton("component.player_design:gender_female") {
            selectBodyType(Appearance.BODY_TYPE_B)
        }
        onIfModalButton(PRONOUN_BUTTONS) {
            val pronoun = it.comsub - 1
            if (pronoun in Appearance.PRONOUN_HE..Appearance.PRONOUN_THEY) {
                player.appearance.pronoun = pronoun
            }
        }
        onIfModalButton("component.player_design:confirm") { ifClose() }
    }

    private fun ProtectedAccess.cycleKit(slot: Int, step: Int) {
        val appearance = player.appearance
        val typeB = appearance.bodyType == Appearance.BODY_TYPE_B
        val kits =
            KIT_STYLES[slot].map { (a, b) -> if (typeB) b else a }.filter { it >= 0 }.distinct()
        if (kits.isEmpty()) {
            return
        }
        val index = kits.indexOf(appearance.identKitSnapshot()[slot].toInt())
        val next = if (index == -1) 0 else Math.floorMod(index + step, kits.size)
        appearance.setIdentKit(slot, kits[next])
    }

    private fun ProtectedAccess.cycleColour(slot: Int, step: Int) {
        val appearance = player.appearance
        val current = appearance.coloursSnapshot()[slot].toInt()
        appearance.setColour(slot, Math.floorMod(current + step, COLOUR_COUNTS[slot]))
    }

    private fun ProtectedAccess.selectBodyType(bodyType: Int) {
        val appearance = player.appearance
        if (appearance.bodyType != bodyType) {
            val toTypeB = bodyType == Appearance.BODY_TYPE_B
            val kits = appearance.identKitSnapshot()
            for ((slot, styles) in KIT_STYLES.withIndex()) {
                val current = kits[slot].toInt()
                val style =
                    styles.firstOrNull { (a, b) -> current == if (toTypeB) a else b }
                        ?: styles.firstOrNull()
                        ?: continue
                appearance.setIdentKit(slot, if (toTypeB) style.second else style.first)
            }
            appearance.bodyType = bodyType
        }
        player.designBodyType = bodyType
    }

    private companion object {
        const val INTERFACE = "interface.player_design"
        const val OPEN_QUEUE = "queue.player_design_open"
        const val PRONOUN_BUTTONS = "component.player_design:pronouns_buttons"
        val PRONOUN_SLOTS = 0..8

        /** Left and right arrows per identity kit slot: head, jaw, torso, arms, hands, legs, feet. */
        val KIT_BUTTONS =
            listOf(
                "component.player_design:head_left" to "component.player_design:head_right",
                "component.player_design:jaw_left" to "component.player_design:jaw_right",
                "component.player_design:torso_left" to "component.player_design:torso_right",
                "component.player_design:arms_left" to "component.player_design:arms_right",
                "component.player_design:hands_left" to "component.player_design:hands_right",
                "component.player_design:legs_left" to "component.player_design:legs_right",
                "component.player_design:feet_left" to "component.player_design:feet_right",
            )

        /** Left and right arrows per colour slot: hair, torso, legs, feet, skin. */
        val COLOUR_BUTTONS =
            listOf(
                "component.player_design:hair_left" to "component.player_design:hair_right",
                "component.player_design:torso_col_left" to
                    "component.player_design:torso_col_right",
                "component.player_design:legs_col_left" to "component.player_design:legs_col_right",
                "component.player_design:feet_col_left" to "component.player_design:feet_col_right",
                "component.player_design:skin_left" to "component.player_design:skin_right",
            )

        /** How many recolours the client knows per colour slot, in [COLOUR_BUTTONS] order. */
        val COLOUR_COUNTS = intArrayOf(25, 29, 29, 6, 8)

        val KIT_STYLES: List<List<Pair<Int, Int>>> by lazy {
            listOf(
                HairStylesRow.all().map { it.playerKitIdTypeA to it.playerKitIdTypeB },
                FacialHairStylesRow.all().map { it.playerKitIdTypeA to it.playerKitIdTypeB },
                TorsoStylesRow.all().map { it.playerKitIdTypeA to it.playerKitIdTypeB },
                SleeveStylesRow.all().map { it.playerKitIdTypeA to it.playerKitIdTypeB },
                HandStylesRow.all().map { it.playerKitIdTypeA to it.playerKitIdTypeB },
                LeggingStylesRow.all().map { it.playerKitIdTypeA to it.playerKitIdTypeB },
                ShoeStylesRow.all().map { it.playerKitIdTypeA to it.playerKitIdTypeB },
            )
        }
    }
}
