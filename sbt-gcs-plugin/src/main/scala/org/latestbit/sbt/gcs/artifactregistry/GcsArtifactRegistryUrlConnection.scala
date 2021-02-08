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

import com.google.api.client.http.{ GenericUrl, HttpRequestFactory, HttpResponseException }
import sbt.Logger

import java.io.InputStream
import java.net.{ HttpURLConnection, URL }

class GcsArtifactRegistryUrlConnection( googleHttpRequestFactory: HttpRequestFactory, url: URL )( implicit
    logger: Logger
) extends HttpURLConnection( url ) {
  private final val genericUrl = GcsArtifactRegistryGenericUrlFactory.createFromUrl( url )

  logger.info( s"Checking GCS artifact at url: ${url}." )

  override def connect(): Unit = {
    connected = false
    try {
      val httpRequest = googleHttpRequestFactory.buildHeadRequest( genericUrl )
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
      val httpRequest  = googleHttpRequestFactory.buildGetRequest( genericUrl )
      val httpResponse = httpRequest.execute()
      httpResponse.getContent
    } catch {
      case ex: HttpResponseException => {
        responseCode = ex.getStatusCode
        responseMessage = ex.getStatusMessage
        null
      }
    }
  }

  override def disconnect(): Unit = {
    connected = false
  }

  override def usingProxy(): Boolean = false
}
