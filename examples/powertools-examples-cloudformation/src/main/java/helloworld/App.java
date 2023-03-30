package helloworld;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.CloudFormationCustomResourceEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.waiters.S3Waiter;
import software.amazon.lambda.powertools.cloudformation.AbstractCustomResourceHandler;
import software.amazon.lambda.powertools.cloudformation.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Handler for requests to Lambda function.
 */

public class App extends AbstractCustomResourceHandler {
    public static final String EXPIRE_OBJECTS_ID = "expire-all-objects-after-x-days";
    private final static Logger log = LogManager.getLogger(App.class);
    private final S3Client s3Client;

    public App() {
        super();
        s3Client = S3Client.builder().httpClientBuilder(ApacheHttpClient.builder()).build();
    }

    @Override
    protected Response create(CloudFormationCustomResourceEvent cloudFormationCustomResourceEvent, Context context) {
        Objects.requireNonNull(cloudFormationCustomResourceEvent, "cloudFormationCustomResourceEvent cannot be null.");
        Objects.requireNonNull(cloudFormationCustomResourceEvent.getResourceProperties().get("BucketName"), "BucketName cannot be null.");
        Objects.requireNonNull(cloudFormationCustomResourceEvent.getResourceProperties().get("RetentionDays"), "RetentionDays cannot be null.");

        log.info(cloudFormationCustomResourceEvent);
        String bucketName = (String) cloudFormationCustomResourceEvent.getResourceProperties().get("BucketName");
        log.info("Bucket Name {}", bucketName);
        int retentionDays = Integer.parseInt(String.valueOf(cloudFormationCustomResourceEvent.getResourceProperties().get("RetentionDays")));
        log.info("Retention Period (Days) {}", retentionDays);

        try {
            createBucketWithRetention(bucketName, retentionDays);
            return Response.success(bucketName);
        } catch (AwsServiceException | SdkClientException e) {
            log.error(e);
            return Response.failed(bucketName);
        }
    }

    @Override
    protected Response update(CloudFormationCustomResourceEvent cloudFormationCustomResourceEvent, Context context) {
        Objects.requireNonNull(cloudFormationCustomResourceEvent, "cloudFormationCustomResourceEvent cannot be null.");
        Objects.requireNonNull(cloudFormationCustomResourceEvent.getResourceProperties().get("BucketName"), "BucketName cannot be null.");
        Objects.requireNonNull(cloudFormationCustomResourceEvent.getResourceProperties().get("RetentionDays"), "RetentionDays cannot be null.");
        log.info(cloudFormationCustomResourceEvent);
        String physicalResourceId = cloudFormationCustomResourceEvent.getPhysicalResourceId();
        log.info("Physical Resource ID {}", physicalResourceId);
        int retentionDays = Integer.parseInt(String.valueOf(cloudFormationCustomResourceEvent.getResourceProperties().get("RetentionDays")));
        log.info("Retention Period (Days) {}", retentionDays);
        String newBucketName = (String) cloudFormationCustomResourceEvent.getResourceProperties().get("BucketName");

        if (!physicalResourceId.equals(newBucketName)) {
            // Custom Resource is configured with a different bucket name
            try {
                createBucketWithRetention(newBucketName, retentionDays);
                return Response.success(newBucketName);
            } catch (AwsServiceException | SdkClientException e) {
                log.error(e);
                return Response.failed(newBucketName);
            }
        } else {
            // Bucket name has not changed - check for changes in Lifecycle configuration
            try {
                updateLifecycleRules(physicalResourceId, retentionDays);
                return Response.success(physicalResourceId);
            } catch (AwsServiceException | SdkClientException e) {
                log.error(e);
                return Response.failed(physicalResourceId);
            }
        }
    }

    @Override
    protected Response delete(CloudFormationCustomResourceEvent cloudFormationCustomResourceEvent, Context context) {
        Objects.requireNonNull(cloudFormationCustomResourceEvent, "cloudFormationCustomResourceEvent cannot be null.");
        Objects.requireNonNull(cloudFormationCustomResourceEvent.getPhysicalResourceId(), "PhysicalResourceId cannot be null.");

        log.info(cloudFormationCustomResourceEvent);
        String bucketName = cloudFormationCustomResourceEvent.getPhysicalResourceId();
        log.info("Bucket Name {}", bucketName);

        if (bucketExists(bucketName)) {
            try {
                s3Client.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build());
                log.info("Bucket Deleted {}", bucketName);
                return Response.success(bucketName);
            } catch (AwsServiceException | SdkClientException e) {
                log.error(e);
                return Response.failed(bucketName);
            }
        } else {
            log.info("Bucket already deleted - no action");
            return Response.success(bucketName);
        }

    }

    private boolean bucketExists(String bucketName) {
        try {
            HeadBucketResponse headBucketResponse = s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            if (headBucketResponse.sdkHttpResponse().isSuccessful()) {
                return true;
            }
        } catch (NoSuchBucketException e) {
            log.info("Bucket does not exist");
            return false;
        }
        return false;
    }

    private void updateLifecycleRules(String physicalResourceId, int retentionDays) {
        GetBucketLifecycleConfigurationResponse bucketLifecycleConfiguration = s3Client.getBucketLifecycleConfiguration(GetBucketLifecycleConfigurationRequest.builder().bucket(physicalResourceId).build());
        log.info(bucketLifecycleConfiguration);

        List<LifecycleRule> oldRules = new ArrayList<>(bucketLifecycleConfiguration.rules());
        Optional<LifecycleRule> oldExpireLifecycleRule = oldRules.stream().filter(lifecycleRule -> lifecycleRule.id().equals(EXPIRE_OBJECTS_ID)).findFirst();
        // Check if the existing configuration is equals to the new configuration
        if (oldExpireLifecycleRule.isPresent()) {
            if (oldExpireLifecycleRule.get().expiration().days() == retentionDays) {
                log.info("Retention period is already set to {}. No further actions", retentionDays);
                return;
            } else {
                oldRules.remove(oldExpireLifecycleRule.get());
            }
        }

        LifecycleRule updatedLifecycleRule = LifecycleRule.builder().id(EXPIRE_OBJECTS_ID).filter(LifecycleRuleFilter.builder().prefix("").build()).status("Enabled").expiration(LifecycleExpiration.builder().days(retentionDays).build()).build();

        List<LifecycleRule> newRules = new ArrayList<>();
        newRules.add(updatedLifecycleRule);
        newRules.addAll(oldRules);

        log.info("Updating Lifecycle configuration");
        s3Client.putBucketLifecycleConfiguration(PutBucketLifecycleConfigurationRequest.builder().bucket(physicalResourceId).lifecycleConfiguration(BucketLifecycleConfiguration.builder().rules(newRules).build()).build());
    }


    private void createBucketWithRetention(String bucketName, int retentionDays) {
        S3Waiter waiter = s3Client.waiter();
        CreateBucketRequest createBucketRequest = CreateBucketRequest.builder().bucket(bucketName).build();
        s3Client.createBucket(createBucketRequest);
        WaiterResponse<HeadBucketResponse> waiterResponse = waiter.waitUntilBucketExists(HeadBucketRequest.builder().bucket(bucketName).build());
        waiterResponse.matched().response().ifPresent(log::info);
        log.info("Bucket Created {}", bucketName);

        LifecycleRule lifecycleRule = LifecycleRule.builder().id(EXPIRE_OBJECTS_ID).filter(LifecycleRuleFilter.builder().prefix("").build()).status("Enabled").expiration(LifecycleExpiration.builder().days(retentionDays).build()).build();
        s3Client.putBucketLifecycleConfiguration(PutBucketLifecycleConfigurationRequest.builder().bucket(bucketName).lifecycleConfiguration(BucketLifecycleConfiguration.builder().rules(lifecycleRule).build()).build());
    }
}