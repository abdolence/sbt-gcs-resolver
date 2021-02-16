package org.latestbit.sbt.gcs

import com.google.api.client.http.HttpRequestFactory
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.auth.http.{ HttpCredentialsAdapter, HttpTransportFactory }
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.StorageOptions
import org.apache.ivy.util.url.{ URLHandlerDispatcher, URLHandlerRegistry }
import org.latestbit.sbt.gcs.artifactregistry.{ GcsArtifactRegistryIvyUrlHandler, GcsArtifactRegistryUrlHandler }
import org.latestbit.sbt.gcs.gs.{ GcsIvyUrlHandler, GcsUrlHandler }
import sbt.{ Logger, ProjectRef }

import java.net.URL

object GcsUrlHandlerFactory {

  /**
   * To install if it isn't already installed gs:// URLs handler
   * without throwing a java.net.MalformedURLException.
   */
  def install( credentials: GoogleCredentials, gcsPublishFilePolicy: GcsPublishFilePolicy )( implicit
      logger: Logger,
      projectRef: ProjectRef
  ) = {

    val gcsStorage               = StorageOptions.newBuilder().setCredentials( credentials ).build().getService
    val googleHttpRequestFactory = createHttpRequestFactory( credentials )

    // Install gs:// handler for JDK
    try {
      new URL( "gs://example.com" )
      new URL( "artifactregistry://example.com" )
      logger.debug( s"The gs:// and artifactregistry:// URLStreamHandlers are already installed for ${projectRef}" )
    } catch {
      case _: java.net.MalformedURLException =>
        logger.info( s"Installing gs:// and artifactregistry:// URLStreamHandlers for ${projectRef}" )
        URL.setURLStreamHandlerFactory {
          case "gs"               => new GcsUrlHandler( gcsStorage )
          case "artifactregistry" => new GcsArtifactRegistryUrlHandler( googleHttpRequestFactory )
          case _                  => null
        }
    }

    // Install gs:// handler for ivy
    val dispatcher: URLHandlerDispatcher = URLHandlerRegistry.getDefault match {
      case existingUrlHandlerDispatcher: URLHandlerDispatcher => existingUrlHandlerDispatcher
      case otherKindOfDispatcher =>
        logger.info( "Setting up Ivy URLHandlerDispatcher to handle gs:// and artifactregistry://" )
        val dispatcher: URLHandlerDispatcher = new URLHandlerDispatcher()
        dispatcher.setDefault( otherKindOfDispatcher )
        URLHandlerRegistry.setDefault( dispatcher )
        dispatcher
    }

    dispatcher.setDownloader( "gs", new GcsIvyUrlHandler( gcsStorage, gcsPublishFilePolicy ) )
    dispatcher.setDownloader( "artifactregistry", new GcsArtifactRegistryIvyUrlHandler( googleHttpRequestFactory ) )
  }

  private final val httpTransportFactory: HttpTransportFactory = { () =>
    new NetHttpTransport()
  }

  private def createHttpRequestFactory( credentials: GoogleCredentials ): HttpRequestFactory = {
    val requestInitializer = new HttpCredentialsAdapter( credentials )
    val httpTransport      = httpTransportFactory.create()
    httpTransport.createRequestFactory( requestInitializer )
  }
}
