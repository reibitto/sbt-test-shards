package sbttestshards

import java.time.Duration

// This trait is open so that users can implement a custom `ShardingAlgorithm` if they'd like
trait ShardingAlgorithm {

  /** Determines whether the specified spec will run on this shard or not. */
  def shouldRun(specName: String, shardContext: ShardContext): Boolean
}

object ShardingAlgorithm {

  /** Shards by suite the name. This is the most reasonable default as it requires no additional setup. */
  final case object SuiteName extends ShardingAlgorithm {
    override def shouldRun(specName: String, shardContext: ShardContext): Boolean =
      // TODO: Test whether `hashCode` gets a good distribution. Otherwise implement a different hash algorithm.
      specName.hashCode.abs % shardContext.testShardCount == shardContext.testShard
  }

  /** Will always mark the test to run on this shard. Useful for debugging or for fallback algorithms. */
  final case object Always extends ShardingAlgorithm {
    override def shouldRun(specName: String, shardContext: ShardContext): Boolean = true
  }

  /** Will never mark the test to run on this shard. Useful for debugging or for fallback algorithms. */
  final case object Never extends ShardingAlgorithm {
    override def shouldRun(specName: String, shardContext: ShardContext): Boolean = false
  }

  /** Attempts to balance the shards by execution time so that no one shard takes significantly longer to complete than
    * another.
    */
  final case class Balance(
    tests: List[TestSuiteInfo],
    bucketCount: Int,
    fallbackShardingAlgorithm: ShardingAlgorithm = ShardingAlgorithm.SuiteName
  ) extends ShardingAlgorithm {
    // TODO: Median might be better here?
    private val averageTime: Option[Duration] = {
      val allTimeTaken = tests.flatMap(_.timeTaken)
      allTimeTaken.reduceOption(_.plus(_)).map { d =>
        if (allTimeTaken.isEmpty) Duration.ZERO
        else d.dividedBy(allTimeTaken.length)
      }
    }

    // TODO: This uses a naive greedy algorithm for partitioning into approximately equal subsets. While this problem
    // is NP-complete, there's a lot of room for improvement with other algorithms. Dynamic programming should be
    // possible here.
    private def createBucketMap(testShardCount: Int) = {
      val durationOrdering: Ordering[Duration] = (a: Duration, b: Duration) => a.compareTo(b)

      val allTests = tests
        .map(t => TestSuiteInfoSimple(t.name, t.timeTaken.getOrElse(averageTime.getOrElse(Duration.ZERO))))
        .sortBy(_.timeTaken)(durationOrdering.reverse)

      val buckets = Array.fill(testShardCount)(TestBucket(Nil, Duration.ZERO))

      allTests.foreach { test =>
        val minBucket = buckets.minBy(_.sum)

        minBucket.tests = test :: minBucket.tests
        minBucket.sum = minBucket.sum.plus(test.timeTaken)
      }

      buckets.zipWithIndex.flatMap { case (bucket, i) =>
        bucket.tests.map { info =>
          info.name -> i
        }
      }.toMap
    }

    // `bucketCount` doesn't necessary need to match `testShardCount`, but ideally it should be a multiple of it.
    // TODO: Maybe print a warning if it's not a multiple of it.
    private val bucketMap: Map[String, Int] = createBucketMap(bucketCount)

    def shouldRun(specName: String, shardContext: ShardContext): Boolean =
      bucketMap.get(specName) match {
        case Some(bucketIndex) => bucketIndex == shardContext.testShard
        case None              => fallbackShardingAlgorithm.shouldRun(specName, shardContext)
      }
  }

  private final case class TestSuiteInfoSimple(name: String, timeTaken: Duration)
  private final case class TestBucket(var tests: List[TestSuiteInfoSimple], var sum: Duration)
}
