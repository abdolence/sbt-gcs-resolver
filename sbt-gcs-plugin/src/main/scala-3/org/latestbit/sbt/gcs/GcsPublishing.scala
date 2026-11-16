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

import sbt._
import sbt.Keys._
import sbt.librarymanagement.Publisher

/** Publishing on sbt 2, which publishes without Ivy and only to file and HTTP(S) repositories by itself.
  *
  * Each gs:// or artifactregistry:// resolver gets a local Maven repository next to it in `otherResolvers`. sbt
  * publishes there, so the layout, cross-version names and checksums stay sbt's own, and the plugin then uploads the
  * staged files.
  */
private[gcs] object GcsPublishing {

  private final case class Installed( clients: GoogleCloudClients, gcsPublishFilePolicy: GcsPublishFilePolicy )

  private val installed = AttributeKey[Installed](
    "gcsPublishing",
    "The Google clients and file policy of this load, for publishing to gs:// and artifactregistry://"
  )

  def install( state: State, clients: GoogleCloudClients, gcsPublishFilePolicy: GcsPublishFilePolicy ): State =
    state.put( installed, Installed( clients, gcsPublishFilePolicy ) )

  val projectSettings: Seq[Setting[_]] = Seq(
    otherResolvers := Def.uncached {
      val resolvers = otherResolvers.value
      resolvers ++ GcsPublishTarget.all( resolvers, stagingBase( target.value ) ).map( _.stagingResolver )
    },
    publisher := Def.uncached {
      val sbtPublisher = publisher.value
      val targets      = GcsPublishTarget.all( otherResolvers.value, stagingBase( target.value ) )
      state.value.get( installed ) match {
        case Some( Installed( clients, gcsPublishFilePolicy ) ) =>
          Publisher( new GcsPublisher( sbtPublisher, targets, clients, gcsPublishFilePolicy ) )
        case None =>
          sbtPublisher
      }
    }
  )

  private def stagingBase( target: File ): File = target / "gcs-staging"
}
