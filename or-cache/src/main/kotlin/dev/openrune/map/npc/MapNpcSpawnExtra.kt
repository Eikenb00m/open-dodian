package dev.openrune.map.npc

/** Optional per-spawn facing (a `Direction` id) and wander range; [UNSET] keeps the npc type's. */
@JvmInline
public value class MapNpcSpawnExtra(public val packed: Int) {
    public val direction: Int
        get() = packed and 0xFF

    public val wanderRange: Int
        get() = (packed shr 8) and 0xFF

    public val hasDirection: Boolean
        get() = direction != UNSET

    public val hasWanderRange: Boolean
        get() = wanderRange != UNSET

    public val isNone: Boolean
        get() = !hasDirection && !hasWanderRange

    public constructor(
        direction: Int?,
        wanderRange: Int?,
    ) : this((((wanderRange ?: UNSET) and 0xFF) shl 8) or ((direction ?: UNSET) and 0xFF))

    public companion object {
        public const val UNSET: Int = 0xFF
        public val NONE: MapNpcSpawnExtra = MapNpcSpawnExtra(null, null)
    }
}
