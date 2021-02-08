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

import sbt.Keys._
import sbt._

object GcsPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport extends GcsPluginKeys
  import autoImport._

  private val gcsPluginDefaultSettings = Seq(
    gcsPublishFilePolicy := GcsPublishFilePolicy.InheritedFromBucket,
    gcsCredentialsFile := None
  )

  private val gcsPluginTaskInits = Seq(
    gcsPublisher := {
      implicit val logger: Logger         = Keys.sLog.value
      implicit val projectRef: ProjectRef = thisProjectRef.value

      new org.latestbit.sbt.gcs.GcsPublisher(
        GcsStorageConnector.create( gcsCredentialsFile.value.map( _.toPath ) ),
        gcsPublishFilePolicy.value
      )
    },
    onLoad in Global := ( onLoad in Global ).value.andThen { state =>
      implicit val logger: Logger         = state.log
      implicit val projectRef: ProjectRef = thisProjectRef.value
      val gcsStorage                      = GcsStorageConnector.create( gcsCredentialsFile.value.map( _.toPath ) )

      GcsUrlHandlerFactory.install( gcsStorage, gcsPublishFilePolicy.value )
      state
    }
  )

  override def projectSettings: Seq[Def.Setting[_]] =
    gcsPluginDefaultSettings ++
      gcsPluginTaskInits ++
      super.projectSettings

}
