package sbttestshards

import sbttestshards.parsers.JUnitReportParser

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration
import scala.util.hashing.MurmurHash3

// This trait is open so that users can implement a custom `ShardingAlgorithm` if they'd like
trait ShardingAlgorithm {

  /** Determines whether the specified spec will run on this shard or not. */
  def shouldRun(specName: String, shardContext: ShardContext): Boolean
}

object ShardingAlgorithm {

  /** Shards by suite the name. This is the most reasonable default as it
    * requires no additional setup.
    */
  final case object SuiteName extends ShardingAlgorithm {

    override def shouldRun(specName: String, shardContext: ShardContext): Boolean =
      MurmurHash3
        .bytesHash(specName.getBytes(StandardCharsets.UTF_8))
        .abs % shardContext.testShardCount == shardContext.testShard
  }

  /** Will always mark the test to run on this shard. Useful for debugging or
    * for fallback algorithms.
    */
  final case object Always extends ShardingAlgorithm {
    override def shouldRun(specName: String, shardContext: ShardContext): Boolean = true
  }

  /** Will never mark the test to run on this shard. Useful for debugging or for
    * fallback algorithms.
    */
  final case object Never extends ShardingAlgorithm {
    override def shouldRun(specName: String, shardContext: ShardContext): Boolean = false
  }

  object Balance {

    def fromJUnitReports(
        reportDirectories: Seq[Path],
        shardsInfo: ShardingInfo,
        fallbackShardingAlgorithm: ShardingAlgorithm = ShardingAlgorithm.SuiteName
    ): Balance =
      ShardingAlgorithm.Balance(
        JUnitReportParser.parseDirectoriesRecursively(reportDirectories).testReports.map { r =>
          SpecInfo(r.name, Some(Duration.ofMillis(r.timeTaken.toLong)))
        },
        shardsInfo,
        fallbackShardingAlgorithm
      )
  }

  /** Attempts to balance the shards by execution time so that no one shard
    * takes significantly longer to complete than another.
    */
  final case class Balance(
      specs: Seq[SpecInfo],
      shardsInfo: ShardingInfo,
      fallbackShardingAlgorithm: ShardingAlgorithm = ShardingAlgorithm.SuiteName
  ) extends ShardingAlgorithm {

    // TODO: Median might be better here?
    private val averageTime: Option[Duration] = {
      val allTimeTaken = specs.flatMap(_.timeTaken)

      allTimeTaken.reduceOption(_.plus(_)).map { d =>
        if (allTimeTaken.isEmpty) Duration.ZERO
        else d.dividedBy(allTimeTaken.length)
      }
    }

    // TODO: This uses a naive greedy algorithm for partitioning into approximately equal subsets. While this problem
    // is NP-complete, there's a lot of room for improvement with other algorithms. Dynamic programming should be
    // possible here.
    def distributeEvenly: Map[TestSuiteInfoSimple, Int] = {
      val durationOrdering: Ordering[Duration] = (a: Duration, b: Duration) => a.compareTo(b)

      val allTests = specs
        .map(t => TestSuiteInfoSimple(t.name, t.timeTaken.getOrElse(averageTime.getOrElse(Duration.ZERO))))
        .sortBy(_.timeTaken)(durationOrdering.reverse)

      val buckets = (0 until shardsInfo.shardCount).map { shardIndex =>
        TestBucket(
          tests = Nil,
          sum = shardsInfo.initialDurations.getOrElse(shardIndex, Duration.ZERO)
        )
      }.toArray

      allTests.foreach { test =>
        val minBucket = buckets.minBy(_.sum)

        minBucket.tests = test :: minBucket.tests
        minBucket.sum = minBucket.sum.plus(test.timeTaken)
      }

      buckets.zipWithIndex.flatMap { case (bucket, i) =>
        bucket.tests.map { info =>
          info -> i
        }
      }.toMap
    }

    private val bucketMap: Map[String, Int] = distributeEvenly.map { case (k, v) =>
      k.name -> v
    }

    def shouldRun(specName: String, shardContext: ShardContext): Boolean =
      bucketMap.get(specName) match {
        case Some(bucketIndex) => bucketIndex == shardContext.testShard
        case None              => fallbackShardingAlgorithm.shouldRun(specName, shardContext)
      }
  }

  final case class TestSuiteInfoSimple(name: String, timeTaken: Duration)

  final private case class TestBucket(var tests: List[TestSuiteInfoSimple], var sum: Duration)
}
