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
import org.latestbit.sbt.gcs.artifactregistry.GcsArtifactRegistryMavenStore
import org.latestbit.sbt.gcs.gs.GcsMavenStore
import sbt.io.{ IO, PathFinder }
import sbt.util.Logger

import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

/** A resolver this plugin publishes to, with the local Maven repository sbt stages its files in first. */
private[gcs] final case class GcsPublishTarget(
    resolverName: String,
    repository: GcsMavenRepository,
    stagingDir: File
) {
  def stagingResolver: Resolver = MavenCache( s"$resolverName (GCS staging)", stagingDir )
}

private[gcs] object GcsPublishTarget {

  def all( resolvers: Seq[Resolver], stagingBase: File ): Seq[GcsPublishTarget] =
    resolvers.collect { case repository: MavenRepository =>
      GcsMavenRepository.parse( repository.root ).map { parsed =>
        val stagingDir = new File( stagingBase, URLEncoder.encode( repository.name, StandardCharsets.UTF_8.name ) )
        GcsPublishTarget( repository.name, parsed, stagingDir )
      }
    }.flatten
}

/** sbt's publisher, with publishing to gs:// and artifactregistry:// resolvers, which it rejects by itself. */
private[gcs] final class GcsPublisher(
    delegate: Publisher,
    targets: Seq[GcsPublishTarget],
    clients: GoogleCloudClients,
    gcsPublishFilePolicy: GcsPublishFilePolicy
) extends PublisherInterface {

  override def moduleDescriptor( moduleSetting: ModuleDescriptorConfiguration ): ModuleDescriptor =
    delegate.moduleDescriptor( moduleSetting )

  override def makePomFile( module: ModuleDescriptor, configuration: MakePomConfiguration, log: Logger ): File =
    delegate.makePomFile( module, configuration, log )

  override def publish( module: ModuleDescriptor, configuration: PublishConfiguration, log: Logger ): Unit =
    targets.find( target => configuration.resolverName.contains( target.resolverName ) ) match {
      case Some( target ) =>
        IO.delete( target.stagingDir )
        delegate.publish( module, configuration.withResolverName( target.stagingResolver.name ), log )
        upload( target, configuration.overwrite, log )
      case None =>
        delegate.publish( module, configuration, log )
    }

  private def upload( target: GcsPublishTarget, overwrite: Boolean, log: Logger ): Unit = {
    val store = target.repository match {
      case bucket: GcsMavenRepository.Bucket =>
        new GcsMavenStore( clients.storage, bucket, gcsPublishFilePolicy )
      case registry: GcsMavenRepository.ArtifactRegistry =>
        new GcsArtifactRegistryMavenStore( clients.httpRequestFactory, registry )
    }
    val files = staged( target.stagingDir )

    // A checksum is rewritten with its file, as Ivy does on sbt 1, so only the file decides whether it is published.
    if ( !overwrite ) {
      val published = files.map( _._1 ).filter( path => !path.isChecksum && store.exists( path ) )
      if ( published.nonEmpty )
        throw GcsPublishException.AlreadyPublished( published.map( target.repository.location ) )
    }

    files.foreach { case ( path, file ) =>
      val location = target.repository.location( path )
      try store.upload( path, file )
      catch { case NonFatal( failure ) => throw GcsPublishException.UploadFailed( location, failure ) }
      log.info( s"published $location" )
    }
  }

  // sbt writes this for snapshots in a local Maven repository only; remote repositories do not carry it.
  private val LocalMavenMetadata = "maven-metadata-local.xml"

  private def staged( stagingDir: File ): Vector[( MavenPath, Path )] = {
    val base = stagingDir.toPath
    PathFinder( stagingDir ).allPaths
      .get()
      .filter( _.isFile )
      .map( file =>
        MavenPath( base.relativize( file.toPath ).iterator.asScala.map( _.toString ).toVector ) -> file.toPath
      )
      .filterNot { case ( path, _ ) => path.segments.last == LocalMavenMetadata }
      .toVector
      .sortBy( _._1.toString )
  }
}
