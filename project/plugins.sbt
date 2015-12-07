// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository 
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
resolvers += "Java.net Maven2 Repository" at "http://download.java.net/maven/2/"
resolvers += "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots"
resolvers += "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases"
resolvers += "Maven Repository" at "http://mvnrepository.com/artifact"

// Use the Play sbt plugin for Play projects
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.4.0")
addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.1")
