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

import com.google.api.client.http.GenericUrl

import java.net.URL

object GcsArtifactRegistryGenericUrlFactory {

  def createFromUrl( srcUrl: URL ): GenericUrl = {
    val genericUrl = new GenericUrl()
    genericUrl.setScheme( "https" )
    genericUrl.setHost( srcUrl.getHost )
    genericUrl.appendRawPath( srcUrl.getPath )
    genericUrl
  }
}
