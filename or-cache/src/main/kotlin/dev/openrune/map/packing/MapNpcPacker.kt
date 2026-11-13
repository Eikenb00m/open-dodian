package dev.openrune.map.packing

import dev.openrune.filesystem.Cache
import dev.openrune.map.npc.MapNpcDefinition
import dev.openrune.map.npc.MapNpcListDefinition
import dev.openrune.map.npc.MapNpcListEncoder
import dev.openrune.map.npc.MapNpcSpawnExtra
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.toml.decode
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import java.io.IOException
import java.nio.file.Path
import org.rsmod.game.map.Direction
import org.rsmod.map.square.MapSquareGrid
import org.rsmod.map.square.MapSquareKey

public object MapNpcPacker : MapSpawnPackerBase<MapNpcListDefinition>() {
    override val resourceDir: Path = Path.of("../.data/raw-cache/map/npcs")

    public fun encodeCacheMapNpc(cache: Cache) {
        MapNpcListEncoder.encodeAll(cache, loadAndCollect())
    }

    override fun decodeTomlSpawnFile(path: Path): List<Pair<MapSquareKey, MapNpcListDefinition>> {
        val parsed = toml.decode<TomlNpcSpawnFile>(path)

        val grouped = Int2ObjectOpenHashMap<IntArrayList>()
        val extras = Int2ObjectOpenHashMap<IntArrayList>()
        for (spawn in parsed.spawn) {
            val npc = spawn.npc.asRSCM()
            val coords = parseCoordGrid(spawn.coords)
            val grid = MapSquareGrid.from(coords)
            val def = MapNpcDefinition(npc, localX = grid.x, localZ = grid.z, level = grid.level)
            val mapSquare = MapSquareKey.from(coords)
            val spawnList = grouped.computeIfAbsent(mapSquare.id) { IntArrayList() }
            spawnList.add(def.packed)
            extras.computeIfAbsent(mapSquare.id) { IntArrayList() }.add(spawn.extra(path).packed)
        }
        return grouped.map { (square, spawns) ->
            val squareExtras = extras.get(square)
            val anyExtra = squareExtras.any { !MapNpcSpawnExtra(it).isNone }
            val def =
                if (anyExtra) {
                    MapNpcListDefinition(spawns, squareExtras)
                } else {
                    MapNpcListDefinition(spawns)
                }
            Pair(MapSquareKey(square), def)
        }
    }

    override fun mergeDefinitions(
        old: MapNpcListDefinition,
        new: MapNpcListDefinition,
    ): MapNpcListDefinition = MapNpcListDefinition.merge(old, new)

    private data class TomlNpcSpawnFile(val spawn: List<TomlNpcSpawn>)

    private data class TomlNpcSpawn(
        val npc: String,
        val coords: String,
        val facing: String? = null,
        val wander: Int? = null,
    ) {
        fun extra(path: Path): MapNpcSpawnExtra {
            val direction =
                facing?.let { name ->
                    Direction.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                        ?: throw IOException("Unknown facing \"$name\" for $npc in $path")
                }
            if (wander != null && wander !in 0 until MapNpcSpawnExtra.UNSET) {
                throw IOException("wander must be 0..${MapNpcSpawnExtra.UNSET - 1} for $npc in $path")
            }
            return MapNpcSpawnExtra(direction, wander)
        }
    }
}
