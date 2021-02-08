package org.latestbit.sbt.gcs.artifactregistry

import com.google.api.client.http.{ GenericUrl, HttpContent, HttpRequestFactory }
import org.apache.ivy.util.url.URLHandler
import org.apache.ivy.util.{ CopyProgressEvent, CopyProgressListener }
import sbt.Logger

import java.io.{ File, InputStream, OutputStream }
import java.net.URL
import java.nio.file.Files
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class GcsArtifactRegistryIvyUrlHandler( googleHttpRequestFactory: HttpRequestFactory )( implicit logger: Logger )
    extends URLHandler {

  override def isReachable( url: URL ): Boolean = getURLInfo( url ).isReachable

  override def isReachable( url: URL, timeout: Int ): Boolean = isReachable( url )

  override def getContentLength( url: URL ): Long = getURLInfo( url ).getContentLength

  override def getContentLength( url: URL, timeout: Int ): Long = getContentLength( url )

  override def getLastModified( url: URL ): Long = getURLInfo( url ).getLastModified

  override def getLastModified( url: URL, timeout: Int ): Long = getLastModified( url )

  override def getURLInfo( url: URL ): URLHandler.URLInfo = {
    val genericUrl   = GcsArtifactRegistryGenericUrlFactory.createFromUrl( url )
    val httpRequest  = googleHttpRequestFactory.buildHeadRequest( genericUrl )
    val httpResponse = httpRequest.execute()
    GcsArtifactRegistryIvyUrlInfo(
      available = httpResponse.isSuccessStatusCode,
      contentLength =
        Option( httpResponse.getHeaders.get( "Content-Length" ) ).map( _.toString.toLong ).getOrElse( 0L ),
      lastModified = Option( httpResponse.getHeaders.get( "Last-Modified" ) )
        .map( _.toString )
        .map( dateAsStr => ZonedDateTime.parse( dateAsStr, DateTimeFormatter.RFC_1123_DATE_TIME ) )
        .map( _.toEpochSecond )
        .getOrElse( 0L )
    )
  }

  override def getURLInfo( url: URL, timeout: Int ): URLHandler.URLInfo = getURLInfo( url )

  override def openStream( url: URL ): InputStream = {
    val genericUrl   = GcsArtifactRegistryGenericUrlFactory.createFromUrl( url )
    val httpRequest  = googleHttpRequestFactory.buildGetRequest( genericUrl )
    val httpResponse = httpRequest.execute()
    httpResponse.getContent
  }

  override def download( src: URL, dest: File, l: CopyProgressListener ): Unit = {
    val event = new CopyProgressEvent()
    Option( l ).foreach( _.start( event ) )
    val genericUrl   = GcsArtifactRegistryGenericUrlFactory.createFromUrl( src )
    val httpRequest  = googleHttpRequestFactory.buildGetRequest( genericUrl )
    val httpResponse = httpRequest.execute()
    httpResponse.download( Files.newOutputStream( dest.toPath ) )
    Option( l ).foreach( _.end( event ) )
  }

  override def upload( src: File, dest: URL, l: CopyProgressListener ): Unit = {
    logger.info(
      s"Publishing GCS artifact to '${dest}'. Source file name: ${src.getName}."
    )
    val event = new CopyProgressEvent()
    Option( l ).foreach( _.start( event ) )
    val genericUrl  = GcsArtifactRegistryGenericUrlFactory.createFromUrl( dest )
    val contentType = getContentType( dest )

    val httpRequest = googleHttpRequestFactory.buildPutRequest(
      genericUrl,
      new HttpContent() {
        override def getLength: Long           = src.length()
        override def getType: String           = contentType
        override def retrySupported(): Boolean = true

        override def writeTo( out: OutputStream ): Unit = {
          Files.copy( src.toPath, out )
          out.close()
        }
      }
    )
    httpRequest.execute()
    Option( l ).foreach( _.end( event ) )
  }

  override def setRequestMethod( requestMethod: Int ): Unit = ()

  private case class GcsArtifactRegistryIvyUrlInfo( available: Boolean, contentLength: Long, lastModified: Long )
      extends URLHandler.URLInfo( available, contentLength, lastModified )

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
}
