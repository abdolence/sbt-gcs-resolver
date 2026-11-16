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

import java.net.URI
import java.nio.file.Path
import scala.jdk.CollectionConverters._
import scala.util.Try

/** A file's path inside a Maven repository, such as `org/latestbit/foo_3/1.0/foo_3-1.0.pom`. */
private[gcs] final case class MavenPath( segments: Vector[String] ) {

  def isChecksum: Boolean = segments.last.endsWith( ".md5" ) || segments.last.endsWith( ".sha1" )

  def contentType: String =
    segments.last.drop( segments.last.lastIndexOf( '.' ) + 1 ).toLowerCase match {
      case "jar"                  => "application/java-archive"
      case "pom" | "xml"          => "application/xml"
      case "md5" | "sha1" | "asc" => "text/plain"
      case _                      => "application/octet-stream"
    }

  override def toString: String = segments.mkString( "/" )
}

/** A Maven repository root that sbt cannot publish to by itself: a GCS bucket or an Artifact Registry repository. */
private[gcs] sealed trait GcsMavenRepository {

  /** Where `path` is published, for logs and errors. */
  def location( path: MavenPath ): String
}

private[gcs] object GcsMavenRepository {

  /** `gs://<name>/<prefix>` */
  final case class Bucket( name: String, prefix: Vector[String] ) extends GcsMavenRepository {
    def blobId( path: MavenPath ): BlobId = BlobId.of( name, ( prefix ++ path.segments ).mkString( "/" ) )

    override def location( path: MavenPath ): String = s"gs://$name/${blobId( path ).getName}"
  }

  /** `artifactregistry://<host>/<repository>`, served over HTTPS at the same host and path. */
  final case class ArtifactRegistry( host: String, repository: Vector[String] ) extends GcsMavenRepository {
    def url( path: MavenPath ): GenericUrl = {
      val url = new GenericUrl()
      url.setScheme( "https" )
      url.setHost( host )
      url.setPathParts( ( "" +: ( repository ++ path.segments ) ).asJava )
      url
    }

    override def location( path: MavenPath ): String = url( path ).build()
  }

  /** The repository a resolver root names, or `None` for any other root. */
  def parse( root: String ): Option[GcsMavenRepository] =
    Try( new URI( root ) ).toOption.flatMap { uri =>
      val segments = Option( uri.getPath ).toVector.flatMap( _.split( '/' ).toVector ).filter( _.nonEmpty )
      ( Option( uri.getScheme ), Option( uri.getAuthority ) ) match {
        case ( Some( "gs" ), Some( bucket ) )             => Some( Bucket( bucket, segments ) )
        case ( Some( "artifactregistry" ), Some( host ) ) => Some( ArtifactRegistry( host, segments ) )
        case _                                            => None
      }
    }
}

private[gcs] trait MavenRepositoryStore {
  def exists( path: MavenPath ): Boolean
  def upload( path: MavenPath, file: Path ): Unit
}
