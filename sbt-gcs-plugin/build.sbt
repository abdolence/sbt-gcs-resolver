name := "sbt-gcs-plugin"

description := "A SBT plugin for Google Cloud Storage (GCS) and Google Artifact Registry"

organization := "org.latestbit"

homepage := Some( url( "http://latestbit.com" ) )

licenses += ( "Apache-2.0", url( "https://www.apache.org/licenses/LICENSE-2.0.html" ) )

val sbtPluginScalaVersion = "2.12.20"

libraryDependencies ++= Seq(
  "org.scala-lang"   % "scala-library"        % scalaVersion.value,
  "org.scala-lang"   % "scala-reflect"        % scalaVersion.value,
  "org.scala-lang"   % "scala-compiler"       % scalaVersion.value,
  "org.apache.ivy"   % "ivy"                  % "2.4.0",
  "com.google.cloud" % "google-cloud-storage" % "2.60.0"
)

sbtPlugin := true

enablePlugins( GitVersioning )

scalaVersion := ( CrossVersion partialVersion ( pluginCrossBuild / sbtVersion ).value match {
  case Some( ( 1, _ ) ) => sbtPluginScalaVersion
  case _                => sys error s"Unhandled sbt version ${( pluginCrossBuild / sbtVersion ).value}"
} )

publishMavenStyle := true

import xerial.sbt.Sonatype.sonatypeCentralHost

ThisBuild / sonatypeCredentialHost := sonatypeCentralHost

ThisBuild / publishTo := sonatypePublishToBundle.value 

ThisBuild / sbtPluginPublishLegacyMavenStyle := false

pomExtra := (
  <developers>
      <developer>
        <id>abdulla</id>
        <name>Abdulla Abdurakhmanov</name>
        <url>http://abdolence.dev</url>
      </developer>
    </developers>
)
