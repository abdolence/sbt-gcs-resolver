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

import com.google.api.client.http.HttpRequestFactory
import com.google.cloud.storage.Storage

/** The clients of one sbt load for GCS and for Artifact Registry, each created on first use. */
private[gcs] final class GoogleCloudClients(
    createStorage: => Storage,
    createHttpRequestFactory: => HttpRequestFactory
) {
  lazy val storage: Storage                       = createStorage
  lazy val httpRequestFactory: HttpRequestFactory = createHttpRequestFactory
}
