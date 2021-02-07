package org.latestbit.sbt.gcs

import com.google.cloud.storage.Storage
import sbt.Logger
import sbt.librarymanagement._

class GcsPublisher(gcsStorage: Storage)(implicit logger: Logger)  {

  def forBucket(bucketName: String, publishPolicy: GcsPublishFilePolicy): Resolver = {
    logger.info(s"Creating GCS publisher for '${bucketName}'. Publish policy: ${publishPolicy}")
    val gcsRepositoryResolver = new GcsRepositoryResolver(new GcsRepository(gcsStorage, bucketName, publishPolicy))
    new RawRepository(gcsRepositoryResolver, GcsRepositoryResolver.GcsRepositoryName)
  }

}
