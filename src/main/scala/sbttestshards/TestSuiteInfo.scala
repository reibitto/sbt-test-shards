package sbttestshards

import java.time.Duration

final case class TestSuiteInfo(name: String, timeTaken: Option[Duration])
