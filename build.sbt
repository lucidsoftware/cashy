import scala.sys.process.stringToProcess

enablePlugins(PlayScala)
enablePlugins(SystemdPlugin)

name := "Cashy"
version := "0.0.1." + "git rev-parse --short HEAD".!!.trim + ".SNAPSHOT"

maintainer := "Lucid Software"
libraryDependencies ++= Seq(
  jdbc,
  evolutions,
  guice,
  "com.google.inject" % "guice" % "5.0.1",
  "com.google.oauth-client" % "google-oauth-client" % "1.30.4",
  "com.google.http-client" % "google-http-client-jackson2" % "1.32.1",
  "com.lucidchart" %% "relate" % "3.0.0",
  "mysql" % "mysql-connector-java" % "8.0.28",
  "org.apache.httpcomponents" % "httpclient" % "4.5.13",
  "org.apache.commons" % "commons-email" % "1.5",
  "com.yahoo.platform.yui" % "yuicompressor" % "2.4.8",
  "software.amazon.awssdk" % "s3" % "2.17.152",
)

Debian/linuxPackageMappings ~= {
  _.map { mapping =>
    val filtered = mapping.mappings.filterNot {
      case (_, name) => name.endsWith("overrides.conf")
    }
    mapping.copy(mappings = filtered)
  }.filter {
    _.mappings.nonEmpty
  }
}

scalaVersion := "2.13.6"
scalacOptions ++= Seq("-feature", "-deprecation", "-Xfatal-warnings")

resolvers ++= List(
  "google-api-services" at "https://google-api-client-libraries.appspot.com/mavenrepo",
)

routesGenerator := InjectedRoutesGenerator
