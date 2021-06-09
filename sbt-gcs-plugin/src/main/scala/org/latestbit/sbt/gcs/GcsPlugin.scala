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

import com.google.auth.oauth2.GoogleCredentials
import sbt.Keys._
import sbt._

import java.io.FileInputStream
import java.nio.file.Path
import scala.util.Try
import scala.collection.JavaConverters._

object GcsPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport extends GcsPluginKeys
  import autoImport._

  private val gcsPluginDefaultSettings = Seq(
    gcsPublishFilePolicy := GcsPublishFilePolicy.InheritedFromBucket,
    googleCredentialsFile := None
  )

  private val gcsPluginTaskInits = Seq(
    onLoad in Global := ( onLoad in Global ).value.andThen { state =>
      implicit val logger: Logger         = state.log
      implicit val projectRef: ProjectRef = thisProjectRef.value
      val googleCredentials = loadGoogleCredentials(
        googleCredentialsFile.value.map( _.toPath )
      )
      GcsUrlHandlerFactory.install( googleCredentials, gcsPublishFilePolicy.value )
      state
    }
  )

  override def projectSettings: Seq[Def.Setting[_]] =
    gcsPluginDefaultSettings ++
      gcsPluginTaskInits ++
      super.projectSettings

  private def loadGoogleCredentials(
      gcsCredentialsFilePath: Option[Path]
  )( implicit logger: Logger, projectRef: ProjectRef ): GoogleCredentials = {
    gcsCredentialsFilePath
      .orElse( lookupGoogleCredentialsInSbtDir() )
      .map { path =>
        logger.debug( s"Loading Google credentials from: ${path.toAbsolutePath.toString} for ${projectRef.toString}" )
        GoogleCredentials.fromStream( new FileInputStream( path.toFile ) ).createScoped(googleCredentialsScopes.asJavaCollection)
      }
      .getOrElse {
        logger.debug( s"Loading default Google credentials for ${projectRef.toString}" )
        GoogleCredentials.getApplicationDefault().createScoped(googleCredentialsScopes.asJavaCollection)
      }

  }

  private def lookupGoogleCredentialsInSbtDir(): Option[Path] = {
    Try(Option(System.getProperty( "user.home" ))).toOption.flatten.flatMap { userHomeDir =>
      val sbtUserRootDir = new File( userHomeDir, ".sbt" )
      if (sbtUserRootDir.exists() && sbtUserRootDir.isDirectory) {
        val googleAccountInSbt = new File( sbtUserRootDir, "gcs-resolver-google-account.json" )
        if (googleAccountInSbt.exists() && googleAccountInSbt.isFile) {
          Some( googleAccountInSbt.toPath )
        } else
          None
      } else
        None
    }
  }

  private final val googleCredentialsScopes: Set[String] = Set(
    "https://www.googleapis.com/auth/cloud-platform",
    "https://www.googleapis.com/auth/cloud-platform.read-only"
  )
}
