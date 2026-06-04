# Temporary Credential Models

This document defines the temporary credential models used across the OpenSharing protocol. All asset types that support credential-based access use these shared models.

Only one of `awsTempCredentials`, `azureUserDelegationSas`, `gcpOauthToken`, or `r2Credentials` should be defined in any credential response. The `expirationTime` field is always required. The API client is advised to cache the credential given this expiration time.

## TemporaryCredentials

| Name | Type | Description | Notes |
|---|---|---|---|
| awsTempCredentials | AwsCredentials | Temporary AWS credentials. | [optional] |
| azureUserDelegationSas | AzureUserDelegationSAS | Azure SAS token. | [optional] |
| gcpOauthToken | GcpOauthToken | GCP OAuth token. | [optional] |
| r2Credentials | R2Credentials | Temporary Cloudflare R2 credentials. | [optional] |
| expirationTime | Long | Server time when the credential will expire, in epoch milliseconds. | [required] |

## AwsCredentials

| Name | Type | Description | Notes |
|---|---|---|---|
| accessKeyId | String | The access key ID that identifies the temporary credentials. | [required] |
| secretAccessKey | String | The secret access key that can be used to sign AWS API requests. | [required] |
| sessionToken | String | The token that users must pass to the AWS API to use the temporary credentials. | [required] |

## AzureUserDelegationSAS

| Name | Type | Description | Notes |
|---|---|---|---|
| sasToken | String | Azure SAS token granting read access to the asset's storage location. | [required] |

## GcpOauthToken

| Name | Type | Description | Notes |
|---|---|---|---|
| oauthToken | String | GCP OAuth token granting read access to the asset's storage location. | [required] |

## R2Credentials

| Name | Type | Description | Notes |
|---|---|---|---|
| accessKeyId | String | The access key ID that identifies the temporary credentials. | [required] |
| secretAccessKey | String | The secret access key. | [required] |
| sessionToken | String | The session token. | [required] |
