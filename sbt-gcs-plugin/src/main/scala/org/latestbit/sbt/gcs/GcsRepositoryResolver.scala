package org.latestbit.sbt.gcs

import org.apache.ivy.plugins.resolver.RepositoryResolver
import sbt.librarymanagement.Resolver

class GcsRepositoryResolver(repository: GcsRepository)  extends RepositoryResolver {
  setName(GcsRepositoryResolver.GcsRepositoryName)
  setRepository(repository)
  setM2compatible(false)
  Resolver.ivyStylePatterns.ivyPatterns.foreach { p ⇒ this.addIvyPattern(p) }
  Resolver.ivyStylePatterns.artifactPatterns.foreach { p ⇒ this.addArtifactPattern(p) }
}

object GcsRepositoryResolver {
  val GcsRepositoryName="GCSRepository"
}