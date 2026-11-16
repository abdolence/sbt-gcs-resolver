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
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.{ AccessToken, GoogleCredentials }
import com.google.cloud.storage.{ BlobInfo, Storage }
import sbt.librarymanagement._
import sbt.util.Logger

import java.io.File
import java.lang.reflect.{ InvocationHandler, Method, Proxy }
import java.nio.file.Files
import scala.collection.mutable.ListBuffer

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
  private class SbtPublisher( targets: Seq[GcsPublishTarget] ) extends PublisherInterface {
    val resolverNames = ListBuffer.empty[String]

    override def publish( module: ModuleDescriptor, configuration: PublishConfiguration, log: Logger ): Unit = {
      val resolverName = configuration.resolverName.getOrElse( "" )
      resolverNames += resolverName
      targets.find( _.stagingResolver.name == resolverName ).foreach { target =>
        stagedFiles.foreach( file => sbt.io.IO.write( new File( target.stagingDir, file ), file ) )
      }
    }

    override def moduleDescriptor( moduleSetting: ModuleDescriptorConfiguration ): ModuleDescriptor              = ???
    override def makePomFile( module: ModuleDescriptor, configuration: MakePomConfiguration, log: Logger ): File = ???
  }

  /** Answers HEAD with 200 for `existing` URLs and 404 otherwise, and every other request with 200. */
  private class RegistryTransport( existing: Set[String] = Set.empty ) extends MockHttpTransport {
    val requests = ListBuffer.empty[( String, String, String )]

    override def buildRequest( method: String, url: String ): LowLevelHttpRequest =
      new MockLowLevelHttpRequest( url ) {
        override def execute(): LowLevelHttpResponse = {
          requests += ( ( method, url, getFirstHeaderValue( "Authorization" ) ) )
          val found = method != "HEAD" || existing.contains( url )
          new MockLowLevelHttpResponse().setStatusCode( if ( found ) 200 else 404 )
        }
      }
  }

  /** A `Storage` that records the object and options of every `createFrom` and finds no existing object. */
  private class BucketStorage {
    val created = ListBuffer.empty[( String, Seq[Storage.BlobWriteOption] )]

    val storage: Storage = Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array( classOf[Storage] ),
        new InvocationHandler {
          override def invoke( proxy: Any, method: Method, args: Array[AnyRef] ): AnyRef = method.getName match {
            case "get"        => null
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

  private def publishTo( resolverName: String, overwrite: Boolean ) =
    PublishConfiguration().withResolverName( resolverName ).withOverwrite( overwrite )

  private def registryClients( transport: RegistryTransport ): GoogleCloudClients = {
    val credentials = GoogleCredentials.create( AccessToken.newBuilder().setTokenValue( "test-token" ).build() )
    new GoogleCloudClients( ???, transport.createRequestFactory( new HttpCredentialsAdapter( credentials ) ) )
  }

  stagingBase.test( "artifactregistry:// publishes are staged by sbt and uploaded, other resolvers stay with sbt" ) {
    base =>
      val targets =
        GcsPublishTarget.all( Seq( MavenRepository( "registry", registryRoot ), Resolver.mavenLocal ), base )
      val sbt       = new SbtPublisher( targets )
      val transport = new RegistryTransport()
      val publisher =
        new GcsPublisher( Publisher( sbt ), targets, registryClients( transport ), GcsPublishFilePolicy.PublicAccess )

      publisher.publish( module, publishTo( "registry", overwrite = true ), Logger.Null )
      publisher.publish( module, publishTo( Resolver.mavenLocal.name, overwrite = true ), Logger.Null )

      assertEquals( sbt.resolverNames.toList, List( targets.head.stagingResolver.name, Resolver.mavenLocal.name ) )
      val endpoint = s"https://europe-north1-maven.pkg.dev/project/snapshots/$version"
      assertEquals(
        transport.requests.toList,
        List( "foo_3-1.0.jar", "foo_3-1.0.jar.sha1", "foo_3-1.0.pom" ).map { file =>
          ( "PUT", s"$endpoint/$file", "Bearer test-token" )
        }
      )
  }

  stagingBase.test( "a release already in the registry is refused before anything is uploaded" ) { base =>
    val targets   = GcsPublishTarget.all( Seq( MavenRepository( "registry", registryRoot ) ), base )
    val jar       = s"https://europe-north1-maven.pkg.dev/project/snapshots/$version/foo_3-1.0.jar"
    val transport = new RegistryTransport( existing = Set( jar ) )
    val publisher = new GcsPublisher(
      Publisher( new SbtPublisher( targets ) ),
      targets,
      registryClients( transport ),
      GcsPublishFilePolicy.InheritedFromBucket
    )

    val refused = intercept[GcsPublishException.AlreadyPublished](
      publisher.publish( module, publishTo( "registry", overwrite = false ), Logger.Null )
    )
    assertEquals( refused.locations, Seq( jar ) )
    assertEquals( transport.requests.map( _._1 ).distinct.toList, List( "HEAD" ) )
  }

  stagingBase.test( "objects are named under the bucket prefix and are public-read only with PublicAccess" ) { base =>
    val targets = GcsPublishTarget.all( Seq( MavenRepository( "bucket", "gs://artifacts/releases/" ) ), base )
    def publishWith( policy: GcsPublishFilePolicy ) = {
      val bucket    = new BucketStorage
      val clients   = new GoogleCloudClients( bucket.storage, ??? )
      val publisher = new GcsPublisher( Publisher( new SbtPublisher( targets ) ), targets, clients, policy )
      publisher.publish( module, publishTo( "bucket", overwrite = false ), Logger.Null )
      bucket.created.toList
    }

    val objects    = List( "foo_3-1.0.jar", "foo_3-1.0.jar.sha1", "foo_3-1.0.pom" ).map( f => s"releases/$version/$f" )
    val publicRead = Storage.BlobWriteOption.predefinedAcl( Storage.PredefinedAcl.PUBLIC_READ )
    assertEquals( publishWith( GcsPublishFilePolicy.PublicAccess ), objects.map( _ -> Seq( publicRead ) ) )
    assertEquals( publishWith( GcsPublishFilePolicy.InheritedFromBucket ), objects.map( _ -> Seq.empty ) )
  }
}
