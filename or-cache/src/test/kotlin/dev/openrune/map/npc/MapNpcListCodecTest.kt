package dev.openrune.map.npc

import dev.openrune.map.util.InlineByteBuf
import io.netty.buffer.Unpooled
import it.unimi.dsi.fastutil.ints.IntArrayList
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapNpcListCodecTest {
    private fun encode(def: MapNpcListDefinition): ByteArray {
        val buf = Unpooled.buffer()
        MapNpcListEncoder.encode(def, buf)
        return ByteArray(buf.readableBytes()).also { buf.readBytes(it) }
    }

    private val spawns =
        IntArrayList(
            intArrayOf(
                MapNpcDefinition(1613, localX = 24, localZ = 33, level = 0).packed,
                MapNpcDefinition(3254, localX = 13, localZ = 34, level = 1).packed,
                MapNpcDefinition(65535, localX = 63, localZ = 63, level = 3).packed,
            ),
        )

    @Test
    fun `spawns without extras encode exactly as the old format`() {
        val bytes = encode(MapNpcListDefinition(spawns))
        assertEquals(2 + spawns.size * 4, bytes.size)
        val decoded = MapNpcListDecoder.decode(InlineByteBuf(bytes))
        assertEquals(spawns, decoded.packedSpawns)
        assertTrue((0 until spawns.size).all { decoded.extra(it).isNone })
    }

    @Test
    fun `facing and wander range round-trip per spawn`() {
        val extras =
            IntArrayList(
                intArrayOf(
                    MapNpcSpawnExtra(direction = 3, wanderRange = null).packed,
                    MapNpcSpawnExtra.NONE.packed,
                    MapNpcSpawnExtra(direction = 7, wanderRange = 0).packed,
                ),
            )
        val bytes = encode(MapNpcListDefinition(spawns, extras))
        val decoded = MapNpcListDecoder.decode(InlineByteBuf(bytes))
        assertEquals(spawns, decoded.packedSpawns)
        assertArrayEquals(extras.toIntArray(), decoded.extras.toIntArray())

        val first = decoded.extra(0)
        assertTrue(first.hasDirection && !first.hasWanderRange)
        assertEquals(3, first.direction)
        assertTrue(decoded.extra(1).isNone)
        assertEquals(0, decoded.extra(2).wanderRange)
        assertTrue(decoded.extra(2).hasWanderRange)
    }

    @Test
    fun `merge keeps extras aligned with their spawns`() {
        val base = MapNpcListDefinition(IntArrayList(intArrayOf(spawns.getInt(0))))
        val edit =
            MapNpcListDefinition(
                IntArrayList(intArrayOf(spawns.getInt(1))),
                IntArrayList(intArrayOf(MapNpcSpawnExtra(direction = 4, wanderRange = 2).packed)),
            )
        val merged = MapNpcListDefinition.merge(edit, base)
        assertTrue(merged.extra(0).isNone)
        assertEquals(4, merged.extra(1).direction)
        assertEquals(2, merged.extra(1).wanderRange)
    }
}
