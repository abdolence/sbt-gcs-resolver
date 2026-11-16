package com.google.cloud.storage

/** A `Blob` for fakes of `Storage#get`; its constructor is package-private. */
object ExistingBlob {
  def apply( storage: Storage, id: BlobId ): Blob = new Blob( storage, new BlobInfo.BuilderImpl( id ) )
}
