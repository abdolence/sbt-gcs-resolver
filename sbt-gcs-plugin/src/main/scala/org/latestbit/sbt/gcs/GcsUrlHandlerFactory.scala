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
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.auth.http.{ HttpCredentialsAdapter, HttpTransportFactory }
import com.google.auth.oauth2.{ AccessToken, GoogleCredentials }
import com.google.common.collect.ImmutableList
import com.google.cloud.storage.StorageOptions
import org.apache.ivy.util.url.{ URLHandlerDispatcher, URLHandlerRegistry }
import org.latestbit.sbt.gcs.artifactregistry.{ GcsArtifactRegistryIvyUrlHandler, GcsArtifactRegistryUrlHandler }
import org.latestbit.sbt.gcs.gs.{ GcsIvyUrlHandler, GcsUrlHandler }
import sbt.{ File, Logger }

import java.io.FileInputStream
import java.net.{ URI, URL }
import java.nio.file.Path
import scala.collection.JavaConverters._
import scala.util.Try

object GcsUrlHandlerFactory {

  /** Registers gs:// and artifactregistry:// URL handlers unconditionally.
    * Credentials are loaded lazily on the first actual network request.
    */
  def install(
      googleCredentialsFile: Option[File],
      googleCredentialsDisable: Boolean,
      gcsPublishFilePolicy: GcsPublishFilePolicy
  )( implicit logger: Logger ) = {
    lazy val credentials: Option[GoogleCredentials] =
      if ( googleCredentialsDisable ) {
        logger.debug( s"Google Application Default Credentials lookup is disabled" )
        None
      } else {
        Some( loadGoogleCredentials( googleCredentialsFile.map( _.toPath ) ) )
      }
    lazy val gcsStorage = {
      val builder = StorageOptions.newBuilder()
      credentials.foreach( builder.setCredentials( _ ) )
      builder.build().getService
    }
    lazy val googleHttpRequestFactory = createHttpRequestFactory( credentials )

    // Install gs:// handler for JDK
    try {
      new URI( "gs://example.com" ).toURL
      new URI( "artifactregistry://example.com" ).toURL
      logger.debug( s"The gs:// and artifactregistry:// URLStreamHandlers are already installed" )
    } catch {
      case _: java.net.MalformedURLException =>
        logger.info( s"Installing gs:// and artifactregistry:// URLStreamHandlers" )
        URL.setURLStreamHandlerFactory {
          case "gs"               => new GcsUrlHandler( gcsStorage )
          case "artifactregistry" => new GcsArtifactRegistryUrlHandler( googleHttpRequestFactory )
          case _                  => null
        }
    }

    // Install gs:// handler for ivy
    val dispatcher: URLHandlerDispatcher = URLHandlerRegistry.getDefault match {
      case existingUrlHandlerDispatcher: URLHandlerDispatcher => existingUrlHandlerDispatcher
      case otherKindOfDispatcher                              =>
        logger.info( "Setting up Ivy URLHandlerDispatcher to handle gs:// and artifactregistry://" )
        val dispatcher: URLHandlerDispatcher = new URLHandlerDispatcher()
        dispatcher.setDefault( otherKindOfDispatcher )
        URLHandlerRegistry.setDefault( dispatcher )
        dispatcher
    }

    dispatcher.setDownloader( "gs", new GcsIvyUrlHandler( gcsStorage, gcsPublishFilePolicy ) )
    dispatcher.setDownloader( "artifactregistry", new GcsArtifactRegistryIvyUrlHandler( googleHttpRequestFactory ) )
  }

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
      val sbtUserRootDir = new java.io.File( userHomeDir, ".sbt" )
      if ( sbtUserRootDir.exists() && sbtUserRootDir.isDirectory ) {
        val googleAccountInSbt = new java.io.File( sbtUserRootDir, "gcs-resolver-google-account.json" )
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

  private final val httpTransportFactory: HttpTransportFactory = { () =>
    new NetHttpTransport()
  }

  private def createHttpRequestFactory( credentials: Option[GoogleCredentials] ): HttpRequestFactory = {
    val httpTransport = httpTransportFactory.create()
    credentials
      .map { creds =>
        httpTransport.createRequestFactory( new HttpCredentialsAdapter( creds ) )
      }
      .getOrElse {
        httpTransport.createRequestFactory()
      }
  }
}
