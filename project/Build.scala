import sbt._
import Keys._
import play.PlayScala
import java.io.PrintWriter
import java.io.File

object ApplicationBuild extends Build {

	val appName         = "Cashy"
	val appVersion      = "0.0.1." + "git rev-parse --short HEAD".!!.trim + ".SNAPSHOT"

	val appDependencies = Seq(
		"org.apache.httpcomponents" % "httpclient" % "4.2.5"
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
