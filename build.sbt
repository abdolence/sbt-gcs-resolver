lazy val ScalaVersions = Seq( "3.6.2", "2.12.20" )

organization := "org.latestbit"

homepage := Some( url( "http://latestbit.com" ) )

licenses += ( "Apache-2.0", url( "https://www.apache.org/licenses/LICENSE-2.0.html" ) )

scalaVersion := "2.12.20"

scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-Xlog-reflective-calls",
  "-Xlint",
  "-deprecation",
  "-feature",
  "-unchecked"
)

publish / skip := true

lazy val testGoogleArtifactRegistry = "europe-north1-maven.pkg.dev/latestbit/latestbit-artifacts-snapshots"

lazy val sbtGcsPlaygroundToPublish = ( projectMatrix in file( "playground-publish" ) )
  .settings(
    name               := "sbt-gcs-plugin-playground-publish",
    version            := "0.0.7",
    crossScalaVersions := Nil,
    publishTo          := Some( "Custom Releases" at "gs://private-artifacts" ),
    logLevel           := Level.Debug
  )
  .jvmPlatform( scalaVersions = ScalaVersions )

lazy val sbtGcsPlaygroundToResolve = ( projectMatrix in file( "playground-resolve" ) )
  .settings(
    name               := "sbt-gcs-plugin-playground-resolve",
    crossScalaVersions := Nil,
    resolvers += "Custom Releases" at "gs://private-artifacts",
    libraryDependencies ++= Seq(
      "org.latestbit" %% "sbt-gcs-plugin-playground-publish" % "0.0.7"
    ),
    logLevel := Level.Debug
  )
  .jvmPlatform( scalaVersions = ScalaVersions )

lazy val sbtGcsArtifactRepositoryPlaygroundToPublish =
  ( projectMatrix in file( "playground-publish-artifact-repository" ) )
    .settings(
      name               := "sbt-gcs-plugin-playground-artifact-publish",
      version            := "0.0.25-SNAPSHOT",
      crossScalaVersions := Nil,
      publishTo          := Some(
        "Custom Releases" at s"artifactregistry://${testGoogleArtifactRegistry}"
      ),
      logLevel := Level.Debug
    )
    .jvmPlatform( scalaVersions = ScalaVersions )

lazy val sbtGcsArtifactRepositoryPlaygroundToResolve =
  ( projectMatrix in file( "playground-resolve-artifact-repository" ) )
    .settings(
      name               := "sbt-gcs-plugin-playground-artifact-resolve",
      crossScalaVersions := Nil,
      resolvers += "Custom Releases" at s"artifactregistry://${testGoogleArtifactRegistry}",
      libraryDependencies ++= Seq(
        "org.latestbit" %% "sbt-gcs-plugin-playground-artifact-publish" % "0.0.25-SNAPSHOT"
      ),
      logLevel := Level.Debug
    )
    .jvmPlatform( scalaVersions = ScalaVersions )

lazy val plugin = ( projectMatrix in file( "sbt-gcs-plugin" ) )
  .enablePlugins( GitVersioning, SbtPlugin )
  .settings(
    name := "sbt-gcs-plugin",
    description := "A SBT plugin for Google Cloud Storage (GCS) and Google Artifact Registry",
    organization := "org.latestbit",
    homepage := Some( url( "http://latestbit.com" ) ),
    licenses += ( "Apache-2.0", url( "https://www.apache.org/licenses/LICENSE-2.0.html" ) ),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/abdolence/sbt-gcs-resolver"),
        "scm:git@github.com:abdolence/sbt-gcs-resolver.git"
      )
    ),
    developers := List(
      Developer(
        id = "abdolence",
        name = "Abdulla Abdurakhmanov",
        email = "me@abdolence.dev",
        url = url("http://abdolence.dev")
      )
    ),
    publishTo := {
      val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
      if version.value.endsWith("-SNAPSHOT") then Some("central-snapshots" at centralSnapshots)
      else localStaging.value
    },
    publish / skip := false,
    sbtPlugin := true,
    publishMavenStyle := true,
    credentials += Credentials(Path.userHome / ".sbt" / "sonatype_central_credentials"),
    scalaVersion := "2.12.20",
    libraryDependencies ++= Seq(
      "org.scala-lang"   % "scala-library"        % scalaVersion.value,
      "org.scala-lang"   % "scala-reflect"        % scalaVersion.value,
      "org.scala-lang"   % "scala-compiler"       % scalaVersion.value,
      "org.apache.ivy"   % "ivy"                  % "2.4.0",
      "com.google.cloud" % "google-cloud-storage" % "2.62.0"
    )
  )
  .jvmPlatform( scalaVersions = ScalaVersions )

lazy val sbtGcsRoot = ( projectMatrix in file( "." ) )
  .settings(
    name               := "sbt-gcs-plugin-root",
    crossScalaVersions := Nil,
    publish            := {},
    publishLocal       := {},
    publishArtifact    := false,
    logLevel           := Level.Debug
  )
  .jvmPlatform( scalaVersions = ScalaVersions )
