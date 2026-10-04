lazy val root = ( project in file( "." ) ).dependsOn(sbtGcsPlugin)

lazy val sbtGcsPlugin = ProjectRef( file( "../sbt-gcs-plugin" ), "sbt-gcs-plugin" )

addSbtPlugin( "org.latestbit" % "sbt-gcs-plugin" % "2.1.0" )

addSbtPlugin( "com.github.sbt" % "sbt-git" % "2.2.0" )
