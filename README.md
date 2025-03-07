# PDF Refact POC

## Overview

This repo contains some POC code to redact PII information found within PDF documents.

In order to run the code you will need to configure environment variables to give
access to AWS Textract and AWS Comprehend with an AWS account, you can do this by
setting the following environment variables:

```bash
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
```

Once those environment variables are configured, you can run the example
by running the class `uk.co.mruoc.Main`. Once the code has completed a
redacted copy of the input file found at `input/example.pdf` will be produced
at `redacted/example.pdf`.

## Useful Commands

```gradle
// cleans build directories
// checks dependency versions
// checks for gradle issues
// formats code
// builds code
// runs tests
// checks dependencies for vulnerabilities
./gradlew clean dependencyUpdates criticalLintGradle spotlessApply build
```