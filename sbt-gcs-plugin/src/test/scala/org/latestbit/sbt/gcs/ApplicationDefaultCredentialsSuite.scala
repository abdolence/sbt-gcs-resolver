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

import com.google.auth.oauth2.{ GoogleCredentials, UserCredentials }

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }

class ApplicationDefaultCredentialsSuite extends munit.FunSuite {

  private val tempDir = FunFixture[Path](
    setup = _ => Files.createTempDirectory( "adc-suite" ),
    teardown = { dir =>
      val files = Files.list( dir )
      try files.toArray.foreach( file => Files.delete( file.asInstanceOf[Path] ) )
      finally files.close()
      Files.delete( dir )
    }
  )

  private def writeUserCredentials( file: Path, refreshToken: String ): Unit = {
    val json =
      s"""{"type": "authorized_user", "client_id": "test-client", "client_secret": "test-secret", "refresh_token": "$refreshToken"}"""
    Files.write( file, json.getBytes( StandardCharsets.UTF_8 ) )
  }

  // The clue never includes the loaded token, in case a real credentials file is picked up.
  private def assertRefreshToken( credentials: GoogleCredentials, expected: String ): Unit =
    credentials match {
      case user: UserCredentials =>
        assert( user.getRefreshToken == expected, s"refresh token is not the one written as '$expected'" )
      case other =>
        fail( s"expected UserCredentials, got ${other.getClass.getName}" )
    }

  tempDir.test( "the file named by GOOGLE_APPLICATION_CREDENTIALS is read again on every load" ) { dir =>
    val file   = dir.resolve( "adc.json" )
    val env    = Map( "GOOGLE_APPLICATION_CREDENTIALS" -> file.toString )
    def load() = GcsUrlHandlerFactory.loadApplicationDefaultCredentials( env.get, dir.resolve( "missing.json" ) )

    writeUserCredentials( file, "first" )
    assertRefreshToken( load(), "first" )
    writeUserCredentials( file, "second" )
    assertRefreshToken( load(), "second" )
  }

  tempDir.test( "the gcloud well-known file is read again on every load" ) { dir =>
    val file   = dir.resolve( "application_default_credentials.json" )
    def load() = GcsUrlHandlerFactory.loadApplicationDefaultCredentials( _ => None, file )

    writeUserCredentials( file, "first" )
    assertRefreshToken( load(), "first" )
    writeUserCredentials( file, "second" )
    assertRefreshToken( load(), "second" )
  }

  tempDir.test( "GOOGLE_CLOUD_QUOTA_PROJECT applies to credentials read from a file" ) { dir =>
    val file = dir.resolve( "application_default_credentials.json" )
    writeUserCredentials( file, "token" )
    val env = Map( "GOOGLE_CLOUD_QUOTA_PROJECT" -> "billing-project" )

    val credentials = GcsUrlHandlerFactory.loadApplicationDefaultCredentials( env.get, file )
    assertEquals( credentials.getQuotaProjectId, "billing-project" )
  }

  tempDir.test( "an empty GOOGLE_APPLICATION_CREDENTIALS falls through to the gcloud well-known file" ) { dir =>
    val file = dir.resolve( "application_default_credentials.json" )
    writeUserCredentials( file, "well-known" )
    val env = Map( "GOOGLE_APPLICATION_CREDENTIALS" -> "" )

    assertRefreshToken( GcsUrlHandlerFactory.loadApplicationDefaultCredentials( env.get, file ), "well-known" )
  }

  tempDir.test( "a GOOGLE_APPLICATION_CREDENTIALS naming a missing file fails instead of falling back" ) { dir =>
    val file = dir.resolve( "application_default_credentials.json" )
    writeUserCredentials( file, "well-known" )
    val env = Map( "GOOGLE_APPLICATION_CREDENTIALS" -> dir.resolve( "missing.json" ).toString )

    intercept[IOException]( GcsUrlHandlerFactory.loadApplicationDefaultCredentials( env.get, file ) )
  }

  tempDir.test( "the well-known path is not resolved when GOOGLE_APPLICATION_CREDENTIALS is set" ) { dir =>
    val file = dir.resolve( "adc.json" )
    writeUserCredentials( file, "from-env" )
    val env = Map( "GOOGLE_APPLICATION_CREDENTIALS" -> file.toString )

    val credentials = GcsUrlHandlerFactory.loadApplicationDefaultCredentials(
      env.get,
      throw new IllegalStateException( "the well-known path was resolved" )
    )
    assertRefreshToken( credentials, "from-env" )
  }
}
