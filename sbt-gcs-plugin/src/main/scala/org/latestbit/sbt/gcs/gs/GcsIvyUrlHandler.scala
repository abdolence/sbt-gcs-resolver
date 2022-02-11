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

import com.google.cloud.storage.Bucket.BlobWriteOption
import com.google.cloud.storage.Storage
import org.apache.ivy.util.url.URLHandler
import org.apache.ivy.util.{ CopyProgressEvent, CopyProgressListener }
import org.latestbit.sbt.gcs.GcsPublishFilePolicy
import sbt.Logger

import java.io.{ File, FileInputStream, InputStream }
import java.net.URL
import java.nio.channels.Channels

class GcsIvyUrlHandler( gcsStorage: Storage, gcsPublishFilePolicy: GcsPublishFilePolicy )( implicit logger: Logger )
    extends URLHandler {

  override def isReachable( url: URL ): Boolean = urlToGcsIvyUrlInfo( url ).available

  override def isReachable( url: URL, timeout: Int ): Boolean = isReachable( url )

  override def getContentLength( url: URL ): Long = urlToGcsIvyUrlInfo( url ).getContentLength

  override def getContentLength( url: URL, timeout: Int ): Long = getContentLength( url )

  override def getLastModified( url: URL ): Long = urlToGcsIvyUrlInfo( url ).getLastModified

  override def getLastModified( url: URL, timeout: Int ): Long = getLastModified( url )

  override def getURLInfo( url: URL ): URLHandler.URLInfo = urlToGcsIvyUrlInfo( url )

  override def getURLInfo( url: URL, timeout: Int ): URLHandler.URLInfo = getURLInfo( url )

  override def openStream( url: URL ): InputStream = {
    Option( gcsStorage.get( GcsUrlConnection.toBlobId( url ) ) ).map { blob =>
      Channels.newInputStream( blob.reader() )
    }.orNull
  }

  override def download( src: URL, dest: File, l: CopyProgressListener ): Unit = {
    val event = new CopyProgressEvent()
    Option( l ).foreach( _.start( event ) )
    Option( gcsStorage.get( GcsUrlConnection.toBlobId( src ) ) )
      .foreach { blob =>
        blob.downloadTo( dest.toPath )
      }
    Option( l ).foreach( _.end( event ) )
  }

  override def upload( src: File, dest: URL, l: CopyProgressListener ): Unit = {
    val bucketName  = dest.getHost
    val destination = dest.getPath.drop( 1 )
    Option( gcsStorage.get( bucketName ) ) match {
      case Some( gcsBucket ) => {
        logger.info(
          s"Publishing GCS artifact to '${dest}'..."
        )
        val event = new CopyProgressEvent()
        Option( l ).foreach( _.start( event ) )
        gcsPublishFilePolicy match {
          case GcsPublishFilePolicy.PublicAccess ⇒
            gcsBucket.create(
              destination,
              new FileInputStream( src ),
              getContentType( dest ),
              BlobWriteOption.predefinedAcl( Storage.PredefinedAcl.PUBLIC_READ )
            )
          case GcsPublishFilePolicy.InheritedFromBucket ⇒
            gcsBucket.create(
              destination,
              new FileInputStream( src ),
              getContentType( dest )
            )
        }
        Option( l ).foreach( _.end( event ) )
      }
      case _ => {
        throw new IllegalStateException( s"Unable to open bucket: ${bucketName} in GCS" )
      }
    }
  }

  override def setRequestMethod( requestMethod: Int ): Unit = ()

  private def urlToGcsIvyUrlInfo( url: URL ): GcsIvyUrlInfo = {
    Option( gcsStorage.get( GcsUrlConnection.toBlobId( url ) ) )
      .map { blob =>
        GcsIvyUrlInfo(
          available = true,
          contentLength = blob.getSize.longValue(),
          lastModified = blob.getUpdateTime.longValue()
        )
      }
      .getOrElse {
        GcsIvyUrlInfo( available = false, contentLength = 0, lastModified = 0 )
      }
  }

  private def getContentType( url: URL ): String = {
    url.getPath.takeRight( 4 ).toLowerCase match {
      case ".jar"  ⇒ "application/java-archive"
      case ".xml"  ⇒ "application/xml"
      case ".sha1" ⇒ "text/plain"
      case ".md5"  ⇒ "text/plain"
      case ".ivy"  ⇒ "application/xml"
      case _      => "application/octet-stream"
    }
  }

  private case class GcsIvyUrlInfo( available: Boolean, contentLength: Long, lastModified: Long )
      extends URLHandler.URLInfo( available, contentLength, lastModified )
}
