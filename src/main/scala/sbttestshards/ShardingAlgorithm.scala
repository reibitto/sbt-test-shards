package sbttestshards

import java.time.Duration

// This trait is open so that users can implement a custom `ShardingAlgorithm` if they'd like
trait ShardingAlgorithm {
  def isInShard(specName: String, shardContext: ShardContext): Boolean
}

object ShardingAlgorithm {
  final case object Always extends ShardingAlgorithm {
    override def isInShard(specName: String, shardContext: ShardContext): Boolean = true
  }

  final case object Never extends ShardingAlgorithm {
    override def isInShard(specName: String, shardContext: ShardContext): Boolean = false
  }

  final case object SuiteName extends ShardingAlgorithm {
    override def isInShard(specName: String, shardContext: ShardContext): Boolean = {
      val shouldRun = specName.hashCode % shardContext.testShardCount == shardContext.testShard

      println(s"${specName} will run? ${shouldRun}")

      shouldRun
    }
  }

  final case class Balance(
    tests: List[TestSuiteInfo],
    bucketCount: Int,
    fallbackShardingAlgorithm: ShardingAlgorithm = ShardingAlgorithm.SuiteName
  ) extends ShardingAlgorithm {
    // TODO: Median might be better here?
    private val averageTime: Option[Duration] = {
      val allTimeTaken = tests.flatMap(_.timeTaken)
      allTimeTaken.reduceOption(_.plus(_)).map { d =>
        if (d.isZero) Duration.ZERO
        else d.dividedBy(allTimeTaken.length)
      }
    }

    private final case class TestSuiteInfoSimple(name: String, timeTaken: Duration)
    private final case class TestBucket(var tests: List[TestSuiteInfoSimple], var sum: Duration)

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

    def isInShard(specName: String, shardContext: ShardContext): Boolean =
      bucketMap.get(specName) match {
        case Some(bucketIndex) => bucketIndex == shardContext.testShard
        case None              => fallbackShardingAlgorithm.isInShard(specName, shardContext)
      }
  }
}
