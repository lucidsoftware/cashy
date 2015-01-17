import sbt._
import Keys._
import play.PlayScala
import play.PlayImport._
import java.io.PrintWriter
import java.io.File

object ApplicationBuild extends Build {

  val appName         = "Cashy"
  val appVersion      = "0.0.1." + "git rev-parse --short HEAD".!!.trim + ".SNAPSHOT"

  val appDependencies = Seq(
    jdbc,
    "com.google.oauth-client" % "google-oauth-client-java6" % "1.18.0-rc",
    "com.google.http-client" % "google-http-client" % "1.18.0-rc",
    "com.google.http-client" % "google-http-client-jackson2" % "1.18.0-rc",
    "com.google.api-client" % "google-api-client" % "1.18.0-rc",
    "com.lucidchart" %% "relate" % "1.7.1",
    "mysql" % "mysql-connector-java" % "5.1.23",
    "org.apache.httpcomponents" % "httpclient" % "4.3.6",
    "org.apache.commons" % "commons-email" % "1.3.3",
    "com.amazonaws" % "aws-java-sdk" % "1.9.13"
  )

  val main = Project(appName, file(".")).enablePlugins(PlayScala).settings(
      javacOptions in Compile ++= Seq("-source", "1.6", "-target", "1.6"),
      libraryDependencies ++= appDependencies,
      scalaVersion := "2.11.2",
      scalacOptions ++= Seq("-feature", "-deprecation", "-Xfatal-warnings"),
      resolvers ++= List(
        "lucidchart release repository" at "http://repo.lucidchart.com:8081/artifactory/libs-release-local",
        "lucidchart external repository" at "http://repo.lucidchart.com:8081/artifactory/ext-release-local",
        "lucidchart snapshot repository" at "http://repo.lucidchart.com:8081/artifactory/libs-snapshot-local",
        "google-api-services" at "http://google-api-client-libraries.appspot.com/mavenrepo"
      ),
      version := appVersion
  )

}
