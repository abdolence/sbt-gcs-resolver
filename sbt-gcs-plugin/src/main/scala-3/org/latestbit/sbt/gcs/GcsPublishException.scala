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

import java.io.IOException

/** A failed publish to a gs:// or artifactregistry:// resolver. */
sealed abstract class GcsPublishException( message: String, cause: Option[Throwable] )
    extends IOException( message, cause.orNull )

object GcsPublishException {

  /** The files at `locations` exist and the publish configuration does not overwrite them; nothing was uploaded. */
  final case class AlreadyPublished( locations: Seq[String] )
      extends GcsPublishException(
        s"Already published, and overwriting is disabled: ${locations.mkString( ", " )}",
        None
      )

  final case class UploadFailed( location: String, failure: Throwable )
      extends GcsPublishException( s"Unable to publish $location: ${failure.getMessage}", Some( failure ) )
}
