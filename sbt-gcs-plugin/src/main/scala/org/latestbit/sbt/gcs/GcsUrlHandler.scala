package org.latestbit.sbt.gcs

import com.google.cloud.storage.Storage
import sbt.Logger

import java.net.{ URL, URLConnection, URLStreamHandler }

class GcsUrlHandler( gcsStorage: Storage )( implicit logger: Logger ) extends URLStreamHandler {

  override def openConnection( url: URL ): URLConnection = {
    new GcsUrlConnection( gcsStorage, url )
  }
}
