# SBT plugin for Google Cloud Storage (GCS) artifact resolving/publishing
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.latestbit/sbt-gcs-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.latestbit/sbt-gcs-plugin/)

Features:
- Simple to use
- Coursier support (from sbt 1.3+)
- Ability to configure Google Credentails using sbt settings

## SBT versions support
sbt v1.4+ (I haven't tested this with previous versions)

## Usage

### Install the plugin

Put this inside your `project/plugins.sbt`:

```scala
addSbtPlugin("org.latestbit" % "sbt-gcs-plugin" % "1.3.0")
```

### Publishing artifacts to GCS

```scala
publishTo := Some("My GCS artifacts" at "gs://<your-bucket-name>")
```

### Resolving artifacts from GCS

```scala
resolvers += "My GCS artifacts" at "gs://<your-bucket-name>"
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

### Configure publish access level:
```
gcsPublishFilePolicy := GcsPublishFilePolicy.InheritedFromBucket // Default

// If you really need to make some of the files available for everyone
gcsPublishFilePolicy := GcsPublishFilePolicy.PublicAccess 
```

### Licence
Apache Software License (ASL)

### Author
Abdulla Abdurakhmanov
