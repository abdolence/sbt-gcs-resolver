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

import com.google.cloud.storage.Storage
import sbt.{Logger, ProjectRef}

import java.net._

object GcsUrlHandlerFactory {

  /**
   * To install if it isn't already installed gcs:// URLs handler
   * without throwing a java.net.MalformedURLException.
   */
  def install( gcsStorage: Storage )( implicit logger: Logger, projectRef: ProjectRef ) = {
    try {
      new URL( "gs://example.com" )
      logger.debug( s"The gs:// URLStreamHandler is already installed for ${projectRef}" )
    } catch {
      // This means we haven't installed the handler, so install it
      case _: java.net.MalformedURLException =>
        logger.info( s"Installing gs:// URLStreamHandler for ${projectRef}" )
        URL.setURLStreamHandlerFactory {
          case "gs" => new GcsUrlHandler( gcsStorage )
          case _     => null
        }
    }
  }

}
