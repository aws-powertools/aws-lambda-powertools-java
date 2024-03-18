---
title: FAQs
description: Frequently Asked Questions
---


## How can I use Powertools for AWS Lambda (Java) with Lombok?

Powertools uses `aspectj-maven-plugin` to compile-time weave (CTW) aspects into the project. In case you want to use `Lombok` or other compile-time preprocessor for your project, it is required to change `aspectj-maven-plugin` configuration to enable in-place weaving feature. Otherwise the plugin will ignore changes introduced by `Lombok` and will use `.java` files as a source. 

To enable in-place weaving feature you need to use following `aspectj-maven-plugin` configuration:

```xml hl_lines="2-6"
<configuration>
    <forceAjcCompile>true</forceAjcCompile> 
    <sources/>
    <weaveDirectories>
        <weaveDirectory>${project.build.directory}/classes</weaveDirectory>
    </weaveDirectories>
    ...
    <aspectLibraries>
        <aspectLibrary>
            <groupId>software.amazon.lambda</groupId>
            <artifactId>powertools-logging</artifactId>
        </aspectLibrary>
    </aspectLibraries>
</configuration>
```

## How can I use Powertools for AWS Lambda (Java) with Kotlin projects?

Powertools uses `aspectj-maven-plugin` to compile-time weave (CTW) aspects into the project. When using it with Kotlin projects, it is required to `forceAjcCompile`. 
No explicit configuration should be required for gradle projects. 

To enable `forceAjcCompile` you need to use following `aspectj-maven-plugin` configuration:

```xml hl_lines="2"
<configuration>
    <forceAjcCompile>true</forceAjcCompile> 
    ...
    <aspectLibraries>
        <aspectLibrary>
            <groupId>software.amazon.lambda</groupId>
            <artifactId>powertools-logging</artifactId>
        </aspectLibrary>
    </aspectLibraries>
</configuration>
```

## How can I use Powertools for AWS Lambda (Java) with the AWS CRT HTTP Client?

Powertools uses the `url-connection-client` as the default HTTP client. The `url-connection-client` is a lightweight HTTP client, which keeps the impact on Lambda cold starts to a minimum. 
With the [announcement](https://aws.amazon.com/blogs/developer/announcing-availability-of-the-aws-crt-http-client-in-the-aws-sdk-for-java-2-x/) of the `aws-crt-client` a new HTTP client has been released, which offers faster SDK startup time and smaller memory footprint. 

Unfortunately, replacing the `url-connection-client` dependency with the `aws-crt-client` will not immediately improve the lambda cold start performance and memory footprint, 
as the default version of the dependency contains native system libraries for all supported runtimes and architectures (Linux, MacOS, Windows, AMD64, ARM64, etc).  This makes the CRT client portable, without the user having to consider _where_ their code will run, but comes at the cost of JAR size.

### Configuring dependencies

Using the `aws-crt-client` in your project requires the exclusion of the `url-connection-client` transitive dependency from the powertools dependency. 

```xml 
<dependency>
    <groupId>software.amazon.lambda</groupId>
    <artifactId>powertools-parameters</artifactId>
    <version>1.18.0</version>
    <exclusions>
        <exclusion>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>url-connection-client</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```
Next, add the `aws-crt-client` and exclude the "generic" `aws-crt` dependency (contains all runtime libraries). 
Instead, set a specific classifier of the `aws-crt` to use the one for your target runtime: either `linux-x86_64` for a Lambda configured for x86 or `linux-aarch_64` for Lambda using arm64. 

!!! note "You will need to add a separate maven profile to build and debug locally when your development environment does not share the target architecture you are using in Lambda."
By specifying the specific target runtime, we prevent other target runtimes from being included in the jar file, resulting in a smaller Lambda package and improved cold start times.

```xml

<dependencies>
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>aws-crt-client</artifactId>
        <version>2.23.21</version>
        <exclusions>
            <exclusion>
                <groupId>software.amazon.awssdk.crt</groupId>
                <artifactId>aws-crt</artifactId>
            </exclusion>
        </exclusions>
    </dependency>

    <dependency>
        <groupId>software.amazon.awssdk.crt</groupId>
        <artifactId>aws-crt</artifactId>
        <version>0.29.9</version>
        <classifier>linux-x86_64</classifier>
    </dependency>
</dependencies>
```

### Explicitly set the AWS CRT HTTP Client
After configuring the dependencies, it's required to explicitly specify the AWS SDK HTTP client. 
Depending on the Powertools module, there is a different way to configure the SDK client.

The following example shows how to use the Lambda Powertools Parameters module while leveraging the AWS CRT Client.   

    ```java hl_lines="11-16 19-20 22"
    import static software.amazon.lambda.powertools.parameters.transform.Transformer.base64;
    
    import com.amazonaws.services.lambda.runtime.Context;
    import com.amazonaws.services.lambda.runtime.RequestHandler;
    import software.amazon.awssdk.services.ssm.SsmClient;
    import software.amazon.awssdk.http.crt.AwsCrtHttpClient;
    import software.amazon.lambda.powertools.parameters.ssm.SSMProvider;

    public class RequestHandlerWithParams implements RequestHandler<String, String> {
    
        // Get an instance of the SSMProvider with a custom HTTP client (aws crt).
        SSMProvider ssmProvider = SSMProvider
                .builder()
                .withClient(
                        SsmClient.builder()
                                .httpClient(AwsCrtHttpClient.builder().build())
                                .build()
                )
                .build();
    
        public String handleRequest(String input, Context context) {
            // Retrieve a single param
            String value = ssmProvider
                    .get("/my/secret");
                    // We might instead want to retrieve multiple parameters at once, returning a Map of key/value pairs
                    // .getMultiple("/my/secret/path");

            // Return the result
            return value;
        }
    }
    ```
It has been considered to make the `aws-crt-client` the default http client in Lambda Powertools for Java, as mentioned in [Move SDK http client to CRT](https://github.com/aws-powertools/powertools-lambda-java/issues/1092), 
but due to the impact on the developer experience it was decided to stick with the `url-connection-client`. 