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

import java.io.{ByteArrayOutputStream, InputStream, OutputStream}
import java.net.{HttpURLConnection, URL}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

class GcsArtifactRegistryUrlConnection( googleHttpRequestFactory: HttpRequestFactory, url: URL )( implicit
    logger: Logger
) extends HttpURLConnection( url ) {
  private final val genericUrl                        = GcsArtifactRegistryGenericUrlFactory.createFromUrl( url )
  private final var connectedWithHeaders: HttpHeaders = new HttpHeaders()
  private var inputStreamIsReady: Option[InputStream] = None

  override def connect(): Unit = {
    // According to documentation if the connect method is called
    // when the connection has already been opened the call is ignored.
    if (!connected) {
      connectedWithHeaders = new HttpHeaders()
      super.getRequestProperties.asScala.foreach { case ( header, headerValues ) =>
        connectedWithHeaders.set( header, headerValues )
      }
      connected = true
    }
  }

  override def getInputStream: InputStream = {
    inputStreamIsReady match {
      case Some( inputStream ) => inputStream
      case None => {
        if (!connected) {
          connect()
        }
        try {
          logger.debug( s"Receiving an artifact from url: ${url}." )
          val httpRequest = googleHttpRequestFactory.buildGetRequest( genericUrl )

          val httpResponse = appendHeadersBeforeConnect( httpRequest ).execute()

          val inputStream = httpResponse.getContent
          inputStreamIsReady = Some( inputStream )
          inputStream
        } catch {
          case ex: HttpResponseException => {
            responseCode = ex.getStatusCode
            responseMessage = ex.getStatusMessage
            null
          }
        }
      }
    }

  }

  override def getOutputStream: OutputStream = {
    new ByteArrayOutputStream() {
      override def close(): Unit = {
        super.close()
        try {
          logger.info( s"Uploading an artifact to: ${url}." )

          val httpRequest =
            googleHttpRequestFactory
              .buildPutRequest( genericUrl, new ByteArrayContent( connectedWithHeaders.getContentType, toByteArray ) )

          appendHeadersBeforeConnect( httpRequest ).execute()
          ()
        } catch {
          case ex: HttpResponseException => {
            responseCode = ex.getStatusCode
            responseMessage = ex.getStatusMessage
            throw ex
          }
          case NonFatal( ex ) =>
            logger.error( s"Failed to upload ${url}:\n${ex.getMessage}" )
            throw ex
        }
      }
    }
  }

  override def disconnect(): Unit = {
    connected = false
    inputStreamIsReady = None
    connectedWithHeaders = new HttpHeaders()
  }

  override def usingProxy(): Boolean = false

  private def appendHeadersBeforeConnect( httpRequest: HttpRequest ): HttpRequest = {
    connectedWithHeaders.asScala.foreach { case ( header, headerValues ) =>
      httpRequest.getHeaders.set( header, headerValues )
    }
    httpRequest
  }

}
