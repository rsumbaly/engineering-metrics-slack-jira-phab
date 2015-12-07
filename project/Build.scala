import sbt._
import Keys._
import play.Play.autoImport._
import PlayKeys._

object ApplicationBuild 
  extends Build { 

  val appName         = "phabricator-report"
  val appVersion      = "0.0.2"

  val appDependencies = Seq(
    "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
    "net.databinder.dispatch" % "dispatch-core_2.10" % "0.11.2",
    "net.databinder.dispatch" % "dispatch-json4s-jackson_2.10" % "0.11.2",
    "com.google.inject" % "guice" % "4.0",
    "javax.inject" % "javax.inject" % "1",
    "com.typesafe.scala-logging" % "scala-logging-slf4j_2.10" % "2.1.2",
    "org.mockito" % "mockito-core" % "1.9.5" % "test",
    "net.gpedro.integrations.slack" % "slack-webhook" % "1.1.1",
    "io.netty" % "netty" % "3.9.2.Final" force()
  )

  val main = Project(appName, file(".")).enablePlugins(play.PlayScala).settings(
    version := appVersion,
    libraryDependencies ++= appDependencies,
    scalacOptions += "-feature"
  )

}
