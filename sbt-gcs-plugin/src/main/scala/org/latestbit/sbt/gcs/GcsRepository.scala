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

import com.google.cloud.storage.{ BlobId, Storage }
import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.plugins.repository.{ AbstractRepository, Resource }
import sbt.Logger

import java.io.File
import java.util

import scala.collection.JavaConverters._

class GcsRepository( gcsStorage: Storage, bucketName: String, publishPolicy: GcsPublishFilePolicy )( implicit
    logger: Logger
) extends AbstractRepository {

  override def getResource( source: String ): Resource = {
    GcsRepositoryResource.createForSource( gcsStorage, bucketName, source )
  }

  override def get( source: String, destination: File ): Unit =
    GcsRepositoryResource.createForSource( gcsStorage, bucketName, source ).download( destination.toPath )

  override def list( parent: String ): util.List[String] =
    gcsStorage.list( bucketName ).getValues.asScala.map( _.getName ).toList.asJava

  override def put( artifact: Artifact, source: File, destination: String, overwrite: Boolean ): Unit = {
    Option( gcsStorage.get( bucketName ) ) match {
      case Some( gcsBucket ) => {
        logger.info(
          s"Publishing GCS artifact to '${destination}'. Artifact: ${artifact.getName}. Overwrite: ${overwrite}"
        )
        GcsRepositoryResource.upload(
          gcsBucket,
          publishPolicy,
          source,
          destination,
          getContentType( artifact )
        )
      }
      case _ => {
        throw new IllegalStateException( s"Unable to open bucket: ${bucketName} in GCS" )
      }
    }
  }

  private def getContentType( artifact: Artifact ): String = {
    Option( artifact )
      .flatMap( a => Option( a.getType ) )
      .map( _.toLowerCase )
      .collect {
        case "jar"  ⇒ "application/java-archive"
        case "xml"  ⇒ "application/xml"
        case "sha1" ⇒ "text/plain"
        case "md5"  ⇒ "text/plain"
        case "ivy"  ⇒ "application/xml"
      }
      .getOrElse( "application/octet-stream" )
  }
}
