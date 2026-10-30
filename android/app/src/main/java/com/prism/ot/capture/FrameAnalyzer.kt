package com.prism.ot.capture

import com.prism.ot.core.PrismResult
import com.prism.ot.data.model.ChannelFeatureVector
import com.prism.ot.data.model.DecodedTile
import com.prism.ot.data.model.FrameData
import com.prism.ot.ml.model.DecodabilityScore

/**
 * Turns one camera frame into zero or more decoded fountain packets.
 *
 * Implementations must be safe to call from a single analyzer thread.
 * Parallelism across tiles is the implementation's responsibility; the
 * caller does not fan out frames.
 */
interface FrameAnalyzer {
    suspend fun analyze(frame: FrameData): PrismResult<FrameAnalysisResult>
}

data class FrameAnalysisResult(
    val decodedTiles: List<DecodedTile>,
    val channelFeatures: ChannelFeatureVector,
    val decodability: DecodabilityScore,
    val detections: Int,
)

/**
 * Callback invoked for each successfully decoded tile. The session layer
 * (Batch 8) wires this to the codec's fountain decoder.
 */
fun interface TileSink {
    suspend fun onTileDecoded(tile: DecodedTile)
}
