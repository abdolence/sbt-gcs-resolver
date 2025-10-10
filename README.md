# SBT plugin for Google Cloud Storage (GCS) and Google Artifact Registry

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

Add this to your `project/plugins.sbt`:

```scala
addSbtPlugin("org.latestbit" % "sbt-gcs-plugin" % "1.14.0")
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
publishTo := Some("My private artifacts" at "artifactregistry://<your-artifact-registry-url>")
```

### Google Artifact Registry resolving

```scala
resolvers += "My private artifacts" at "artifactregistry://<your-artifact-registry-url>"
```

## Configuration

### Google Cloud credentials file configuration

Plugin tries to load Google Account in the following order:
- Specified settings in sbt build
```
googleCredentialsFile := Some(new File("<your-account-file>"))
``` 
- Looking for `gcs-resolver-google-account.json` in `<user-home>/.sbt` directory
- Looking for the Access Token from the environment variable: ``GOOGLE_OAUTH_ACCESS_TOKEN``
- Default application credentials (gcloud settings) or environment variable:
```bash
export GOOGLE_APPLICATION_CREDENTIALS=<your-account-file>
```

---------------------------------------------------------------------------------------------
If you see some errors such as `The Application Default Credentials are not available.` 
when you start sbt, that means there is no default credentials configured on your machine.
You can use
`gcloud auth application-default login` to fix it or specify path to your account file 
using environment variable `GOOGLE_APPLICATION_CREDENTIALS`.

Follow for details https://developers.google.com/accounts/docs/application-default-credentials.

---------------------------------------------------------------------------------------------

### Workload Identity Federation
The plugin supports Workload Identity Federation since it uses the official Google client for Java. 
The example how to use with GitHub Actions is:
```
    - name: Authenticate Google Cloud
    id: auth
    uses: google-github-actions/auth@v1
    with:
      workload_identity_provider: 'projects/${{ env.GCP_PROJECT_ID }}/locations/global/workloadIdentityPools/${{ env.GCP_IDENTITY_POOL }}/providers/${{ env.GCP_IDENTITY_POOL_PROVIDER }}'
      service_account: '${{ env.GCP_SA_NAME }}@${{ env.GCP_PROJECT }}.iam.gserviceaccount.com'
      access_token_lifetime: '240s'
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
