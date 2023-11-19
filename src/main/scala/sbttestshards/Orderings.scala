package sbttestshards

import java.time.Duration

object Orderings {
  val duration: Ordering[Duration] = (a: Duration, b: Duration) => a.compareTo(b)
}
