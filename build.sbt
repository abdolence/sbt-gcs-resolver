import org.latestbit.sbt.gcs.GcsPublishFilePolicy

ThisBuild / version := "1.4.0-SNAPSHOT"

ThisBuild / organization := "org.latestbit"

ThisBuild / homepage := Some(url("http://latestbit.com"))

ThisBuild / licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))

ThisBuild / scalaVersion := "2.12.12"

ThisBuild / scalacOptions ++= Seq(
	"-encoding", "UTF-8",
	"-Xlog-reflective-calls",
	"-Xlint",
	"-deprecation",
	"-feature",
	"-language:_",
	"-unchecked"
)

lazy val sbtGcsPlaygroundToPublish = project
	.in(file("playground-publish"))
	.settings(
		name := "sbt-gcs-plugin-playground-publish",
		version := "0.0.3",
		crossScalaVersions := Nil,
		gcsPublishFilePolicy := GcsPublishFilePolicy.InheritedFromBucket,
		publishTo := Some("Custom Releases" at "gs://private-artifacts")
	)

lazy val sbtGcsPlaygroundToResolve = project
	.in(file("playground-resolve"))
	.settings(
		name := "sbt-gcs-plugin-playground-resolve",
		crossScalaVersions := Nil,
		resolvers += "Custom Releases" at "gs://private-artifacts",
		libraryDependencies ++= Seq(
			"org.latestbit" %% "sbt-gcs-plugin-playground-publish" % "0.0.3"
		)
)

lazy val sbtGcsArtifactRepositoryPlaygroundToPublish = project
	.in(file("playground-publish-artifact-repository"))
	.settings(
		name := "sbt-gcs-plugin-playground-artifact-publish",
		version := "0.0.4",
		crossScalaVersions := Nil,
		gcsPublishFilePolicy := GcsPublishFilePolicy.InheritedFromBucket,
		publishTo := Some("Custom Releases" at "artifactregistry://europe-north1-maven.pkg.dev/abd-artifact-registry-test/test-rep")
	)

lazy val sbtGcsArtifactRepositoryPlaygroundToResolve = project
	.in(file("playground-resolve-artifact-repository"))
	.settings(
		name := "sbt-gcs-plugin-playground-artifact-resolve",
		crossScalaVersions := Nil,
		resolvers += "Custom Releases" at "artifactregistry://europe-north1-maven.pkg.dev/abd-artifact-registry-test/test-rep",
		libraryDependencies ++= Seq(
			"org.latestbit" %% "sbt-gcs-plugin-playground-artifact-publish" % "0.0.4"
		)
	)

lazy val plugin = project
	.in(file("sbt-gcs-plugin"))

lazy val sbtGcsRoot = project
	.in(file("."))
	.settings(
		name := "sbt-gcs-plugin-root",
		crossScalaVersions := Nil,
		publish := {},
		publishLocal := {},
		publishArtifact := false
	)
	.aggregate(sbtGcsPlaygroundToPublish)
