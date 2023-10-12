/*
 * Copyright 2023 Amazon.com, Inc. or its affiliates.
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package cdk;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awscdk.BundlingOptions;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.Tracing;
import software.amazon.awscdk.services.s3.assets.AssetOptions;
import software.constructs.Construct;

/**
 * Defines a stack that consists of a single Java Lambda function and an API Gateway
 */
public class CdkStack extends Stack {
    private static final String SHELL_COMMAND = "/bin/sh";
    private static final String MAVEN_PACKAGE = "mvn package";
    private static final String COPY_OUTPUT = "cp /asset-input/target/helloworld-lambda.jar /asset-output/";

    public CdkStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public CdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Function helloWorldFunction = createHelloWorldFunction();
        Function helloWorldStreamFunction = createHelloWorldStreamFunction();
        RestApi restApi = createHelloWorldApi();

        restApi.getRoot().resourceForPath("/hello")
                .addMethod("GET", LambdaIntegration.Builder.create(helloWorldFunction)
                        .build());

        restApi.getRoot().resourceForPath("/hellostream")
                .addMethod("GET", LambdaIntegration.Builder.create(helloWorldStreamFunction)
                        .build());

        outputApiUrl(restApi);
    }

    private static List<String> createFunctionPackageInstructions() {
        // CDK will use this command to package your Java Lambda
        return Arrays.asList(
                SHELL_COMMAND,
                "-c",
                MAVEN_PACKAGE + " && " +
                        COPY_OUTPUT
        );
    }

    /**
     * Adds API URL to the outputs
     *
     * @param restApi
     */
    private void outputApiUrl(RestApi restApi) {
        CfnOutput.Builder.create(this, "HelloWorldApiUrl")
                .description("API Gateway endpoint URL for Prod stage for Hello World function")
                .value(restApi.getUrl() + "hello").build();
    }

    // Method to create the Lambda function
    private Function createHelloWorldFunction() {
        List<String> functionPackageInstructions = createFunctionPackageInstructions();

        Map<String, String> environment = new HashMap<>();
        environment.put("POWERTOOLS_LOG_LEVEL", "INFO");
        environment.put("POWERTOOLS_LOGGER_SAMPLE_RATE", "0.1");
        environment.put("POWERTOOLS_LOGGER_LOG_EVENT", "true");
        environment.put("POWERTOOLS_METRICS_NAMESPACE", "Coreutilities");

        return Function.Builder.create(this, "HelloWorldFunction")
                .runtime(Runtime.JAVA_11)
                .memorySize(512)
                .timeout(Duration.seconds(20))
                .tracing(Tracing.ACTIVE)
                .code(Code.fromAsset("../app/", AssetOptions.builder()
                        .bundling(BundlingOptions.builder()
                                .image(Runtime.JAVA_11.getBundlingImage())
                                .command(functionPackageInstructions)
                                .build())
                        .build()))
                .handler("helloworld.App")
                .environment(environment)
                .build();
    }

    private Function createHelloWorldStreamFunction() {
        List<String> functionPackageInstructions = createFunctionPackageInstructions();

        Map<String, String> environment = new HashMap<>();
        environment.put("POWERTOOLS_LOG_LEVEL", "INFO");
        environment.put("POWERTOOLS_LOGGER_SAMPLE_RATE", "0.7");
        environment.put("POWERTOOLS_LOGGER_LOG_EVENT", "true");
        environment.put("POWERTOOLS_METRICS_NAMESPACE", "Coreutilities");
        environment.put("POWERTOOLS_SERVICE_NAME", "hello");

        return Function.Builder.create(this, "HelloWorldStreamFunction")
                .runtime(Runtime.JAVA_11)
                .memorySize(512)
                .timeout(Duration.seconds(20))
                .tracing(Tracing.ACTIVE)
                .code(Code.fromAsset("../app/", AssetOptions.builder()
                        .bundling(BundlingOptions.builder()
                                .image(Runtime.JAVA_11.getBundlingImage())
                                .command(functionPackageInstructions)
                                .build())
                        .build()))
                .handler("helloworld.AppStream")
                .environment(environment)
                .build();
    }

    // Method to create the REST API
    private RestApi createHelloWorldApi() {
        return RestApi.Builder.create(this, "HelloWorldApi")
                .description("API Gateway endpoint URL for Prod stage for Hello World function")
                .build();
    }
}
