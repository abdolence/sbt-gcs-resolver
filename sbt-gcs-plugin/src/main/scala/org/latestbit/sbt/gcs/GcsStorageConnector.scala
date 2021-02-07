package org.latestbit.sbt.gcs

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.{Storage, StorageOptions}
import sbt.Logger

import java.io.FileInputStream
import java.nio.file.Path

object GcsStorageConnector {
  def create(gcsCredentialsFilePath: Option[Path])(implicit logger: Logger) : Storage = {

    val credentials: GoogleCredentials =
      gcsCredentialsFilePath.map { path =>
        logger.info(s"Loading Google credentials from: ${path.toAbsolutePath.toString}")
        GoogleCredentials.fromStream(new FileInputStream(path.toFile))
      }.getOrElse {
        logger.info(s"Loading default Google credentials")
        GoogleCredentials.getApplicationDefault()
      }

    StorageOptions.newBuilder().setCredentials(credentials).build().getService
  }
}
