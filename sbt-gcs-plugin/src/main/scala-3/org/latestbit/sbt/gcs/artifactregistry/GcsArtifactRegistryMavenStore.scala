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
package org.latestbit.sbt.gcs.artifactregistry

import com.google.api.client.http.{ FileContent, HttpRequestFactory }
import org.latestbit.sbt.gcs.{ GcsMavenRepository, MavenPath, MavenRepositoryStore }

import java.nio.file.Path

private[gcs] final class GcsArtifactRegistryMavenStore(
    requests: HttpRequestFactory,
    registry: GcsMavenRepository.ArtifactRegistry
) extends MavenRepositoryStore {

  override def exists( path: MavenPath ): Boolean = {
    val response = requests.buildHeadRequest( registry.url( path ) ).setThrowExceptionOnExecuteError( false ).execute()
    try response.isSuccessStatusCode
    finally response.disconnect()
  }

  override def upload( path: MavenPath, file: Path ): Unit =
    requests
      .buildPutRequest( registry.url( path ), new FileContent( path.contentType, file.toFile ) )
      .execute()
      .disconnect()
}
