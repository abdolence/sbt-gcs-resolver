package org.latestbit.sbt.gcs.gs

import com.google.cloud.storage.Storage
import org.apache.ivy.util.url.URLHandler
import org.apache.ivy.util.{CopyProgressEvent, CopyProgressListener}
import org.latestbit.sbt.gcs.GcsPublishFilePolicy
import sbt.Logger

import java.io.{File, InputStream}
import java.net.URL

class GcsIvyUrlHandler( gcsStorage: Storage, gcsPublishFilePolicy: GcsPublishFilePolicy )( implicit logger: Logger )
    extends URLHandler {

  override def isReachable( url: URL ): Boolean = urlToGcsRepositoryResource( url ).exists()

  override def isReachable( url: URL, timeout: Int ): Boolean = urlToGcsRepositoryResource( url ).exists()

  override def getContentLength( url: URL ): Long = urlToGcsRepositoryResource( url ).getContentLength

  override def getContentLength( url: URL, timeout: Int ): Long = urlToGcsRepositoryResource( url ).getContentLength

  override def getLastModified( url: URL ): Long = urlToGcsRepositoryResource( url ).getLastModified

  override def getLastModified( url: URL, timeout: Int ): Long = urlToGcsRepositoryResource( url ).getLastModified

  override def getURLInfo( url: URL ): URLHandler.URLInfo = {
    val gcsResource = urlToGcsRepositoryResource( url )
    new GcsIvyUrlInfo( gcsResource )
  }

  override def getURLInfo( url: URL, timeout: Int ): URLHandler.URLInfo = getURLInfo( url )

  override def openStream( url: URL ): InputStream = urlToGcsRepositoryResource( url ).openStream()

  override def download( src: URL, dest: File, l: CopyProgressListener ): Unit = {
    val event = new CopyProgressEvent()
    Option( l ).foreach( _.start( event ) )
    urlToGcsRepositoryResource( src ).download( dest.toPath )
    Option( l ).foreach( _.end( event ) )
  }

  override def upload( src: File, dest: URL, l: CopyProgressListener ): Unit = {
    val bucketName  = dest.getHost
    val destination = dest.getPath.drop( 1 )
    Option( gcsStorage.get( bucketName ) ) match {
      case Some( gcsBucket ) => {
        logger.info(
          s"Publishing GCS artifact to '${dest}'. Source file name: ${src.getName}."
        )
        val event = new CopyProgressEvent()
        Option( l ).foreach( _.start( event ) )
        GcsRepositoryResource.upload(
          gcsBucket,
          gcsPublishFilePolicy,
          src,
          destination,
          getContentType( dest )
        )
        Option( l ).foreach( _.end( event ) )
      }
      case _ => {
        throw new IllegalStateException( s"Unable to open bucket: ${bucketName} in GCS" )
      }
    }
  }

  override def setRequestMethod( requestMethod: Int ): Unit = ()

  private def urlToGcsRepositoryResource( url: URL ): GcsRepositoryResource = {
    val bucketName = url.getHost
    GcsRepositoryResource.createForSource(
      gcsStorage,
      bucketName,
      url.getPath.drop( 1 )
    )
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

  private class GcsIvyUrlInfo( gcsResource: GcsRepositoryResource )
      extends URLHandler.URLInfo(
        gcsResource.exists(),
        gcsResource.getContentLength,
        gcsResource.getLastModified
      )
}
