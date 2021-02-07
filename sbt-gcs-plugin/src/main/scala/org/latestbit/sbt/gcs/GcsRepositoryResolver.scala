package org.latestbit.sbt.gcs

import org.apache.ivy.plugins.resolver.RepositoryResolver
import sbt.librarymanagement.Resolver

class GcsRepositoryResolver( repository: GcsRepository ) extends RepositoryResolver {
  setName( GcsRepositoryResolver.GcsRepositoryName )
  setRepository( repository )
  setM2compatible( true )
  addArtifactPattern( Resolver.mavenStyleBasePattern )
}

object GcsRepositoryResolver {
  val GcsRepositoryName = "GCSRepository"
}
