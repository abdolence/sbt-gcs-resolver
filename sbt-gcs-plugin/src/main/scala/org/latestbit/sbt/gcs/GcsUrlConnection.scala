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

import com.google.cloud.storage.{ Blob, BlobId, Bucket, Storage }
import sbt.Logger

import java.io.{ IOException, InputStream }
import java.net.{ URL, URLConnection }
import java.nio.channels.Channels

class GcsUrlConnection( gcsStorage: Storage, url: URL )( implicit logger: Logger ) extends URLConnection( url ) {
  private val bucketName             = url.getHost
  private val blobId                 = BlobId.of( bucketName, url.getPath.drop( 1 ) )
  private var bucket: Option[Bucket] = None
  private var blob: Option[Blob]     = None

  logger.info( s"Checking GCS artifact at url: ${url}. Bucket: $bucketName" )

  override def connect(): Unit = {
    connected = false
    Option( gcsStorage.get( bucketName ) ) match {
      case Some( gcsBucket ) => {
        bucket = Some( gcsBucket )
        Option( gcsStorage.get( blobId ) ) match {
          case Some( gcsBlob ) => {
            blob = Some( gcsBlob )
            logger.info( s"Found GCS artifact at url: ${url}. BlobId: ${blobId}" )
            connected = true
          }
          case _ =>
        }
      }
      case _ => {
        throw new IOException( s"Unable to open bucket: ${bucketName} in GCS" )
      }
    }

  }

  override def getInputStream: InputStream = {
    if (!connected)
      connect()
    blob
      .map( b => Channels.newInputStream( b.reader() ) )
      .getOrElse(
        throw new IllegalStateException( s"Trying to read empty resource at: ${blobId}" )
      )
  }
}
