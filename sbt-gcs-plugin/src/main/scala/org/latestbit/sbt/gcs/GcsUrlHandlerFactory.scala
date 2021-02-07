package org.latestbit.sbt.gcs

import com.google.cloud.storage.Storage
import sbt.{ Logger, ProjectRef }

import java.net._

object GcsUrlHandlerFactory {

  /**
   * To install if it isn't already installed gcs:// URLs handler
   * without throwing a java.net.MalformedURLException.
   */
  def install( gcsStorage: Storage )( implicit logger: Logger, projectRef: ProjectRef ) = {
    try {
      new URL( "gcs://example.com" )
      logger.debug( s"The gcs:// URLStreamHandler is already installed for ${projectRef}" )
    } catch {
      // This means we haven't installed the handler, so install it
      case _: java.net.MalformedURLException =>
        logger.info( s"Installing gcs:// URLStreamHandler for ${projectRef}" )
        URL.setURLStreamHandlerFactory {
          case "gcs" => new GcsUrlHandler( gcsStorage )
          case _     => null
        }
    }
  }

}
