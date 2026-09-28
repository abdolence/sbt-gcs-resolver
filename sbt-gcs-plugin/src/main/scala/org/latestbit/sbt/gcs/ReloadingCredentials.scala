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

import com.google.auth.{ CredentialTypeForMetrics, Credentials, Retryable }
import com.google.auth.oauth2.GoogleCredentials
import sbt.Logger

import java.io.IOException
import java.net.URI
import java.util.{ List => JList, Map => JMap }

/** Credentials that run `load` again when the current ones can no longer get an access token, so a source replaced on
  * disk (a fresh `gcloud auth application-default login`, a rotated key file) is used without restarting the JVM.
  *
  * A reload happens when `getRequestMetadata` or `refresh` fails with an `IOException` that is not transient, and the
  * call is then retried once on the reloaded instance. Transient failures (a `Retryable` that is retryable, or an
  * interrupt) are rethrown unchanged, as is the failure of the retry. HTTP clients call `refresh` after a 401 and
  * `getRequestMetadata` on every request, so a swap is seen on the next request without rebuilding them.
  *
  * `load` is called once on construction and once per failed instance; it must be safe to call repeatedly and may
  * throw, in which case its exception propagates, the current instance is kept and the next failure tries again.
  *
  * Thread-safe: callers that fail on the same instance at the same time share one reload.
  */
final class ReloadingCredentials( load: () => GoogleCredentials )( implicit logger: Logger ) extends Credentials {

  @volatile private var current: GoogleCredentials = load()

  /** The instance currently answering requests. */
  def delegate: GoogleCredentials = current

  override def getRequestMetadata( uri: URI ): JMap[String, JList[String]] = withReload( _.getRequestMetadata( uri ) )

  override def refresh(): Unit = withReload( _.refresh() )

  private def withReload[T]( call: GoogleCredentials => T ): T = {
    val used = current
    try call( used )
    catch {
      case failure: IOException if !isTransient( failure ) =>
        call( reloadReplacing( used, failure ) )
    }
  }

  // A network error says nothing about the credentials, and reloading would hide it behind a second attempt.
  private def isTransient( failure: IOException ): Boolean =
    failure match {
      case retryable: Retryable if retryable.isRetryable => true
      case _                                             => Thread.currentThread().isInterrupted
    }

  // Parallel downloads fail together on the same instance. Only the first caller that still sees it reloads; the rest
  // retry with whatever that caller installed.
  private def reloadReplacing( failed: GoogleCredentials, cause: IOException ): GoogleCredentials =
    synchronized {
      if ( current eq failed ) {
        // The token endpoint's message carries the whole JSON response; its first line is the status.
        val reason =
          Option( cause.getMessage ).flatMap( _.linesIterator.find( _.nonEmpty ) ).getOrElse( cause.toString )
        logger.warn( s"Google credentials can no longer get an access token ($reason), reloading them" )
        current = load()
      }
      current
    }

  override def getAuthenticationType: String                      = current.getAuthenticationType
  override def hasRequestMetadata: Boolean                        = current.hasRequestMetadata
  override def hasRequestMetadataOnly: Boolean                    = current.hasRequestMetadataOnly
  override def getUniverseDomain: String                          = current.getUniverseDomain
  override def getMetricsCredentialType: CredentialTypeForMetrics = current.getMetricsCredentialType
}
