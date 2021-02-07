import org.latestbit.sbt.gcs.GcsPublishFilePolicy

ThisBuild / version := "1.0.0"

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

lazy val sbtGcsPlayground = project
	.in(file("playground"))
	.settings(
		name := "sbt-gcs-plugin-playground",
		crossScalaVersions := Nil,
		publishTo := Some(gcsPublisher.value.forBucket("private-artifacts", GcsPublishFilePolicy.InheritedFromBucket))
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
	.aggregate(sbtGcsPlayground)
