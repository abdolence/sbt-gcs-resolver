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
import com.google.common.collect.ImmutableList
import sbt.Keys._
import sbt._

import com.google.auth.oauth2.AccessToken
import java.io.FileInputStream
import java.nio.file.Path
import scala.collection.JavaConverters._
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
    onLoad in Global := ( onLoad in Global ).value.andThen { state =>
      implicit val logger: Logger = state.log
      Try {
        val googleCredentials = if ( googleCredentialsDisable.value ) {
          logger.debug( s"Google Application Default Credentials lookup is disabled" )
          None
        } else {
          Some(
            loadGoogleCredentials(
              googleCredentialsFile.value.map( _.toPath )
            )
          )
        }
        GcsUrlHandlerFactory.install( googleCredentials, gcsPublishFilePolicy.value )
        logger.info(
          s"Google GCS/Artifact Registry support is enabled. Google Credentials: ${googleCredentials.map( _ => "identified" ).getOrElse( "unused" )} "
        )
      } match {
        case Success( _ )   => state
        case Failure( err ) => {
          logger.err(
            s"Unable to find/initialise google credentials: ${err}. Publishing/resolving artifacts from GCP is disabled."
          )
          state
        }
      }
    }
  )

  override def globalSettings: Seq[Setting[_]] = gcsPluginTaskInits ++ gcsPluginDefaultSettings ++ super.globalSettings

  private def loadGoogleCredentials(
      gcsCredentialsFilePath: Option[Path]
  )( implicit logger: Logger ): GoogleCredentials = {
    val scopes: java.util.Collection[String] =
      ImmutableList.copyOf( GoogleCredentialsScopes.asJavaCollection.iterator() )
    gcsCredentialsFilePath
      .orElse( lookupGoogleCredentialsInSbtDir() )
      .map { path =>
        logger.debug( s"Loading Google credentials from: ${path.toAbsolutePath.toString}" )
        GoogleCredentials
          .fromStream( new FileInputStream( path.toFile ) )
          .createScoped( scopes )
      }
      .orElse {
        Option( System.getenv( "GOOGLE_OAUTH_ACCESS_TOKEN" ) ).map( accessToken =>
          GoogleCredentials
            .create( AccessToken.newBuilder().setTokenValue( accessToken ).build() )
            .createScoped( scopes )
        )
      }
      .getOrElse {
        logger.debug( s"Loading default Google credentials" )
        GoogleCredentials.getApplicationDefault().createScoped( scopes )
      }

  }

  private def lookupGoogleCredentialsInSbtDir(): Option[Path] = {
    Try( Option( System.getProperty( "user.home" ) ) ).toOption.flatten.flatMap { userHomeDir =>
      val sbtUserRootDir = new File( userHomeDir, ".sbt" )
      if ( sbtUserRootDir.exists() && sbtUserRootDir.isDirectory ) {
        val googleAccountInSbt = new File( sbtUserRootDir, "gcs-resolver-google-account.json" )
        if ( googleAccountInSbt.exists() && googleAccountInSbt.isFile ) {
          Some( googleAccountInSbt.toPath )
        } else
          None
      } else
        None
    }
  }

  private final val GoogleCredentialsScopes: Set[String] = Set(
    "https://www.googleapis.com/auth/cloud-platform",
    "https://www.googleapis.com/auth/cloud-platform.read-only"
  )
}
