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

import com.google.auth.oauth2.AccessToken
import java.io.FileInputStream
import java.nio.file.Path
import scala.jdk.CollectionConverters._
import scala.util.{ Failure, Success, Try }

object GcsPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport extends GcsPluginKeys
  import autoImport._

  private val gcsPluginDefaultSettings = Seq(
    Global / gcsPublishFilePolicy     := GcsPublishFilePolicy.InheritedFromBucket,
    Global / googleCredentialsFile    := None,
    Global / googleCredentialsDisable := false
  )

  private val gcsPluginTaskInits = Seq(
    ( Global / onLoad ) := ( Global / onLoad ).value.andThen { state =>
      implicit val logger: Logger = state.log
      Try {
        GcsUrlHandlerFactory.install(
          googleCredentialsFile = googleCredentialsFile.value,
          googleCredentialsDisable = googleCredentialsDisable.value,
          gcsPublishFilePolicy = gcsPublishFilePolicy.value
        )
        logger.info( s"Google GCS/Artifact Registry support is enabled." )
      } match {
        case Success( _ )   => state
        case Failure( err ) => {
          logger.err(
            s"Unable to install GCS/Artifact Registry URL handlers: ${err}. Publishing/resolving artifacts from GCP is disabled."
          )
          state
        }
      }
    }
  )

  override def globalSettings: Seq[Setting[_]] = gcsPluginTaskInits ++ gcsPluginDefaultSettings ++ super.globalSettings
}
