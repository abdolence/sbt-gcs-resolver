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

import com.google.api.client.http.GenericUrl
import com.google.cloud.storage.BlobId
import org.latestbit.sbt.gcs.artifactregistry.GcsArtifactRegistryGenericUrlFactory
import org.latestbit.sbt.gcs.gs.GcsUrlConnection

import java.net.URI
import scala.util.Try

/** A repository is rooted at the path of its resolver's URL, `rootPath`, without a trailing `/`. A published file is
  * addressed by its path relative to the root, such as `org/latestbit/foo_3/1.0/foo_3-1.0.pom`.
  */
private[gcs] sealed trait GcsMavenRepository {
  def location( path: String ): String
}

private[gcs] object GcsMavenRepository {

  /** `gs://<name><rootPath>` */
  final case class Bucket( name: String, rootPath: String ) extends GcsMavenRepository {
    def blobId( path: String ): BlobId = GcsUrlConnection.toBlobId( name, s"$rootPath/$path" )

    override def location( path: String ): String = s"gs://$name/${blobId( path ).getName}"
  }

  /** `artifactregistry://<host><rootPath>`, served over HTTPS at the same host and path. */
  final case class ArtifactRegistry( host: String, rootPath: String ) extends GcsMavenRepository {
    def url( path: String ): GenericUrl = GcsArtifactRegistryGenericUrlFactory.create( host, s"$rootPath/$path" )

    override def location( path: String ): String = url( path ).build()
  }

  /** The repository a resolver root names, or `None` for any other root. */
  def parse( root: String ): Option[GcsMavenRepository] =
    Try( new URI( root ) ).toOption.flatMap { uri =>
      val rootPath = Option( uri.getPath ).getOrElse( "" ).stripSuffix( "/" )
      ( Option( uri.getScheme ), Option( uri.getAuthority ) ) match {
        case ( Some( "gs" ), Some( bucket ) )             => Some( Bucket( bucket, rootPath ) )
        case ( Some( "artifactregistry" ), Some( host ) ) => Some( ArtifactRegistry( host, rootPath ) )
        case _                                            => None
      }
    }
}
