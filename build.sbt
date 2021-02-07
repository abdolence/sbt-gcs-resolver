import org.latestbit.sbt.gcs.GcsPublishFilePolicy

ThisBuild / version := "1.0.1"

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
		version := "0.0.1",
		crossScalaVersions := Nil,
		gcsPublishFilePolicy := GcsPublishFilePolicy.InheritedFromBucket,
		publishTo := Some(gcsPublisher.value.toBucket("private-artifacts"))
	)

lazy val sbtGcsPlaygroundToResolve = project
	.in(file("playground-resolve"))
	.settings(
		name := "sbt-gcs-plugin-playground-resolve",
		crossScalaVersions := Nil,
		resolvers += "Custom Releases" at "gcs://private-artifacts",
		libraryDependencies ++= Seq(
			"org.latestbit" %% "sbt-gcs-plugin-playground-publish" % "0.0.1"
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
