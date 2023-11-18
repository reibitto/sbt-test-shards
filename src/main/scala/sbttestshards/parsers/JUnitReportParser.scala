package sbttestshards.parsers

import java.nio.file.Files
import java.nio.file.Path
import scala.jdk.CollectionConverters.*
import scala.xml.XML

final case class FullTestReport(testReports: Seq[SpecTestReport]) {
  def specCount: Int = testReports.length

  def testCount: Int = testReports.map(_.testCount).sum

  def allPassed: Boolean = testReports.forall(!_.hasFailures)

  def badTestCount: Int = testReports.map(_.badTestCount).sum

  def hasFailures: Boolean = testReports.exists(_.hasFailures)

  def ++(other: FullTestReport): FullTestReport = FullTestReport(testReports ++ other.testReports)
}

final case class SpecTestReport(
    name: String,
    testCount: Int,
    errorCount: Int,
    failureCount: Int,
    skipCount: Int,
    timeTaken: Double,
    failedTests: Seq[String]
) {
  def badTestCount: Int = failureCount + errorCount

  def successCount: Int = testCount - errorCount - failureCount - skipCount

  def hasFailures: Boolean = failedTests.nonEmpty
}

object JUnitReportParser {

  def listReports(reportDirectory: Path): Iterator[Path] =
    Files.list(reportDirectory).iterator().asScala.filter(_.toString.endsWith(".xml"))

  def listReportsRecursively(reportDirectory: Path): Iterator[Path] =
    Files.walk(reportDirectory).iterator().asScala.filter(_.toString.endsWith(".xml"))

  def parseDirectories(reportDirectories: Seq[Path]): FullTestReport =
    FullTestReport(
      reportDirectories.flatMap { dir =>
        listReports(dir).map { reportFile =>
          parseReport(reportFile)
        }
      }
    )

  def parseDirectoriesRecursively(reportDirectories: Seq[Path]): FullTestReport =
    FullTestReport(
      reportDirectories.flatMap { dir =>
        listReportsRecursively(dir).map { reportFile =>
          parseReport(reportFile)
        }
      }
    )

  def parseReport(reportFile: Path): SpecTestReport = {
    val xml = XML.loadFile(reportFile.toFile)

    val specName = xml \@ "name"
    val testCount = (xml \@ "tests").toInt
    val errorCount = (xml \@ "errors").toInt
    val failureCount = (xml \@ "failures").toInt
    val skipCount = (xml \@ "skipped").toInt
    val timeTaken = (xml \@ "time").toDouble

    val testcaseNodes = xml \ "testcase"

    val failedTests = testcaseNodes.map { node =>
      if ((node \ "failure").nonEmpty) {
        val testName = (node \@ "name").trim

        Some(testName)
      } else {
        None
      }
    }.collect { case Some(testName) =>
      testName
    }

    SpecTestReport(
      name = specName,
      testCount = testCount,
      errorCount = errorCount,
      failureCount = failureCount,
      skipCount = skipCount,
      timeTaken = timeTaken,
      failedTests = failedTests
    )
  }
}