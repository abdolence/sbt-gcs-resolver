lazy val root = ( project in file( "." ) ) dependsOn sbtGcsPlugin

lazy val sbtGcsPlugin = ProjectRef( file( "../sbt-gcs-plugin" ), "sbt-gcs-plugin" )

addSbtPlugin( "org.latestbit" % "sbt-gcs-plugin" % "1.7.3" )

addSbtPlugin( "org.scalameta" % "sbt-scalafmt" % "2.4.6" )

addSbtPlugin( "com.typesafe.sbt" % "sbt-git" % "1.0.2" )
