package sbttestshards

import sbt.Keys.*
import sbt.*

object TestShardsPlugin extends AutoPlugin {
  object autoImport {
    val testShard         = settingKey[Int]("testShard")
    val testShardCount    = settingKey[Int]("testShardCount")
    val shardingAlgorithm = settingKey[ShardingAlgorithm]("shardingAlgorithm")
  }

  import autoImport.*

  override def trigger = allRequirements

  override lazy val projectSettings: Seq[Def.Setting[?]] =
    Seq(
      testShard         := 0,
      testShardCount    := 1,
      shardingAlgorithm := ShardingAlgorithm.SuiteName,
      Test / testOptions += {
        val shardContext = ShardContext(testShardCount.value, testShard.value, sLog.value)
        Tests.Filter(specName => shardingAlgorithm.value.isInShard(specName, shardContext))
      }
    )

}
