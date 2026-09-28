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

import com.google.auth.Retryable
import com.google.auth.oauth2.GoogleCredentials
import sbt.Logger

import java.io.IOException
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ Callable, CountDownLatch, Executors, TimeUnit }
import java.util.{ Collections, List => JList, Map => JMap }
import scala.collection.JavaConverters._

class ReloadingCredentialsSuite extends munit.FunSuite {
  private implicit val logger: Logger = Logger.Null

  private val uri = new URI( "https://storage.googleapis.com/bucket/object" )

  /** Answers with a header naming `token`, or throws `failure` on each of the first `failures` calls (forever when
    * negative). `onCall` runs before either outcome. `refresh` throws a non-retryable `IOException` when
    * `refreshFails`.
    */
  private class FakeCredentials(
      token: String,
      failures: Int,
      onCall: () => Unit = () => (),
      failure: String => IOException = new IOException( _ ),
      refreshFails: Boolean = false
  ) extends GoogleCredentials {
    private val calls = new AtomicInteger( 0 )
    val refreshes     = new AtomicInteger( 0 )

    override def getRequestMetadata( uri: URI ): JMap[String, JList[String]] = {
      onCall()
      val call = calls.incrementAndGet()
      if ( failures < 0 || call <= failures )
        throw failure( s"$token rejected" )
      Collections.singletonMap( "Authorization", Collections.singletonList( s"Bearer $token" ) )
    }

    override def refresh(): Unit = {
      refreshes.incrementAndGet()
      if ( refreshFails ) throw new IOException( s"$token refresh rejected" )
    }
  }

  private class RetryableIOException( message: String ) extends IOException( message ) with Retryable {
    override def isRetryable: Boolean = true
    override def getRetryCount: Int   = 0
  }

  /** A loader that hands out `sources` in order, repeating the last one, and counts its calls. A source given as `None`
    * makes that call throw.
    */
  private class CountingLoader( sources: Seq[Option[GoogleCredentials]] ) extends ( () => GoogleCredentials ) {
    val calls = new AtomicInteger( 0 )

    override def apply(): GoogleCredentials = {
      val call = calls.incrementAndGet()
      sources( math.min( call, sources.size ) - 1 ).getOrElse( throw new IOException( s"load $call failed" ) )
    }
  }

  private def loading( sources: GoogleCredentials* ) = new CountingLoader( sources.map( Some( _ ) ) )

  private def bearer( credentials: ReloadingCredentials ): String =
    credentials.getRequestMetadata( uri ).get( "Authorization" ).asScala.mkString

  test( "a rejected delegate is reloaded once and the request retried with the new one" ) {
    val loader      = loading( new FakeCredentials( "old", failures = -1 ), new FakeCredentials( "new", 0 ) )
    val credentials = new ReloadingCredentials( loader )

    assertEquals( bearer( credentials ), "Bearer new" )
    assertEquals( bearer( credentials ), "Bearer new" )
    assertEquals( loader.calls.get(), 2 )
  }

  test( "concurrent failures on the same delegate trigger exactly one reload" ) {
    val callers = 8
    val arrived = new CountDownLatch( callers )
    val failing = new FakeCredentials(
      "old",
      failures = -1,
      onCall = { () =>
        arrived.countDown()
        arrived.await( 10, TimeUnit.SECONDS )
        ()
      }
    )
    val loader      = loading( failing, new FakeCredentials( "new", 0 ) )
    val credentials = new ReloadingCredentials( loader )
    val pool        = Executors.newFixedThreadPool( callers )
    try {
      val results = ( 1 to callers ).map { _ =>
        pool.submit( new Callable[String] { override def call(): String = bearer( credentials ) } )
      }
      results.foreach( result => assertEquals( result.get( 30, TimeUnit.SECONDS ), "Bearer new" ) )
    } finally pool.shutdownNow()

    assertEquals( loader.calls.get(), 2 )
  }

  test( "a delegate that keeps failing is reloaded once and its own IOException is rethrown" ) {
    val loader      = loading( new FakeCredentials( "broken", failures = -1 ) )
    val credentials = new ReloadingCredentials( loader )

    val thrown = intercept[IOException]( credentials.getRequestMetadata( uri ) )
    assertEquals( thrown.getMessage, "broken rejected" )
    assertEquals( loader.calls.get(), 2 )
  }

  test( "a failed refresh reloads the delegate and refreshes the new one" ) {
    val old         = new FakeCredentials( "old", 0, refreshFails = true )
    val fresh       = new FakeCredentials( "new", 0 )
    val loader      = loading( old, fresh )
    val credentials = new ReloadingCredentials( loader )

    credentials.refresh()
    assertEquals( loader.calls.get(), 2 )
    assertEquals( fresh.refreshes.get(), 1 )
    assertEquals( bearer( credentials ), "Bearer new" )
  }

  test( "a retryable IOException is rethrown unchanged without a reload" ) {
    val original = new RetryableIOException( "network down" )
    val loader   = loading( new FakeCredentials( "old", -1, failure = _ => original ), new FakeCredentials( "new", 0 ) )
    val credentials = new ReloadingCredentials( loader )

    val thrown = intercept[IOException]( credentials.getRequestMetadata( uri ) )
    assert( thrown eq original, s"expected the delegate's exception, got $thrown" )
    assertEquals( loader.calls.get(), 1 )
  }

  test( "an IOException raised by an interrupt is rethrown without a reload" ) {
    val interrupted = new FakeCredentials(
      "old",
      failures = -1,
      onCall = () => Thread.currentThread().interrupt()
    )
    val loader      = loading( interrupted, new FakeCredentials( "new", 0 ) )
    val credentials = new ReloadingCredentials( loader )

    try intercept[IOException]( credentials.getRequestMetadata( uri ) )
    finally Thread.interrupted()
    assertEquals( loader.calls.get(), 1 )
  }

  test( "a reload that throws keeps the current delegate and the next failure reloads again" ) {
    val failing     = new FakeCredentials( "old", failures = -1 )
    val loader      = new CountingLoader( Seq( Some( failing ), None, Some( new FakeCredentials( "new", 0 ) ) ) )
    val credentials = new ReloadingCredentials( loader )

    val thrown = intercept[IOException]( credentials.getRequestMetadata( uri ) )
    assertEquals( thrown.getMessage, "load 2 failed" )
    assert( credentials.delegate eq failing, "the delegate changed although the reload failed" )
    assertEquals( bearer( credentials ), "Bearer new" )
    assertEquals( loader.calls.get(), 3 )
  }

  test( "a healthy delegate is never reloaded" ) {
    val loader      = loading( new FakeCredentials( "good", 0 ) )
    val credentials = new ReloadingCredentials( loader )

    ( 1 to 5 ).foreach( _ => assertEquals( bearer( credentials ), "Bearer good" ) )
    assertEquals( loader.calls.get(), 1 )
  }
}
