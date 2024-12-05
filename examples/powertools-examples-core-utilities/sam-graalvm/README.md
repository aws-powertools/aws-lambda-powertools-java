#  Powertools for AWS Lambda (Java) - Core Utilities Example with SAM on GraalVM

This project demonstrates the Lambda for Powertools Java module deployed using [Serverless Application Model](https://aws.amazon.com/serverless/sam/) running as a GraalVM native image.

For general information on the deployed example itself, you can refer to the parent [README](../README.md)

## Configuration
 - SAM uses [template.yaml](template.yaml) to define the application's AWS resources.
This file defines the Lambda function to be deployed as well as API Gateway for it.

 - Set the environment to use GraalVM

```shell 
      export JAVA_HOME=<path to GraalVM>
````

## Build the sample application

- Build the Docker image that will be used as the environment for SAM build:
```shell
       docker build --platform linux/amd64 . -t powertools-examples-core-sam-graalvm
```

- Build the SAM project using the docker image
```shell
    sam build --use-container --build-image powertools-examples-core-sam-graalvm

```
#### [Optional] Building with -SNAPSHOT versions of PowerTools
- If you are testing the example with a -SNAPSHOT version of PowerTools, the maven build inside the docker image will fail. This is because the -SNAPSHOT version of the PowerTools library that you are working on is still not available in maven central/snapshot repository.
To get around this, follow these steps: 
  - Create the native image using the `docker` command below on your development machine. The native image is created in the `target` directory. 
    - ```docker run --platform linux/amd64  -it -v `pwd`:`pwd` -w `pwd` -v ~/.m2:/root/.m2 powertools-examples-core-sam-graalvm mvn clean -Pnative-image package -DskipTests```
  - Edit the [`Makefile`](Makefile) remove this line
    - ```mvn clean package -P native-image```
  - Build the SAM project using the docker image
    - ```sam build --use-container --build-image powertools-examples-core-sam-graalvm```

## Deploy the sample application
- SAM deploy

       sam deploy

To deploy the example, check out the instructions for getting
started with SAM in [the examples directory](../../README.md)

## Additional notes

You can watch the trace information or log information using the SAM CLI:
```bash
# Tail the logs
sam logs --tail $MY_STACK

# Tail the traces
sam traces --tail
```