# SBT plugin for Google Cloud Storage (GCS) and Google Artifact Registry
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.latestbit/sbt-gcs-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.latestbit/sbt-gcs-plugin/)

Features:
- Support for raw Google Cloud Storage buckets (`gs://`)
- Support Google Artifact Registry (`artifactregistry://`)
- Simple to use
- Coursier support (sbt 1.3+)
- Ability to configure Google Credentials using sbt settings

## SBT versions support
sbt v1.4+ (I haven't tested this with previous versions)

## Usage

### Install the plugin

Put this inside your `project/plugins.sbt`:

```scala
addSbtPlugin("org.latestbit" % "sbt-gcs-plugin" % "1.3.0")
```

### GCS publishing

```scala
publishTo := Some("My GCS artifacts" at "gs://<your-bucket-name>")
```

### GCS resolving

```scala
resolvers += "My GCS artifacts" at "gs://<your-bucket-name>"
```

### Google Artifact Registry publishing

```scala
publishTo := Some("My private artifacts" at "artifactregistry://<your-gcs-url>")
```

### Google Artifact Registry resolving

```scala
resolvers += "My private artifacts" at "artifactregistry://<your-gcs-url>"
```

## Configuration

### Google Cloud credentials file configuration

```
gcsCredentialsFile := Some(new File("<your-account-file>"))
```
By default, it uses application default, which isn't recommended by Google.
Another way is doing this, is using environment variable:

```bash
export GOOGLE_APPLICATION_CREDENTIALS=<your-account-file>
```

### Configure publish access level (GCS only):
```
gcsPublishFilePolicy := GcsPublishFilePolicy.InheritedFromBucket // Default

// If you really need to make some of the files available for everyone
gcsPublishFilePolicy := GcsPublishFilePolicy.PublicAccess 
```
For Google Artifact Registry please use gcloud/GCP console to manage security.

### Licence
Apache Software License (ASL)

### Author
Abdulla Abdurakhmanov
