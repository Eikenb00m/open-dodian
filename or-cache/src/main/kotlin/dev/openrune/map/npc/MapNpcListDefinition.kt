package dev.openrune.map.npc

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.ints.IntLists

/**
 * [extras] runs parallel to [packedSpawns] with per-spawn [MapNpcSpawnExtra] values; an empty
 * list means no spawn in the map square has any.
 */
public data class MapNpcListDefinition(
    public val packedSpawns: IntList,
    public val extras: IntList = IntLists.EMPTY_LIST,
) {
    public fun extra(index: Int): MapNpcSpawnExtra =
        if (index < extras.size) MapNpcSpawnExtra(extras.getInt(index)) else MapNpcSpawnExtra.NONE

    public companion object {
        public fun merge(
            edit: MapNpcListDefinition,
            base: MapNpcListDefinition,
        ): MapNpcListDefinition {
            val merged = IntArrayList(base.packedSpawns.size + edit.packedSpawns.size)
            merged.addAll(base.packedSpawns)
            merged.addAll(edit.packedSpawns)
            if (base.extras.isEmpty() && edit.extras.isEmpty()) {
                return MapNpcListDefinition(merged)
            }
            val extras = IntArrayList(merged.size)
            for (i in 0 until base.packedSpawns.size) extras.add(base.extra(i).packed)
            for (i in 0 until edit.packedSpawns.size) extras.add(edit.extra(i).packed)
            return MapNpcListDefinition(merged, extras)
        }
    }
}
