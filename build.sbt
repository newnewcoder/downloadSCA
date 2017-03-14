name := "downloadSCA"

version := "1.0"

scalaVersion := "2.11.7"

resolvers ++= Seq(
  "snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
  "releases" at "http://oss.sonatype.org/content/repositories/releases",
  "Typesafe" at "http://repo.typesafe.com/typesafe/releases"
)

scalacOptions ++= Seq("-deprecation", "-unchecked")

unmanagedClasspath in Runtime += baseDirectory.value / "src/main/scala"

libraryDependencies += "org.apache.httpcomponents" % "httpclient" % "4.5.1"
//libraryDependencies += "net.databinder.dispatch" %% "dispatch-core" % "0.11.2"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.2"
libraryDependencies += "com.typesafe" % "config" % "1.3.0"