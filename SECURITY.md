# Security Policy

## Reporting

Please report security issues privately through GitHub security advisories for this repository when available. Avoid opening public issues that include exploitable details, credentials, or private medical data.

## Data Handling

CareCopilot is a reference backend for medical-assistant workflows. Do not use production patient data unless you have completed the required privacy, compliance, access-control, retention, and audit reviews for your deployment environment.

## Secrets

Never commit credentials, database passwords, API keys, model-provider tokens, private certificates, or local environment files. Use environment variables, Kubernetes secrets, or your platform secret manager.
