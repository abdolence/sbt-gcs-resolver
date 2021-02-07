package org.latestbit.sbt.gcs

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.{Storage, StorageOptions}
import sbt.{Logger, ProjectRef}

import java.io.FileInputStream
import java.nio.file.Path

object GcsStorageConnector {
  def create(gcsCredentialsFilePath: Option[Path], project: ProjectRef)(implicit logger: Logger) : Storage = {

    val credentials: GoogleCredentials =
      gcsCredentialsFilePath.map { path =>
        logger.info(s"Loading Google credentials from: ${path.toAbsolutePath.toString} for ${project.toString}")
        GoogleCredentials.fromStream(new FileInputStream(path.toFile))
      }.getOrElse {
        logger.info(s"Loading default Google credentials for ${project.toString}")
        GoogleCredentials.getApplicationDefault()
      }

    StorageOptions.newBuilder().setCredentials(credentials).build().getService
  }
}
