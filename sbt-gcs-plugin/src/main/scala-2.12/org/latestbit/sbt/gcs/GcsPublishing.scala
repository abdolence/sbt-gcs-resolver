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

import org.apache.ivy.util.url.{ URLHandlerDispatcher, URLHandlerRegistry }
import org.latestbit.sbt.gcs.artifactregistry.GcsArtifactRegistryIvyUrlHandler
import org.latestbit.sbt.gcs.gs.GcsIvyUrlHandler
import sbt._

/** Publishing on sbt 1, where Ivy uploads through the URL handler registered for each scheme. */
object GcsPublishing {

  def install( state: State, clients: GoogleCloudClients, gcsPublishFilePolicy: GcsPublishFilePolicy ): State = {
    implicit val logger: Logger = state.log
    val dispatcher: URLHandlerDispatcher = URLHandlerRegistry.getDefault match {
      case existingUrlHandlerDispatcher: URLHandlerDispatcher => existingUrlHandlerDispatcher
      case otherKindOfDispatcher                              =>
        logger.info( "Setting up Ivy URLHandlerDispatcher to handle gs:// and artifactregistry://" )
        val dispatcher: URLHandlerDispatcher = new URLHandlerDispatcher()
        dispatcher.setDefault( otherKindOfDispatcher )
        URLHandlerRegistry.setDefault( dispatcher )
        dispatcher
    }

    dispatcher.setDownloader( "gs", new GcsIvyUrlHandler( clients.storage, gcsPublishFilePolicy ) )
    dispatcher.setDownloader( "artifactregistry", new GcsArtifactRegistryIvyUrlHandler( clients.httpRequestFactory ) )
    state
  }

  def loadFailed( state: State, failure: Throwable ): State = state

  val projectSettings: Seq[Setting[_]] = Nil
}
