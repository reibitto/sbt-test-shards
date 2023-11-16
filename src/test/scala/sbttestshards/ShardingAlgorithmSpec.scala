package sbttestshards

import munit.ScalaCheckSuite
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll
import org.scalacheck.Test

import java.time.Duration

class ShardingAlgorithmSpec extends ScalaCheckSuite {

  implicit val specInfoArbitrary: Arbitrary[SpecInfo] = Arbitrary {
    for {
      specName  <- Gen.resize(30, Gen.alphaStr)
      timeTaken <- Gen.choose(0L, 9 * 60 * 1000)
    } yield SpecInfo(specName, Some(Duration.ofMillis(timeTaken)))
  }

  override def scalaCheckTestParameters: Test.Parameters =
    super.scalaCheckTestParameters.withMinSuccessfulTests(10000)

  property(
    "each shard should be balanced so that the difference between two never exceed the maximum single spec time"
  ) {
    forAll { (tests: List[SpecInfo]) =>
      val algo = ShardingAlgorithm.Balance(tests, ShardingInfo(3))

      val maxSpecTime = tests.map(_.timeTaken.getOrElse(Duration.ZERO)).reduceOption { (a, b) =>
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

            difference.compareTo(maxSpecTime.getOrElse(Duration.ZERO)) <= 0
        }
        .reduceOption(_ && _)
        .getOrElse(true)
    }
  }

}
