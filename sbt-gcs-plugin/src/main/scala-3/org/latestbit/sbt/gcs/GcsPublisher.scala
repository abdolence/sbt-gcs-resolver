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

import com.google.api.client.http.{ FileContent, HttpResponseException }
import com.google.cloud.storage.{ BlobInfo, Storage }
import sbt.librarymanagement.{
  MavenCache,
  MavenRepository,
  MakePomConfiguration,
  ModuleDescriptor,
  ModuleDescriptorConfiguration,
  PublishConfiguration,
  Publisher,
  PublisherInterface,
  Resolver
}
import sbt.io.{ IO, PathFinder }
import sbt.util.Logger

import java.io.{ File, IOException }
import scala.jdk.CollectionConverters._
import scala.util.Try

final case class GcsPublishTarget(
    resolverName: String,
    repository: GcsMavenRepository,
    stagingDir: File
) {
  def stagingResolver: Resolver = MavenCache( s"$resolverName (GCS staging)", stagingDir )
}

object GcsPublishTarget {

  def of( resolver: Resolver, stagingDir: File ): Option[GcsPublishTarget] = resolver match {
    case repository: MavenRepository =>
      GcsMavenRepository.parse( repository.root ).map( GcsPublishTarget( repository.name, _, stagingDir ) )
    case _ => None
  }
}

/** The files at `locations` exist and the publish configuration does not overwrite them; nothing was uploaded. */
final class AlreadyPublished( val locations: Seq[String] )
    extends IOException(
      s"Already published, and overwriting is disabled: ${locations.mkString( ", " )}\n\t" +
        "Publish a new version, or relax the setting as follows:\n\t" +
        "publish / publishConfiguration := publishConfiguration.value.withOverwrite(true)"
    )

/** `clients` is the outcome of creating the Google clients when sbt loaded the build. A failure is reported when a
  * publish to `target` is attempted, not on loading.
  */
final class GcsPublisher(
    delegate: Publisher,
    target: GcsPublishTarget,
    clients: Try[GoogleCloudClients],
    gcsPublishFilePolicy: GcsPublishFilePolicy
) extends PublisherInterface {

  override def moduleDescriptor( moduleSetting: ModuleDescriptorConfiguration ): ModuleDescriptor =
    delegate.moduleDescriptor( moduleSetting )

  override def makePomFile( module: ModuleDescriptor, configuration: MakePomConfiguration, log: Logger ): File =
    delegate.makePomFile( module, configuration, log )

  override def publish( module: ModuleDescriptor, configuration: PublishConfiguration, log: Logger ): Unit =
    if ( configuration.resolverName.contains( target.resolverName ) ) {
      val googleClients = clients.fold(
        failure =>
          throw new IOException(
            s"Unable to publish to ${target.resolverName}: the GCS/Artifact Registry support failed to load: $failure"
          ),
        identity
      )
      IO.delete( target.stagingDir )
      delegate.publish( module, configuration.withResolverName( target.stagingResolver.name ), log )
      upload( googleClients, configuration.overwrite, log )
    } else
      delegate.publish( module, configuration, log )

  private def upload( clients: GoogleCloudClients, overwrite: Boolean, log: Logger ): Unit = {
    val files = staged()

    // Only artifacts are checked, as sbt does for a remote Maven repository; their checksums are written with them.
    if ( !overwrite ) {
      val published = files.map( _._1 ).filter( path => !isChecksum( path ) && exists( clients, path ) )
      if ( published.nonEmpty )
        throw new AlreadyPublished( published.map( target.repository.location ) )
    }

    files.foreach { case ( path, file ) =>
      put( clients, path, file )
      log.info( s"published ${target.repository.location( path )}" )
    }
  }

  private def isChecksum( path: String ): Boolean = path.endsWith( ".md5" ) || path.endsWith( ".sha1" )

  /** Only a 404 means absent: any other failure of the check is an error, as the file may exist. */
  private def exists( clients: GoogleCloudClients, path: String ): Boolean = target.repository match {
    case bucket: GcsMavenRepository.Bucket =>
      Option( clients.storage.get( bucket.blobId( path ) ) ).isDefined
    case registry: GcsMavenRepository.ArtifactRegistry =>
      try {
        clients.httpRequestFactory.buildHeadRequest( registry.url( path ) ).execute().disconnect()
        true
      } catch {
        case failure: HttpResponseException if failure.getStatusCode == 404 => false
      }
  }

  private def put( clients: GoogleCloudClients, path: String, file: File ): Unit = {
    val contentType = ContentTypes.forPath( path )
    target.repository match {
      case bucket: GcsMavenRepository.Bucket =>
        val blob = BlobInfo.newBuilder( bucket.blobId( path ) ).setContentType( contentType ).build()
        gcsPublishFilePolicy match {
          case GcsPublishFilePolicy.PublicAccess =>
            clients.storage.createFrom(
              blob,
              file.toPath,
              Storage.BlobWriteOption.predefinedAcl( Storage.PredefinedAcl.PUBLIC_READ )
            )
          case GcsPublishFilePolicy.InheritedFromBucket =>
            clients.storage.createFrom( blob, file.toPath )
        }
      case registry: GcsMavenRepository.ArtifactRegistry =>
        clients.httpRequestFactory
          .buildPutRequest( registry.url( path ), new FileContent( contentType, file ) )
          .execute()
          .disconnect()
    }
  }

  private def staged(): Vector[( String, File )] = {
    val base = target.stagingDir.toPath
    PathFinder( target.stagingDir ).allPaths
      .get()
      .filter( _.isFile )
      .map( file => base.relativize( file.toPath ).iterator.asScala.mkString( "/" ) -> file )
      .filterNot { case ( path, _ ) => path.split( '/' ).last == LocalMavenMetadata }
      .toVector
      .sortBy( _._1 )
  }

  // sbt writes this for snapshots in a local Maven repository only; remote repositories do not carry it.
  private val LocalMavenMetadata = "maven-metadata-local.xml"
}
