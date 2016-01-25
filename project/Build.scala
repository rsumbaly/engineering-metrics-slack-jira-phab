import sbt._
import Keys._
import play.Play.autoImport._
import PlayKeys._
import com.typesafe.sbt.SbtNativePackager.autoImport._

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
    "org.scribe" % "scribe" % "1.3.7",
    "org.quartz-scheduler" % "quartz" % "2.2.2",
    "com.atlassian.jira" % "jira-rest-java-client-core" % "3.0.0",
    "com.atlassian.jira" % "jira-rest-java-client-api" % "3.0.0",
    "net.oauth.core" % "oauth" % "20100527",
    "net.oauth.core" % "oauth-consumer" % "20100527",
    "net.oauth.core" % "oauth-httpclient4" % "20090913",
    "io.netty" % "netty" % "3.9.2.Final" force()
  )

  val main = Project(appName, file(".")).enablePlugins(play.PlayScala).settings(
    version := appVersion,
    libraryDependencies ++= appDependencies,
    scalacOptions += "-feature",
    resolvers += "Atlassian" at "https://maven.atlassian.com/content/repositories/atlassian-public",
    resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
    resolvers += "Java.net Maven2 Repository" at "http://download.java.net/maven/2/",
    resolvers += "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
    resolvers += "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases",
    resolvers += "Maven Repository" at "http://mvnrepository.com/artifact",
    resolvers += "Maven 2 Repository" at "http://repo1.maven.org/maven2", 
    resolvers += "OAuth specific" at "http://oauth.googlecode.com/svn/code/maven/"
  )

}
