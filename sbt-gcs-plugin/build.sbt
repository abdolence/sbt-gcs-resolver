name := "sbt-gcs-plugin"

description := "A SBT resolver and publisher for Google Cloud Storage"

organization := "org.latestbit"

homepage := Some(url("http://latestbit.com"))

licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))

val sbtPluginScalaVersion = "2.12.12"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-library" % scalaVersion.value,
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  "org.apache.ivy" % "ivy" % "2.4.0",
  "com.google.cloud" % "google-cloud-storage" % "1.113.8"
)

sbtPlugin := true

enablePlugins(GitVersioning)

crossSbtVersions := Seq("1.4.6")

scalaVersion := (CrossVersion partialVersion (sbtVersion in pluginCrossBuild).value match {
  case Some((1, _))  => sbtPluginScalaVersion
  case _             => sys error s"Unhandled sbt version ${(sbtVersion in pluginCrossBuild).value}"
})

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

pomExtra := (
  <scm>
    <url>https://github.com/abdolence/sbt-gcs-resolver</url>
    <connection>scm:git:https://github.com/abdolence/sbt-gcs-resolver</connection>
    <developerConnection>scm:git:https://github.com/abdolence/sbt-gcs-resolver</developerConnection>
  </scm>
    <developers>
      <developer>
        <id>abdulla</id>
        <name>Abdulla Abdurakhmanov</name>
        <url>http://abdolence.dev</url>
      </developer>
    </developers>
  )