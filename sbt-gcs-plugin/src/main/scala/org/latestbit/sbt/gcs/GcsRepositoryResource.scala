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

import com.google.cloud.storage.Bucket.BlobWriteOption
import com.google.cloud.storage.{Blob, BlobId, Bucket, Storage}
import org.apache.ivy.plugins.repository.Resource

import java.io.{File, FileInputStream, InputStream}
import java.nio.channels.Channels
import java.nio.file.Path

case class GcsRepositoryResource( gcsStorage: Storage, sourceBlobName: String, blob: Option[Blob] ) extends Resource {
  override def getName: String = sourceBlobName

  override def getLastModified: Long = blob.map( _.getUpdateTime.longValue() ).getOrElse( 0L )

  override def getContentLength: Long = blob.map( _.getSize.longValue() ).getOrElse( 0L )

  override def exists(): Boolean = blob.isDefined

  override def isLocal: Boolean = false

  override def clone( cloneName: String ): Resource = copy()

  override def openStream(): InputStream = blob
    .map( b => Channels.newInputStream( b.reader() ) )
    .getOrElse(
      throw new IllegalStateException( s"Trying to read empty resource at: ${sourceBlobName}" )
    )

  def download( path: Path ): Unit = {
    blob.foreach( _.downloadTo( path ) )
  }
}

object GcsRepositoryResource {

  def upload(
      bucket: Bucket,
      publishPolicy: GcsPublishFilePolicy,
      source: File,
      destination: String,
      contentType: String
  ): Unit = {
    publishPolicy match {
      case GcsPublishFilePolicy.PublicAccess ⇒
        bucket.create(
          destination,
          new FileInputStream( source ),
          contentType,
          BlobWriteOption.predefinedAcl( Storage.PredefinedAcl.PUBLIC_READ )
        )
      case GcsPublishFilePolicy.InheritedFromBucket ⇒
        bucket.create(
          destination,
          new FileInputStream( source ),
          contentType
        )
    }
  }

  def createForSource( gcsStorage: Storage, bucketName: String, source: String ): GcsRepositoryResource = {
    val blobId = BlobId.of( bucketName, source )
    Option( gcsStorage.get( blobId ) )
      .map { blob =>
        GcsRepositoryResource( gcsStorage, source, Some( blob ) )
      }
      .getOrElse {
        GcsRepositoryResource( gcsStorage, source, None )
      }
  }

}
