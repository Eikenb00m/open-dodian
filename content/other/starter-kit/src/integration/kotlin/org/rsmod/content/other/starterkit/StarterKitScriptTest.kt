package org.rsmod.content.other.starterkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.entity.Player

class StarterKitScriptTest {
    @Test
    fun GameTestState.`a new account starts with exactly the starter kit`() =
        runGameTest(StarterKitScript::class) {
            val fresh = Player().also { VarPlayerIntMapSetter.set(it, NEW_ACCOUNT, 1) }
            registerPlayer(player = fresh)

            for ((obj, count) in KIT) {
                assertEquals(count, fresh.inv.count(obj), obj)
            }
            assertEquals(KIT_SLOTS, fresh.inv.occupiedSpace())
        }

    @Test
    fun GameTestState.`an existing account gets no starter kit on login`() =
        runGameTest(StarterKitScript::class) {
            val existing = Player()
            registerPlayer(player = existing)

            assertEquals(0, existing.inv.occupiedSpace())
        }

    private companion object {
        const val NEW_ACCOUNT = "varbit.new_player_account"

        /** Bread does not stack, so the five loaves take five slots. */
        const val KIT_SLOTS = 12

        val KIT =
            mapOf(
                "obj.bronze_sword" to 1,
                "obj.wooden_shield" to 1,
                "obj.bronze_axe" to 1,
                "obj.bronze_pickaxe" to 1,
                "obj.tinderbox" to 1,
                "obj.net" to 1,
                "obj.coins" to 100,
                "obj.bread" to 5,
            )
    }
}
