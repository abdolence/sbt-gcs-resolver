/*
 * Copyright 2021 Abdulla Abdurakhmanov (abdulla@latestbit.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.latestbit.sbt.gcs

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.{ Storage, StorageOptions }
import sbt.{ Logger, ProjectRef }

import java.io.FileInputStream
import java.nio.file.Path

object GcsStorageConnector {

  def create( gcsCredentialsFilePath: Option[Path] )( implicit logger: Logger, project: ProjectRef ): Storage = {

    val credentials: GoogleCredentials =
      gcsCredentialsFilePath
        .map { path =>
          logger.info( s"Loading Google credentials from: ${path.toAbsolutePath.toString} for ${project.toString}" )
          GoogleCredentials.fromStream( new FileInputStream( path.toFile ) )
        }
        .getOrElse {
          logger.info( s"Loading default Google credentials for ${project.toString}" )
          GoogleCredentials.getApplicationDefault()
        }

    StorageOptions.newBuilder().setCredentials( credentials ).build().getService
  }
}
