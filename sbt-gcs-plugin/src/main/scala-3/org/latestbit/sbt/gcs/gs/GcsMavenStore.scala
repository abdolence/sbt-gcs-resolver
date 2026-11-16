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
package org.latestbit.sbt.gcs.gs

import com.google.cloud.storage.{ BlobInfo, Storage }
import org.latestbit.sbt.gcs.{ GcsMavenRepository, GcsPublishFilePolicy, MavenPath, MavenRepositoryStore }

import java.nio.file.Path

private[gcs] final class GcsMavenStore(
    storage: Storage,
    bucket: GcsMavenRepository.Bucket,
    gcsPublishFilePolicy: GcsPublishFilePolicy
) extends MavenRepositoryStore {

  override def exists( path: MavenPath ): Boolean = Option( storage.get( bucket.blobId( path ) ) ).isDefined

  override def upload( path: MavenPath, file: Path ): Unit = {
    val blob = BlobInfo.newBuilder( bucket.blobId( path ) ).setContentType( path.contentType ).build()
    gcsPublishFilePolicy match {
      case GcsPublishFilePolicy.PublicAccess =>
        storage.createFrom( blob, file, Storage.BlobWriteOption.predefinedAcl( Storage.PredefinedAcl.PUBLIC_READ ) )
      case GcsPublishFilePolicy.InheritedFromBucket =>
        storage.createFrom( blob, file )
    }
    ()
  }
}
