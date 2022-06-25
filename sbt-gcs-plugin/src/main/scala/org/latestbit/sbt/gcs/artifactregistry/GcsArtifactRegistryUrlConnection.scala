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
import scala.util.Try
import scala.jdk.CollectionConverters.*

class GcsArtifactRegistryUrlConnection( googleHttpRequestFactory: HttpRequestFactory, url: URL )( implicit
    logger: Logger
) extends HttpURLConnection( url ) {
  private final val genericUrl                        = GcsArtifactRegistryGenericUrlFactory.createFromUrl( url )
  private final var connectedWithHeaders: HttpHeaders = new HttpHeaders()

  logger.info( s"Checking artifact at url: ${url}." )

  override def connect(): Unit = {
    connected = false
    connectedWithHeaders = new HttpHeaders()
    try {
      val httpRequest = googleHttpRequestFactory.buildHeadRequest( genericUrl )
      super.getRequestProperties.asScala.foreach { case ( header, headerValues ) =>
        connectedWithHeaders.set( header, headerValues )
      }
      connected = httpRequest.execute().isSuccessStatusCode
    } catch {
      case ex: HttpResponseException => {
        responseCode = ex.getStatusCode
        responseMessage = ex.getStatusMessage
      }
    }
  }

  override def getInputStream: InputStream = {
    if (!connected) {
      connect()
    }
    try {
      val httpRequest = googleHttpRequestFactory.buildGetRequest( genericUrl )

      val httpResponse = appendHeadersBeforeConnect( httpRequest ).execute()

      httpResponse.getContent
    } catch {
      case ex: HttpResponseException => {
        responseCode = ex.getStatusCode
        responseMessage = ex.getStatusMessage
        null
      }
    }
  }

  override def getOutputStream: OutputStream = {
    if (!connected) {
      connect()
    }
    new ByteArrayOutputStream() {
      override def close(): Unit = {
        super.close()
        Try {
          val httpRequest =
            googleHttpRequestFactory
              .buildPutRequest( genericUrl, new ByteArrayContent( connectedWithHeaders.getContentType, toByteArray ) )

          appendHeadersBeforeConnect( httpRequest ).execute()

        }.recover { case e: Exception =>
          logger.error( s"Failed to upload $url\n${e.getMessage}" )
          throw e
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
