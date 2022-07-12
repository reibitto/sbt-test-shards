package sbttestshards

import sbt.Keys.*
import sbt.*

object TestShardsPlugin extends AutoPlugin {
  object autoImport {
    val testShard         = settingKey[Int]("testShard")
    val testShardCount    = settingKey[Int]("testShardCount")
    val shardingAlgorithm = settingKey[ShardingAlgorithm]("shardingAlgorithm")
    val testShardDebug    = settingKey[Boolean]("testShardDebug")
  }

  import autoImport.*

  override def trigger = allRequirements

  def stringConfig(key: String, default: String): String = {
    val propertyKey = key.replace('_', '.').toLowerCase
    sys.props.get(propertyKey).orElse(sys.env.get(key)).getOrElse(default)
  }

  override lazy val projectSettings: Seq[Def.Setting[?]] =
    Seq(
      testShard         := stringConfig("TEST_SHARD", "0").toInt,
      testShardCount    := stringConfig("TEST_SHARD_COUNT", "1").toInt,
      shardingAlgorithm := ShardingAlgorithm.SuiteName,
      testShardDebug    := false,
      Test / testOptions += {
        val shardContext = ShardContext(testShard.value, testShardCount.value, sLog.value)
        Tests.Filter { specName =>
          val isInShard = shardingAlgorithm.value.shouldRun(specName, shardContext)

          if (testShardDebug.value) {
            if (isInShard) {
              sLog.value.info(s"`$specName` set to run on this shard.")
            } else {
              sLog.value.warn(s"`$specName` skipped because it will run on another shard.")
            }
          }

          isInShard
        }
      }
    )

}
