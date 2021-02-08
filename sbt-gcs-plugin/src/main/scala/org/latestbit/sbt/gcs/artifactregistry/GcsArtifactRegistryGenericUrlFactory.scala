package org.latestbit.sbt.gcs.artifactregistry

import com.google.api.client.http.GenericUrl

import java.net.URL

object GcsArtifactRegistryGenericUrlFactory {

  def createFromUrl( srcUrl: URL ): GenericUrl = {
    val genericUrl = new GenericUrl()
    genericUrl.setScheme( "https" )
    genericUrl.setHost( srcUrl.getHost )
    genericUrl.appendRawPath( srcUrl.getPath )
    genericUrl
  }
}
