package org.latestbit.sbt.gcs

import sbt._

class GcsPluginKeys {
  val gcsPublishFilePolicy = settingKey[GcsPublishFilePolicy]("Published artifacts will have either inherited access rights or public.")
  val gcsCredentialsFile = settingKey[Option[File]]("A file path to Google credentials (optional)")
  val gcsPublisher = taskKey[GcsPublisher]("Google Cloud Storage artifact publisher")
}
