package org.latestbit.sbt.gcs

sealed trait GcsPublishFilePolicy

object GcsPublishFilePolicy {
  case object InheritedFromBucket extends GcsPublishFilePolicy
  case object PublicAccess        extends GcsPublishFilePolicy
}
