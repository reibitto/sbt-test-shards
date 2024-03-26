package sbttestshards

import sbt.*
import sbt.Keys.*
import sbttestshards.parsers.JUnitReportParser

import java.nio.file.Paths

object TestShardsPlugin extends AutoPlugin {

  object autoImport {
    val testShard = settingKey[Int]("testShard")
    val testShardCount = settingKey[Int]("testShardCount")
    val shardingAlgorithm = settingKey[ShardingAlgorithm]("shardingAlgorithm")
    val testShardDebug = settingKey[Boolean]("testShardDebug")
    val testDryRun = inputKey[Unit]("testDryRun")
  }

  import autoImport.*

  override def trigger = allRequirements

  def stringConfig(key: String, default: String): String = {
    val propertyKey = key.replace('_', '.').toLowerCase
    sys.props.get(propertyKey).orElse(sys.env.get(key)).getOrElse(default)
  }

  override lazy val projectSettings: Seq[Def.Setting[?]] =
    Seq(
      testShard := stringConfig("TEST_SHARD", "0").toInt,
      testShardCount := stringConfig("TEST_SHARD_COUNT", "1").toInt,
      shardingAlgorithm := ShardingAlgorithm.SuiteName,
      testShardDebug := false,
      Test / testOptions += {
        val shardContext = ShardContext(testShard.value, testShardCount.value, sLog.value)

        Tests.Filter { suiteName =>
          val isInShard = shardingAlgorithm.value.shouldRun(suiteName, shardContext)

          if (testShardDebug.value)
            if (isInShard)
              sLog.value.info(s"`$suiteName` set to run on this shard (#${testShard.value}).")
            else
              sLog.value.warn(s"`$suiteName` skipped because it will run on another shard.")

          isInShard
        }
      },
      testDryRun := {
        val shardContext = ShardContext(testShard.value, testShardCount.value, sLog.value)
        val logger = shardContext.logger
        val algorithm = shardingAlgorithm.value

        // TODO:: Make path customizable
        val fullTestReport = JUnitReportParser.parseDirectoriesRecursively(
          Seq(Paths.get(s"test-reports/main/resources/test-reports", moduleName.value))
        )

        val sbtSuiteNames = (Test / definedTestNames).value.toSet
        val missingSuiteNames = sbtSuiteNames diff fullTestReport.testReports.map(_.name).toSet

        val results = fullTestReport.testReports.map { suiteReport =>
          val shardResult = algorithm.check(suiteReport.name, shardContext)

          shardResult.testShard -> suiteReport
        }.collect { case (Some(shard), report) => shard -> report }
          .groupBy(_._1)

        results.toSeq.sortBy(_._1).foreach { case (k, v) =>
          val totalTime = v.map(_._2.timeTaken).sum

          logger.info(s"[${moduleName.value}] Shard $k expected to take $totalTime s")

          v.map(_._2).foreach { suiteReport =>
            logger.info(s"* ${suiteReport.name} = ${suiteReport.timeTaken} s")
          }
        }

        if(missingSuiteNames.nonEmpty) {
          logger.warn(s"Detected ${missingSuiteNames.size} suites that don't have a test report")

          missingSuiteNames.foreach { s =>
            logger.warn(s"- $s")
          }
        }
      }
    )

}
