# SBT plugin for Google Cloud Storage (GCS) and Google Artifact Registry

Features:
- Support for raw Google Cloud Storage buckets (`gs://`)
- Support Google Artifact Registry (`artifactregistry://`)
- Simple to use
- Coursier support (sbt 1.3+)
- Ability to configure Google Credentials using sbt settings

## SBT versions support
1. sbt v2.x+
2. sbt v1.x+

## Usage

### Install the plugin

Add this to your `project/plugins.sbt`:

For sbt 1.x:
```scala
addSbtPlugin("org.latestbit" % "sbt-gcs-plugin" % "2.1.0")
```

For sbt 2.x:
```scala
addSbtPlugin("org.latestbit" % "sbt-gcs-plugin" % "2.1.0")
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
Global / googleCredentialsFile := Some(new File("<your-account-file>"))
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

### Short-lived credentials
When the current credentials can no longer get an access token, the plugin loads them again using the same lookup order and retries once.
This happens when Google refuses to issue a token, e.g. for an expired or revoked refresh token, or when refreshing the token after a `401` response fails.
Network errors are reported as they are, without loading the credentials again.

So there is no need to restart sbt after:
- `gcloud auth application-default login`, when your organization limits the lifetime of user credentials;
- replacing the credentials file, for example a rotated service account key.

After changing `googleCredentialsFile` or `googleCredentialsDisable` in the build, you need to restart sbt, `reload` is not enough.

Be aware that `GOOGLE_OAUTH_ACCESS_TOKEN` is a static token and the plugin cannot refresh it.
When it expires, you need to export a new one and restart sbt.

### Custom credentials flows
Custom flows are configured with a credentials file, specified with `googleCredentialsFile` or `GOOGLE_APPLICATION_CREDENTIALS`.
The Google client for Java supports these file types:
- `external_account` for Workload Identity Federation, including executable-sourced credentials (requires `GOOGLE_EXTERNAL_ACCOUNT_ALLOW_EXECUTABLES=1`);
- `impersonated_service_account` to act as a service account using your own credentials.

`gcloud` can generate them, e.g. `gcloud iam workload-identity-pools create-cred-config` or
`gcloud auth application-default login --impersonate-service-account=<service-account-email>`.

Full details available here: https://github.com/googleapis/google-auth-library-java

### No credentials mode
If you want to access publicly available buckets/registries without any authentication you can disable credentials loading using:
```
Global / googleCredentialsDisable := true
```

### Configure publish access level (GCS only):
```
Global / gcsPublishFilePolicy := GcsPublishFilePolicy.InheritedFromBucket // Default

// If you really need to make some of the files available for everyone
Global / gcsPublishFilePolicy := GcsPublishFilePolicy.PublicAccess 
```
For Google Artifact Registry please use Google Cloud IAM to manage security.

### Licence
Apache Software License (ASL)

### Author
Abdulla Abdurakhmanov
