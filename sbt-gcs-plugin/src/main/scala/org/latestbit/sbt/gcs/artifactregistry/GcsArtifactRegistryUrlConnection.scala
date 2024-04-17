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

import com.google.api.client.http.HttpRequestFactory

import java.io.{ InputStream, OutputStream }
import java.net.{ HttpURLConnection, URL }

class GcsArtifactRegistryUrlConnection( googleHttpRequestFactory: HttpRequestFactory, url: URL )
    extends HttpURLConnection( url ) {

  override def connect(): Unit = ()

  override def getInputStream: InputStream = super.getInputStream()

  override def getOutputStream: OutputStream = super.getOutputStream()

  override def disconnect(): Unit = ()

  override def usingProxy(): Boolean = false

}
