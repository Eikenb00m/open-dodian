package org.rsmod.game.cheat

import org.rsmod.game.entity.Player

public data class Cheat(
    public val player: Player,
    public val command: String,
    public val args: List<String>,
    /** [args] as the player typed them, before the command was lowercased. */
    public val rawArgs: List<String> = args,
)
