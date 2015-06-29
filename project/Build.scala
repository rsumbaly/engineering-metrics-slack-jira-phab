import sbt._
import Keys._
import play.Project._

object ApplicationBuild 
  extends Build { 

  val appName         = "phabricator-report"
  val appVersion      = "0.0.1"

  val appDependencies = Seq(
    "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
    "net.databinder.dispatch" % "dispatch-core_2.10" % "0.11.2",
    "net.databinder.dispatch" % "dispatch-json4s-jackson_2.10" % "0.11.2",
    "com.google.inject" % "guice" % "3.0",
    "javax.inject" % "javax.inject" % "1",
    "com.typesafe.scala-logging" % "scala-logging-slf4j_2.10" % "2.1.2",
    "org.mockito" % "mockito-core" % "1.9.5" % "test",
    "com.tzavellas" % "sse-guice" % "0.7.1"
  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    requireJs += "main.js",
    requireJs += "barchart.js",
    scalacOptions += "-feature"
  )

}
