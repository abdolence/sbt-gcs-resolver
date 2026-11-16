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

class ContentTypesSuite extends munit.FunSuite {

  test( "published Maven and Ivy files get a content type from their extension" ) {
    val expected = Map(
      "org/foo_3/1.0/foo_3-1.0.jar"         -> "application/java-archive",
      "org/foo_3/1.0/foo_3-1.0.pom"         -> "application/xml",
      "org/foo_3/1.0/maven-metadata.xml"    -> "application/xml",
      "org/foo/1.0/ivys/ivy.ivy"            -> "application/xml",
      "org/foo_3/1.0/foo_3-1.0.jar.sha1"    -> "text/plain",
      "org/foo_3/1.0/foo_3-1.0.pom.md5"     -> "text/plain",
      "org/foo_3/1.0/foo_3-1.0.jar.asc"     -> "text/plain",
      "org/foo_3/1.0/foo_3-1.0.JAR"         -> "application/java-archive",
      "org/foo_3/1.0/foo_3-1.0-sources.zip" -> "application/octet-stream",
      "org/foo_3/1.0/NOTICE"                -> "application/octet-stream"
    )
    expected.foreach { case ( path, contentType ) => assertEquals( ContentTypes.forPath( path ), contentType, path ) }
  }
}
