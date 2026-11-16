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

import com.google.api.client.http.{ LowLevelHttpRequest, LowLevelHttpResponse }
import com.google.api.client.testing.http.{ MockHttpTransport, MockLowLevelHttpRequest, MockLowLevelHttpResponse }
import com.google.cloud.NoCredentials
import com.google.cloud.storage.{ BlobId, BlobInfo, ExistingBlob, Storage, StorageOptions }
import sbt.librarymanagement._
import sbt.util.Logger

import java.io.{ File, IOException }
import java.lang.reflect.{ InvocationHandler, Method, Proxy }
import java.nio.file.Files
import scala.collection.mutable.ListBuffer
import scala.util.{ Failure, Success }

class GcsPublisherSuite extends munit.FunSuite {

  private val stagingBase = FunFixture[File](
    setup = _ => Files.createTempDirectory( "gcs-publisher-suite" ).toFile,
    teardown = dir => sbt.io.IO.delete( dir )
  )

  private val registryRoot = "artifactregistry://europe-north1-maven.pkg.dev/project/snapshots"
  private val version      = "org/latestbit/foo_3/1.0"

  /** What sbt writes into a local Maven repository for one module. */
  private val stagedFiles =
    Seq(
      s"$version/foo_3-1.0.jar",
      s"$version/foo_3-1.0.jar.sha1",
      s"$version/foo_3-1.0.pom",
      s"$version/maven-metadata-local.xml"
    )

  /** sbt's publisher: writes `stagedFiles` when asked to publish to a staging resolver, and records every resolver. */
  private class SbtPublisher( target: GcsPublishTarget ) extends PublisherInterface {
    val resolverNames = ListBuffer.empty[String]

    override def publish( module: ModuleDescriptor, configuration: PublishConfiguration, log: Logger ): Unit = {
      val resolverName = configuration.resolverName.getOrElse( "" )
      resolverNames += resolverName
      if ( target.stagingResolver.name == resolverName )
        stagedFiles.foreach( file => sbt.io.IO.write( new File( target.stagingDir, file ), file ) )
    }

    override def moduleDescriptor( moduleSetting: ModuleDescriptorConfiguration ): ModuleDescriptor              = ???
    override def makePomFile( module: ModuleDescriptor, configuration: MakePomConfiguration, log: Logger ): File = ???
  }

  /** Answers HEAD with the status in `headStatus`, or 404 for other URLs, and every other request with 200. */
  private class RegistryTransport( headStatus: Map[String, Int] = Map.empty ) extends MockHttpTransport {
    val requests = ListBuffer.empty[( String, String )]

    override def buildRequest( method: String, url: String ): LowLevelHttpRequest =
      new MockLowLevelHttpRequest( url ) {
        override def execute(): LowLevelHttpResponse = {
          requests += ( ( method, url ) )
          val status = if ( method == "HEAD" ) headStatus.getOrElse( url, 404 ) else 200
          new MockLowLevelHttpResponse().setStatusCode( status )
        }
      }
  }

  /** A `Storage` that records the object and options of every `createFrom` and finds the `existing` objects only. */
  private class BucketStorage( existing: Set[String] = Set.empty ) {
    val created = ListBuffer.empty[( String, Seq[Storage.BlobWriteOption] )]

    private val options = StorageOptions
      .newBuilder()
      .setProjectId( "test" )
      .setCredentials( NoCredentials.getInstance() )
      .build()

    val storage: Storage = Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array( classOf[Storage] ),
        new InvocationHandler {
          override def invoke( proxy: Any, method: Method, args: Array[AnyRef] ): AnyRef = method.getName match {
            case "getOptions" => options
            case "get"        =>
              args( 0 ) match {
                case id: BlobId if existing.contains( id.getName ) => ExistingBlob( storage, id )
                case _                                             => null
              }
            case "createFrom" =>
              val options = args( 2 ).asInstanceOf[Array[Storage.BlobWriteOption]].toSeq
              created += ( ( args( 0 ).asInstanceOf[BlobInfo].getName, options ) )
              null
            case other => throw new UnsupportedOperationException( other )
          }
        }
      )
      .asInstanceOf[Storage]
  }

  private val module: ModuleDescriptor = new ModuleDescriptor {
    override def directDependencies: Vector[ModuleID]     = Vector.empty
    override def scalaModuleInfo: Option[ScalaModuleInfo] = None
    override def moduleSettings: ModuleSettings           = ???
    override def extraInputHash: Long                     = 0L
  }

  private def targetFor( resolver: Resolver, stagingDir: File ): GcsPublishTarget =
    GcsPublishTarget.of( resolver, stagingDir ).getOrElse( fail( s"no publish target for $resolver" ) )

  private def publishTo( resolverName: String, overwrite: Boolean ) =
    PublishConfiguration().withResolverName( resolverName ).withOverwrite( overwrite )

  private def registryClients( transport: RegistryTransport ): GoogleCloudClients =
    new GoogleCloudClients( ???, transport.createRequestFactory() )

  private def publisherFor(
      target: GcsPublishTarget,
      clients: GoogleCloudClients,
      policy: GcsPublishFilePolicy = GcsPublishFilePolicy.InheritedFromBucket
  ) = new GcsPublisher( Publisher( new SbtPublisher( target ) ), target, Success( clients ), policy )

  private val registryEndpoint = s"https://europe-north1-maven.pkg.dev/project/snapshots/$version"

  stagingBase.test( "artifactregistry:// publishes are staged by sbt and uploaded, other resolvers stay with sbt" ) {
    base =>
      val target    = targetFor( MavenRepository( "registry", registryRoot ), base )
      val sbt       = new SbtPublisher( target )
      val transport = new RegistryTransport()
      val publisher = new GcsPublisher(
        Publisher( sbt ),
        target,
        Success( registryClients( transport ) ),
        GcsPublishFilePolicy.PublicAccess
      )

      publisher.publish( module, publishTo( "registry", overwrite = true ), Logger.Null )
      publisher.publish( module, publishTo( Resolver.mavenLocal.name, overwrite = true ), Logger.Null )

      assertEquals( sbt.resolverNames.toList, List( target.stagingResolver.name, Resolver.mavenLocal.name ) )
      assertEquals(
        transport.requests.toList,
        List( "foo_3-1.0.jar", "foo_3-1.0.jar.sha1", "foo_3-1.0.pom" ).map( file =>
          ( "PUT", s"$registryEndpoint/$file" )
        )
      )
  }

  stagingBase.test( "a release already in the registry is refused before anything is uploaded" ) { base =>
    val target    = targetFor( MavenRepository( "registry", registryRoot ), base )
    val jar       = s"$registryEndpoint/foo_3-1.0.jar"
    val transport = new RegistryTransport( Map( jar -> 200 ) )
    val publisher = publisherFor( target, registryClients( transport ) )

    val refused = intercept[AlreadyPublished](
      publisher.publish( module, publishTo( "registry", overwrite = false ), Logger.Null )
    )
    assertEquals( refused.locations, Seq( jar ) )
    assertEquals( transport.requests.map( _._1 ).distinct.toList, List( "HEAD" ) )
  }

  stagingBase.test( "a registry that fails the existence check fails the publish before anything is uploaded" ) {
    base =>
      val target    = targetFor( MavenRepository( "registry", registryRoot ), base )
      val transport = new RegistryTransport( Map( s"$registryEndpoint/foo_3-1.0.jar" -> 403 ) )
      val publisher = publisherFor( target, registryClients( transport ) )

      intercept[IOException]( publisher.publish( module, publishTo( "registry", overwrite = false ), Logger.Null ) )
      assertEquals( transport.requests.map( _._1 ).distinct.toList, List( "HEAD" ) )
  }

  stagingBase.test( "a release already in the bucket is refused before anything is uploaded" ) { base =>
    val target    = targetFor( MavenRepository( "bucket", "gs://artifacts/releases/" ), base )
    val pom       = s"releases/$version/foo_3-1.0.pom"
    val bucket    = new BucketStorage( existing = Set( pom ) )
    val publisher = publisherFor( target, new GoogleCloudClients( bucket.storage, ??? ) )

    val refused = intercept[AlreadyPublished](
      publisher.publish( module, publishTo( "bucket", overwrite = false ), Logger.Null )
    )
    assertEquals( refused.locations, Seq( s"gs://artifacts/$pom" ) )
    assertEquals( bucket.created.toList, Nil )
  }

  stagingBase.test( "objects are named under the bucket prefix and are public-read only with PublicAccess" ) { base =>
    val target = targetFor( MavenRepository( "bucket", "gs://artifacts/releases/" ), base )
    def publishWith( policy: GcsPublishFilePolicy ) = {
      val bucket    = new BucketStorage
      val publisher = publisherFor( target, new GoogleCloudClients( bucket.storage, ??? ), policy )
      publisher.publish( module, publishTo( "bucket", overwrite = false ), Logger.Null )
      bucket.created.toList
    }

    val objects    = List( "foo_3-1.0.jar", "foo_3-1.0.jar.sha1", "foo_3-1.0.pom" ).map( f => s"releases/$version/$f" )
    val publicRead = Storage.BlobWriteOption.predefinedAcl( Storage.PredefinedAcl.PUBLIC_READ )
    assertEquals( publishWith( GcsPublishFilePolicy.PublicAccess ), objects.map( _ -> Seq( publicRead ) ) )
    assertEquals( publishWith( GcsPublishFilePolicy.InheritedFromBucket ), objects.map( _ -> Seq.empty ) )
  }

  stagingBase.test( "a publish fails without staging anything when the Google clients failed to load" ) { base =>
    val target    = targetFor( MavenRepository( "registry", registryRoot ), base )
    val sbt       = new SbtPublisher( target )
    val publisher =
      new GcsPublisher(
        Publisher( sbt ),
        target,
        Failure( new IllegalStateException( "no credentials" ) ),
        GcsPublishFilePolicy.PublicAccess
      )

    intercept[IOException]( publisher.publish( module, publishTo( "registry", overwrite = true ), Logger.Null ) )
    assertEquals( sbt.resolverNames.toList, Nil )
  }
}
