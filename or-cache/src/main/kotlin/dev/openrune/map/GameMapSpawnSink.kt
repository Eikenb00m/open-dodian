package dev.openrune.map

import dev.openrune.map.npc.MapNpcDefinition
import dev.openrune.map.npc.MapNpcSpawnExtra
import dev.openrune.map.obj.MapObjDefinition
import org.rsmod.map.CoordGrid

public interface GameMapSpawnSink {
    public fun onNpcSpawn(def: MapNpcDefinition, coords: CoordGrid)

    /** A spawn with its own facing or wander range; the default ignores them. */
    public fun onNpcSpawn(def: MapNpcDefinition, coords: CoordGrid, extra: MapNpcSpawnExtra): Unit =
        onNpcSpawn(def, coords)

    public fun onObjSpawn(def: MapObjDefinition, coords: CoordGrid)
}
