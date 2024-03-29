package sbttestshards

import munit.ScalaCheckSuite
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll
import org.scalacheck.Test

import java.time.Duration

class ShardingAlgorithmSpec extends ScalaCheckSuite {

  implicit val specInfoArbitrary: Arbitrary[SuiteInfo] = Arbitrary {
    for {
      specName  <- Gen.resize(30, Gen.alphaStr)
      timeTaken <- Gen.choose(0L, 5 * 60 * 1000)
    } yield SuiteInfo(specName, Some(Duration.ofMillis(timeTaken)))
  }

  override def scalaCheckTestParameters: Test.Parameters =
    super.scalaCheckTestParameters.withMinSuccessfulTests(10000)

  property(
    "each shard should be balanced so that the difference between two never exceed the maximum single suite time"
  ) {
    forAll { (tests: List[SuiteInfo]) =>
      val algo = ShardingAlgorithm.Balance(tests, ShardingInfo(3))

      val maxSuiteTime = tests.map(_.timeTaken.getOrElse(Duration.ZERO)).reduceOption { (a, b) =>
        if (a.compareTo(b) > 0) a else b
      }

      val bucketMap = algo.distributeEvenly.toSeq.groupBy(_._2).map { case (k, v) =>
        k -> v.map(_._1.timeTaken).reduceOption(_.plus(_)).getOrElse(Duration.ZERO)
      }

      bucketMap.values.toSeq
        .sliding(2)
        .map {
          case Seq(_) =>
            true

          case Seq(a, b) =>
            val difference = a.minus(b).abs()

            difference.compareTo(maxSuiteTime.getOrElse(Duration.ZERO)) <= 0
        }
        .reduceOption(_ && _)
        .getOrElse(true)
    }
  }

  property("only choose shard if initialDuration indicates node started early") {
    forAll { (tests: List[SuiteInfo]) =>
      val initialDurations = Map(0 -> Duration.ofMinutes(-1000))
      val algo = ShardingAlgorithm.Balance(tests, ShardingInfo(3, initialDurations))

      val bucketMap = algo.distributeEvenly.toSeq.groupBy(_._2).map { case (k, v) =>
        k -> v.map(_._1.timeTaken).reduceOption(_.plus(_)).getOrElse(Duration.ZERO)
      }

      if (tests.isEmpty)
        bucketMap.isEmpty
      else
        bucketMap.keys.toSeq == Seq(0)
    }
  }

  property("not choose shard if initialDuration exceeds the available time") {
    forAll { (tests: List[SuiteInfo]) =>
      val initialDurations = Map(0 -> Duration.ofMinutes(1000))
      val algo = ShardingAlgorithm.Balance(tests, ShardingInfo(3, initialDurations))

      val bucketMap = algo.distributeEvenly.toSeq.groupBy(_._2).map { case (k, v) =>
        k -> v.map(_._1.timeTaken).reduceOption(_.plus(_)).getOrElse(Duration.ZERO)
      }

      !bucketMap.contains(0)
    }
  }

  test("take initialDurations into account when distributing specs") {
    val initialDurations = Map(0 -> Duration.ofSeconds(10))
    val tests = List(
      SuiteInfo("spec1", timeTaken = Some(Duration.ofSeconds(1))),
      SuiteInfo("spec2", timeTaken = Some(Duration.ofSeconds(2))),
      SuiteInfo("spec3", timeTaken = Some(Duration.ofSeconds(3))),
      SuiteInfo("spec4", timeTaken = Some(Duration.ofSeconds(4))),
      SuiteInfo("spec5", timeTaken = Some(Duration.ofSeconds(5))),
      SuiteInfo("spec6", timeTaken = Some(Duration.ofSeconds(6))),
      SuiteInfo("spec7", timeTaken = Some(Duration.ofSeconds(7))),
      SuiteInfo("spec8", timeTaken = Some(Duration.ofSeconds(8))),
      SuiteInfo("spec9", timeTaken = Some(Duration.ofSeconds(9)))
    )

    val algo = ShardingAlgorithm.Balance(tests, ShardingInfo(3, initialDurations))

    val bucketMap = algo.distributeEvenly.toSeq.groupBy(_._2).map { case (k, v) =>
      k -> v.map(_._1.timeTaken).reduceOption(_.plus(_)).getOrElse(Duration.ZERO)
    }

    val bucketMapWithInitialDurations = bucketMap.map { case (k, v) =>
      k -> initialDurations.getOrElse(k, Duration.ZERO).plus(v)
    }

    assertEquals(
      bucketMapWithInitialDurations,
      Map(
        0 -> Duration.ofSeconds(19),
        1 -> Duration.ofSeconds(18),
        2 -> Duration.ofSeconds(18)
      )
    )
  }

}
