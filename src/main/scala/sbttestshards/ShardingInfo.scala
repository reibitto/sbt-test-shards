package sbttestshards

import java.time.Duration

final case class ShardingInfo(shardCount: Int, initialDurations: Map[Int, Duration] = Map.empty)
