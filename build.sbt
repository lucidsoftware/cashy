import scala.sys.process.stringToProcess

enablePlugins(PlayScala)

name := "Cashy"

libraryDependencies ++= Seq(
  jdbc,
  "com.google.oauth-client" % "google-oauth-client-java6" % "1.18.0-rc",
  "com.google.http-client" % "google-http-client" % "1.18.0-rc",
  "com.google.http-client" % "google-http-client-jackson2" % "1.18.0-rc",
  "com.google.api-client" % "google-api-client" % "1.18.0-rc",
  "com.lucidchart" %% "relate" % "1.7.1",
  "mysql" % "mysql-connector-java" % "5.1.23",
  "org.apache.httpcomponents" % "httpclient" % "4.3.6",
  "org.apache.commons" % "commons-email" % "1.3.3",
  "com.yahoo.platform.yui" % "yuicompressor" % "2.4.8",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.678"
)

scalaVersion := "2.11.8"
scalacOptions ++= Seq("-feature", "-deprecation", "-Xfatal-warnings")
resolvers ++= List(
  "google-api-services" at "https://google-api-client-libraries.appspot.com/mavenrepo"
)
routesGenerator := InjectedRoutesGenerator
version := "0.0.1." + "git rev-parse --short HEAD".!!.trim + ".SNAPSHOT"

routesGenerator := InjectedRoutesGenerator
