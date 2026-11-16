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

import org.latestbit.sbt.gcs.GcsPlugin.autoImport.gcsPublishFilePolicy
import sbt._
import sbt.Keys._
import sbt.librarymanagement.Publisher

import scala.util.{ Failure, Success, Try }

/** Publishing on sbt 2, which publishes without Ivy and only to file and HTTP(S) repositories by itself.
  *
  * A gs:// or artifactregistry:// `publishTo` gets a local Maven repository next to it in `otherResolvers`. sbt
  * publishes there, so the layout, cross-version names and checksums stay sbt's own, and the plugin then uploads the
  * staged files.
  */
object GcsPublishing {

  private val googleClients = AttributeKey[Try[GoogleCloudClients]](
    "gcsGoogleClients",
    "The Google clients of this load, or why they could not be created, for publishing to gs:// and artifactregistry://"
  )

  private val gcsPublishTarget = taskKey[Option[GcsPublishTarget]](
    "The gs:// or artifactregistry:// resolver in publishTo, with the local Maven repository sbt publishes to first"
  )

  // sbt 1 registers the file policy with its Ivy handlers when loading; here it is read for each publish.
  def install( state: State, clients: GoogleCloudClients, gcsPublishFilePolicy: GcsPublishFilePolicy ): State =
    state.put( googleClients, Success( clients ) )

  def loadFailed( state: State, failure: Throwable ): State = state.put( googleClients, Failure( failure ) )

  val projectSettings: Seq[Setting[_]] = Seq(
    gcsPublishTarget := Def.uncached {
      publishTo.value.flatMap( GcsPublishTarget.of( _, target.value / "gcs-staging" ) )
    },
    otherResolvers := Def.uncached {
      otherResolvers.value ++ gcsPublishTarget.value.map( _.stagingResolver )
    },
    publisher := Def.uncached {
      val sbtPublisher = publisher.value
      ( gcsPublishTarget.value, state.value.get( googleClients ) ) match {
        case ( Some( publishTarget ), Some( clients ) ) =>
          Publisher( new GcsPublisher( sbtPublisher, publishTarget, clients, ( Global / gcsPublishFilePolicy ).value ) )
        case _ =>
          publishTo.value
            .flatMap( GcsIvyPatterns.resolverName )
            .fold( sbtPublisher )( name => Publisher( new GcsIvyPatternsPublisher( sbtPublisher, name ) ) )
      }
    }
  )
}
