package software.amazon.lambda.powertools.largemessages;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import static software.amazon.lambda.powertools.core.internal.LambdaConstants.AWS_LAMBDA_INITIALIZATION_TYPE;
import static software.amazon.lambda.powertools.core.internal.LambdaConstants.AWS_REGION_ENV;
import static software.amazon.lambda.powertools.core.internal.LambdaConstants.ON_DEMAND;

/**
 * Singleton instance for Large Message Config.
 * <br/>
 * Optional: Use it in your Lambda constructor to pass a custom {@link S3Client} to the {@link software.amazon.lambda.powertools.largemessages.internal.LargeMessageProcessor}
 * <br/>
 * If you don't use this, a default S3Client will be created.
 * <pre>
 * public MyLambdaHandler() {
 *     LargeMessageConfig.builder().withS3Client(S3Client.create()).build();
 * }
 * </pre>
 */
public class LargeMessageConfig {

    private static final LargeMessageConfig INSTANCE = new LargeMessageConfig();
    private S3Client s3Client;

    private LargeMessageConfig() {}

    public static LargeMessageConfig get() {
        return INSTANCE;
    }

    public static LargeMessageConfig init() {
        return INSTANCE;
    }

    public void withS3Client(S3Client s3Client) {
        if (this.s3Client == null) {
            this.s3Client = s3Client;
        }
    }

    // For tests purpose
    void setS3Client(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    // Getter needs to initialize if not done with setter
    public S3Client getS3Client() {
        if (this.s3Client == null) {
            S3ClientBuilder s3ClientBuilder = S3Client.builder()
                    .httpClient(UrlConnectionHttpClient.builder().build())
                    .region(Region.of(System.getenv(AWS_REGION_ENV)));

            // AWS_LAMBDA_INITIALIZATION_TYPE has two values on-demand and snap-start
            // when using snap-start mode, the env var creds provider isn't used and causes a fatal error if set
            // fall back to the default provider chain if the mode is anything other than on-demand.
            String initializationType = System.getenv().get(AWS_LAMBDA_INITIALIZATION_TYPE);
            if (initializationType != null && initializationType.equals(ON_DEMAND)) {
                s3ClientBuilder.credentialsProvider(EnvironmentVariableCredentialsProvider.create());
            }
            this.s3Client = s3ClientBuilder.build();
        }
        return this.s3Client;
    }
}
