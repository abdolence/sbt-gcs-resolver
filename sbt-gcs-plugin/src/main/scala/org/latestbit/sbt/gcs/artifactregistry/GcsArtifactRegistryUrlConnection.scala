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

import com.google.api.client.http.{
  ByteArrayContent,
  HttpHeaders,
  HttpRequest,
  HttpRequestFactory,
  HttpResponseException
}
import sbt.Logger

import java.io.{ ByteArrayOutputStream, InputStream, OutputStream }
import java.net.{ HttpURLConnection, URL }
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

class GcsArtifactRegistryUrlConnection(
    googleHttpRequestFactory: HttpRequestFactory,
    url: URL,
    var iStream: java.io.InputStream = null
)( implicit
    logger: Logger
) extends HttpURLConnection( url ) {
  private final val genericUrl                        = GcsArtifactRegistryGenericUrlFactory.createFromUrl( url )
  private final var connectedWithHeaders: HttpHeaders = new HttpHeaders()

  logger.debug( s"Creating a connection for $url" )

  override def connect(): Unit = {
    connectedWithHeaders = new HttpHeaders()
    super.getRequestProperties.asScala.foreach { case ( header, headerValues ) =>
      connectedWithHeaders.set( header, headerValues )
    }
    connected = true
  }

  override def getInputStream: InputStream = {
    if (iStream == null) {
      if (!connected) {
        connect()
      }
      try {
        logger.debug( s"Receiving artifact from url: $url." )
        val httpRequest = googleHttpRequestFactory.buildGetRequest( genericUrl )

        val httpResponse = appendHeadersBeforeConnect( httpRequest ).execute()

        iStream = httpResponse.getContent
      } catch {
        case ex: HttpResponseException =>
          responseCode = ex.getStatusCode
          responseMessage = ex.getStatusMessage
          return null
      }
    }
    iStream
  }

  override def getOutputStream: OutputStream = {
    if (!connected) {
      connect()
    }
    new ByteArrayOutputStream() {
      override def close(): Unit = {
        super.close()
        try {
          logger.info( s"Upload artifact from to: ${url}." )

          val httpRequest =
            googleHttpRequestFactory
              .buildPutRequest( genericUrl, new ByteArrayContent( connectedWithHeaders.getContentType, toByteArray ) )

          appendHeadersBeforeConnect( httpRequest ).execute()
          ()
        } catch {
          case NonFatal( ex ) =>
            logger.error( s"Failed to upload ${url}:\n${ex.getMessage}" )
            throw ex
        }
      }
    }
  }

  override def disconnect(): Unit = {
    connected = false
  }

  override def usingProxy(): Boolean = false

  private def appendHeadersBeforeConnect( httpRequest: HttpRequest ): HttpRequest = {
    connectedWithHeaders.asScala.foreach { case ( header, headerValues ) =>
      httpRequest.getHeaders.set( header, headerValues )
    }
    httpRequest
  }

}
