package org.rsmod.api.death

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.realm.Realm
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

@Singleton
public class PlayerRespawnPoint
@Inject
constructor(private val respawnHooks: Set<PlayerRespawnHook>, private val realm: Realm) {
    public val home: CoordGrid
        get() = realm.config.respawnCoord

    public fun hooked(player: Player): CoordGrid? =
        respawnHooks.firstNotNullOfOrNull { it.respawnCoords(player) }

    public fun coords(player: Player): CoordGrid = hooked(player) ?: home
}
