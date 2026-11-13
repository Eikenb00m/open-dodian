package org.rsmod.content.areas.city.yanille

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The door to the Yanille dungeon stairs. The map places an ordinary posh door there, so it is
 * swapped for a copy of its own at startup; that copy only lets players through who carry the
 * red key, and closes again right behind them.
 */
class YanilleDungeonDoorScript @Inject constructor(private val locRepo: LocRepository) :
    PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        onGameStartup { replaceMapDoor() }
        onOpLoc1(DUNGEON_DOOR) { useDoor(it.loc) }
        onOpLoc1(OPEN_DOOR) {}
    }

    private fun replaceMapDoor() {
        val mapDoor = MAP_DOOR.asRSCM(RSCMType.LOC)
        val door = locRepo.findAll(DOOR_COORDS).firstOrNull { it.id == mapDoor }
        if (door == null) {
            logger.warn { "Yanille dungeon: no '$MAP_DOOR' at $DOOR_COORDS; the door stays unlocked." }
            return
        }
        locRepo.del(door, Int.MAX_VALUE)
        locRepo.add(door.coords, DUNGEON_DOOR, Int.MAX_VALUE, door.angle, door.shape)
    }

    private suspend fun ProtectedAccess.useDoor(door: BoundLocInfo) {
        if (RED_KEY !in inv) {
            soundSynth(LOCKED_SOUND)
            mes("The door is locked.")
            return
        }
        mes("You unlock the door with the red key.")
        val dest = if (coords == door.coords) beyond(door) else door.coords
        val closed = LocInfo(door.layer, door.coords, door.entity)
        val openCoords = DoorTranslations.translateOpen(door.coords, door.shape, door.angle)
        locRepo.add(door.coords, INVISIBLE_WALL, Int.MAX_VALUE, door.angle, door.shape)
        locRepo.add(
            openCoords,
            OPEN_DOOR,
            OPEN_CYCLES,
            door.turnAngle(rotations = 1),
            door.shape,
            onDespawn = { locRepo.add(closed, Int.MAX_VALUE) },
        )
        soundSynth(OPEN_SOUND)
        forcedWalk(dest, crossTiles = 1)
    }

    private fun beyond(door: BoundLocInfo): CoordGrid =
        when (door.angle) {
            LocAngle.West -> door.coords.translateX(-1)
            LocAngle.North -> door.coords.translateZ(1)
            LocAngle.East -> door.coords.translateX(1)
            LocAngle.South -> door.coords.translateZ(-1)
        }

    private companion object {
        const val MAP_DOOR = "loc.poshdoor"
        const val DUNGEON_DOOR = "loc.yanille_dungeon_door"
        const val OPEN_DOOR = "loc.yanille_dungeon_door_open"
        const val RED_KEY = "obj.redkey"
        const val OPEN_SOUND = "synth.nicedoor_open"
        const val LOCKED_SOUND = "synth.irondoor_locked"
        const val OPEN_CYCLES = 3

        /** Holds the doorway while open: deleting the door would bring the map's posh door back. */
        const val INVISIBLE_WALL = "loc.inviswall"

        val DOOR_COORDS = CoordGrid(2603, 3082, 0)
    }
}
