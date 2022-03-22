import scala.sys.process.stringToProcess

enablePlugins(PlayScala)

name := "Cashy"
version := "0.0.1." + "git rev-parse --short HEAD".!!.trim + ".SNAPSHOT"

libraryDependencies ++= Seq(
  jdbc,
  evolutions,
  guice,
  "com.google.inject" % "guice" % "5.0.1",
  "com.google.oauth-client" % "google-oauth-client-java6" % "1.18.0-rc",
  "com.google.http-client" % "google-http-client" % "1.18.0-rc",
  "com.google.http-client" % "google-http-client-jackson2" % "1.18.0-rc",
  "com.google.api-client" % "google-api-client" % "1.18.0-rc",
  "com.lucidchart" %% "relate" % "3.0.0",
  "mysql" % "mysql-connector-java" % "5.1.23",
  "org.apache.httpcomponents" % "httpclient" % "4.3.6",
  "org.apache.commons" % "commons-email" % "1.3.3",
  "com.yahoo.platform.yui" % "yuicompressor" % "2.4.8",
  "software.amazon.awssdk" % "s3" % "2.17.152",
)

scalaVersion := "2.13.6"
scalacOptions ++= Seq("-feature", "-deprecation", "-Xfatal-warnings")

resolvers ++= List(
  "google-api-services" at "https://google-api-client-libraries.appspot.com/mavenrepo",
)

routesGenerator := InjectedRoutesGenerator
