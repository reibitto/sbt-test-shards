package sbttestshards

import sbt.Logger

final case class ShardContext(testShard: Int, testShardCount: Int, logger: Logger)
