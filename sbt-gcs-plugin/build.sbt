name := "sbt-gcs-plugin"

description := "A SBT plugin for Google Cloud Storage (GCS) and Google Artifact Registry"

organization := "org.latestbit"

homepage := Some( url( "http://latestbit.com" ) )

licenses += ( "Apache-2.0", url( "https://www.apache.org/licenses/LICENSE-2.0.html" ) )

developers := List(
  Developer( "abdulla", "Abdulla Abdurakhmanov", "me@abdolence.dev", url( "https://abdolence.dev" ) )
)

startYear := Some( 2021 )

scmInfo := Some(
  ScmInfo(
    url( "https://github.com/abdolence/sbt-gcs-resolver" ),
    "scm:git:git@github.com:abdolence/sbt-gcs-resolver.git"
  )
)

lazy val sbt1PluginScalaVersion = "2.12.21"
lazy val sbt2PluginScalaVersion = "3.8.4"

crossScalaVersions := Seq( sbt1PluginScalaVersion, sbt2PluginScalaVersion )

libraryDependencies ++= Seq(
  "com.google.cloud" % "google-cloud-storage" % "2.70.0"
)

sbtPlugin := true

enablePlugins( SbtPlugin, GitVersioning )

( pluginCrossBuild / sbtVersion ) := {
  scalaBinaryVersion.value match {
    case "2.12" => "1.12.3"
    case _      => "2.0.3"
  }
}

publishMavenStyle := true

publishTo := {
  if ( version.value.endsWith( "-SNAPSHOT" ) ) {
    Some( "central-snapshots" at "https://central.sonatype.com/repository/maven-snapshots/" )
  } else {
    localStaging.value
  }
}

credentials += Credentials( Path.userHome / ".sbt" / ".credentials" )

sbtPluginPublishLegacyMavenStyle := false

scalacOptions ++= {
  scalaBinaryVersion.value match {
    case "2.12" =>
      Seq(
        "-release:8"
      )
    case "3" =>
      Nil
  }
}
