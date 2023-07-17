# Cloudformation Custom Resource Example

This project contains an example of Lambda function using the CloudFormation module of Lambda Powertools for Java. For more information on this module, please refer to the [documentation](https://awslabs.github.io/aws-lambda-powertools-java/utilities/custom_resources/).

## Deploy the sample application

This sample can be used either with the Serverless Application Model (SAM) or with CDK.

### Deploy with SAM CLI
To use the SAM CLI, you need the following tools.

* SAM CLI - [Install the SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html)
* Java11 - [Install the Java 11](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/downloads-list.html)
* Maven - [Install Maven](https://maven.apache.org/install.html)
* Docker - [Install Docker community edition](https://hub.docker.com/search/?type=edition&offering=community)

To build and deploy this application for the first time, run the following in your shell:

```bash
cd infra/sam
sam build
sam deploy --guided
```

### Deploy with CDK
To use CDK you need the following tools.

* CDK - [Install CDK](https://docs.aws.amazon.com/cdk/v2/guide/getting_started.html)
* Java11 - [Install the Java 11](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/downloads-list.html)
* Maven - [Install Maven](https://maven.apache.org/install.html)
* Docker - [Install Docker community edition](https://hub.docker.com/search/?type=edition&offering=community)

To build and deploy this application for the first time, run the following in your shell:

```bash
cd infra/cdk
mvn package
cdk synth
cdk deploy
```