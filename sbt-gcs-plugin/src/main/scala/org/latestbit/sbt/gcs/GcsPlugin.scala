package org.latestbit.sbt.gcs

import sbt._
import sbt.Keys._

object GcsPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport extends GcsPluginKeys
  import autoImport._

  private val gcsPluginDefaultSettings = Seq(
    gcsPublishFilePolicy := GcsPublishFilePolicy.InheritedFromBucket,
    gcsCredentialsFile := None
  )

  private val gcsPluginTaskInits = Seq(
    gcsPublisher := {
      implicit val logger: Logger = Keys.sLog.value
      new org.latestbit.sbt.gcs.GcsPublisher(
        GcsStorageConnector.create(gcsCredentialsFile.value.map(_.toPath), thisProjectRef.value )
      )
    }
  )

  override def projectSettings: Seq[Def.Setting[_]] = gcsPluginDefaultSettings ++ gcsPluginTaskInits ++ super.projectSettings ++
    Seq(
      publishMavenStyle := false
    )
}
