package com.pureenhance.ai.ai.tiling

import kotlin.math.min

/**
 * One tile. The "core" is the region this tile is responsible for in the output; the network sees a
 * [tileSize]² window starting at (originX, originY) that contains the core plus context padding.
 */
data class Tile(
    val coreX: Int, val coreY: Int, val coreW: Int, val coreH: Int,
    val originX: Int, val originY: Int,
)

object TilePlanner {
    /**
     * Cores tile the image exactly once (no overlap in the *output*), while the input windows overlap by
     * 2·[pad] pixels. Only the interior of each network output is kept, so no seams can appear.
     */
    fun plan(width: Int, height: Int, tileSize: Int, pad: Int): List<Tile> {
        val core = tileSize - 2 * pad
        require(core > 0) { "pad too large for tile" }
        val tiles = ArrayList<Tile>()
        var y = 0
        while (y < height) {
            val ch = min(core, height - y)
            var x = 0
            while (x < width) {
                val cw = min(core, width - x)
                val ox = if (width <= tileSize) 0 else (x - pad).coerceIn(0, width - tileSize)
                val oy = if (height <= tileSize) 0 else (y - pad).coerceIn(0, height - tileSize)
                tiles += Tile(x, y, cw, ch, ox, oy)
                x += cw
            }
            y += ch
        }
        return tiles
    }
}
