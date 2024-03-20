import sbtwelcome.*

inThisBuild(
  List(
    organization := "com.github.reibitto",
    homepage := Some(url("https://github.com/reibitto/sbt-test-shards")),
    licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer("reibitto", "reibitto", "reibitto@users.noreply.github.com", url("https://reibitto.github.io"))
    )
  )
)

lazy val root = (project in file(".")).settings(
  name := "sbt-test-shards",
  organization := "com.github.reibitto",
  scalaVersion := "2.12.19",
  sbtPlugin := true,
  libraryDependencies ++= Seq(
    "org.scalameta" %% "munit" % "0.7.29" % Test,
    "org.scalameta" %% "munit-scalacheck" % "0.7.29" % Test
  )
)

addCommandAlias("fmt", "all root/scalafmtSbt root/scalafmtAll")
addCommandAlias("fmtCheck", "all root/scalafmtSbtCheck root/scalafmtCheckAll")

logo :=
  s"""
     |                              ______ _____
     |                       __________  /___  /_
     |                       __  ___/_  __ \\  __/
     |                       _(__  )_  /_/ / /_
     |                       /____/ /_.___/\\__/
     |  _____            _____           ______               _________
     |  __  /______________  /_   __________  /_______ _____________  /_______
     |  _  __/  _ \\_  ___/  __/   __  ___/_  __ \\  __ `/_  ___/  __  /__  ___/
     |  / /_ /  __/(__  )/ /_     _(__  )_  / / / /_/ /_  /   / /_/ / _(__  )
     |  \\__/ \\___//____/ \\__/     /____/ /_/ /_/\\__,_/ /_/    \\__,_/  /____/
     |
     |${version.value}
     |
     |${scala.Console.YELLOW}Scala ${scalaVersion.value}${scala.Console.RESET}
     |
     |""".stripMargin

usefulTasks := Seq(
  UsefulTask("~compile", "Compile with file-watch enabled"),
  UsefulTask("fmt", "Run scalafmt on the entire project"),
  UsefulTask("publishLocal", "Publish the sbt plugin locally so that you can consume it from a different project")
)

logoColor := scala.Console.MAGENTA

ThisBuild / organization := "com.github.reibitto"
