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

package software.amazon.lambda.powertools.validation.internal;

import static com.networknt.schema.SpecVersion.VersionFlag.V201909;
import static java.nio.charset.StandardCharsets.UTF_8;
import static software.amazon.lambda.powertools.common.internal.LambdaHandlerProcessor.placedOnRequestHandler;
import static software.amazon.lambda.powertools.utilities.jmespath.Base64Function.decode;
import static software.amazon.lambda.powertools.utilities.jmespath.Base64GZipFunction.decompress;
import static software.amazon.lambda.powertools.validation.ValidationUtils.getJsonSchema;
import static software.amazon.lambda.powertools.validation.ValidationUtils.validate;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.amazonaws.services.lambda.runtime.events.ActiveMQEvent;
import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerRequestEvent;
import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerResponseEvent;
import com.amazonaws.services.lambda.runtime.events.CloudFormationCustomResourceEvent;
import com.amazonaws.services.lambda.runtime.events.CloudWatchLogsEvent;
import com.amazonaws.services.lambda.runtime.events.KafkaEvent;
import com.amazonaws.services.lambda.runtime.events.KinesisAnalyticsFirehoseInputPreprocessingEvent;
import com.amazonaws.services.lambda.runtime.events.KinesisAnalyticsInputPreprocessingResponse;
import com.amazonaws.services.lambda.runtime.events.KinesisAnalyticsStreamsInputPreprocessingEvent;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.lambda.runtime.events.KinesisFirehoseEvent;
import com.amazonaws.services.lambda.runtime.events.RabbitMQEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse;
import com.networknt.schema.JsonSchema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.lambda.powertools.validation.Validation;
import software.amazon.lambda.powertools.validation.ValidationConfig;
import software.amazon.lambda.powertools.validation.ValidationException;

/**
 * Aspect for {@link Validation} annotation. Internal to Powertools, use the annotation itself.
 */
@Aspect
public class ValidationAspect {
    private static final Logger LOG = LoggerFactory.getLogger(ValidationAspect.class);

    @SuppressWarnings({"EmptyMethod"})
    @Pointcut("@annotation(validation)")
    public void callAt(Validation validation) {
    }

    @Around(value = "callAt(validation) && execution(@Validation * *.*(..))", argNames = "pjp,validation")
    public Object around(ProceedingJoinPoint pjp,
                         Validation validation) throws Throwable {
        Object[] proceedArgs = pjp.getArgs();
        boolean validationNeeded = false;

        if (validation.schemaVersion() != V201909) {
            ValidationConfig.get().setSchemaVersion(validation.schemaVersion());
        }

        // we need this result object to be null at this point as validation of API events, if
        // it fails, will catch the ValidationException and generate a 400 API response. This response
        // will be stored in the result object to prevent executing the lambda
        Object validationResult = null;
        boolean failFast = false;

        if (placedOnRequestHandler(pjp)) {
            validationNeeded = true;

            if (!validation.inboundSchema().isEmpty()) {
                JsonSchema inboundJsonSchema = getJsonSchema(validation.inboundSchema(), true);

                Object obj = pjp.getArgs()[0];
                if (validation.envelope() != null && !validation.envelope().isEmpty()) {
                    validate(obj, inboundJsonSchema, validation.envelope());
                } else if (obj instanceof APIGatewayProxyRequestEvent) {
                    APIGatewayProxyRequestEvent event = (APIGatewayProxyRequestEvent) obj;
                    validationResult = validateAPIGatewayProxyBody(event.getBody(), inboundJsonSchema, null, null);
                    failFast = true;
                } else if (obj instanceof APIGatewayV2HTTPEvent) {
                    APIGatewayV2HTTPEvent event = (APIGatewayV2HTTPEvent) obj;
                    validationResult = validateAPIGatewayV2HTTPBody(event.getBody(), inboundJsonSchema, null, null);
                    failFast = true;
                } else if (obj instanceof SNSEvent) {
                    SNSEvent event = (SNSEvent) obj;
                    event.getRecords()
                            .forEach(snsRecord -> validate(snsRecord.getSNS().getMessage(), inboundJsonSchema));
                } else if (obj instanceof SQSEvent) {
                    SQSEvent event = (SQSEvent) obj;
                    validationResult = validateSQSEventMessages(event.getRecords(), inboundJsonSchema);
                } else if (obj instanceof ScheduledEvent) {
                    ScheduledEvent event = (ScheduledEvent) obj;
                    validate(event.getDetail(), inboundJsonSchema);
                } else if (obj instanceof ApplicationLoadBalancerRequestEvent) {
                    ApplicationLoadBalancerRequestEvent event = (ApplicationLoadBalancerRequestEvent) obj;
                    validate(event.getBody(), inboundJsonSchema);
                } else if (obj instanceof CloudWatchLogsEvent) {
                    CloudWatchLogsEvent event = (CloudWatchLogsEvent) obj;
                    validate(decompress(decode(event.getAwsLogs().getData().getBytes(UTF_8))), inboundJsonSchema);
                } else if (obj instanceof CloudFormationCustomResourceEvent) {
                    CloudFormationCustomResourceEvent event = (CloudFormationCustomResourceEvent) obj;
                    validate(event.getResourceProperties(), inboundJsonSchema);
                } else if (obj instanceof KinesisEvent) {
                    KinesisEvent event = (KinesisEvent) obj;
                    validationResult = validateKinesisEventRecords(event.getRecords(), inboundJsonSchema);
                } else if (obj instanceof KinesisFirehoseEvent) {
                    KinesisFirehoseEvent event = (KinesisFirehoseEvent) obj;
                    event.getRecords()
                            .forEach(eventRecord -> validate(decode(eventRecord.getData()), inboundJsonSchema));
                } else if (obj instanceof KafkaEvent) {
                    KafkaEvent event = (KafkaEvent) obj;
                    event.getRecords().forEach((s, records) -> records.forEach(
                            eventRecord -> validate(decode(eventRecord.getValue()), inboundJsonSchema)));
                } else if (obj instanceof ActiveMQEvent) {
                    ActiveMQEvent event = (ActiveMQEvent) obj;
                    event.getMessages().forEach(message -> validate(decode(message.getData()), inboundJsonSchema));
                } else if (obj instanceof RabbitMQEvent) {
                    RabbitMQEvent event = (RabbitMQEvent) obj;
                    event.getRmqMessagesByQueue().forEach((s, records) -> records.forEach(
                            message -> validate(decode(message.getData()), inboundJsonSchema)));
                } else if (obj instanceof KinesisAnalyticsFirehoseInputPreprocessingEvent) {
                    KinesisAnalyticsFirehoseInputPreprocessingEvent event =
                            (KinesisAnalyticsFirehoseInputPreprocessingEvent) obj;
                    event.getRecords()
                            .forEach(eventRecord -> validate(decode(eventRecord.getData()), inboundJsonSchema));
                } else if (obj instanceof KinesisAnalyticsStreamsInputPreprocessingEvent) {
                    KinesisAnalyticsStreamsInputPreprocessingEvent event =
                            (KinesisAnalyticsStreamsInputPreprocessingEvent) obj;
                    event.getRecords()
                            .forEach(eventRecord -> validate(decode(eventRecord.getData()), inboundJsonSchema));
                } else {
                    LOG.warn("Unhandled event type {}, please use the 'envelope' parameter to specify what to validate",
                            obj.getClass().getName());
                }
            }
        }

        Object result;

        // don't execute the lambda if result was set by previous validation step and should fail fast
        // in that case result should already hold a response with validation information
        if (failFast && validationResult != null) {
            LOG.error("Incoming API event's body failed inbound schema validation.");
            return validationResult;
        } else {
            result = pjp.proceed(proceedArgs);

            if (validationResult != null && result != null) {
                // in the case of batches (SQS, Kinesis), we copy the batch item failures to the result
                if (result instanceof SQSBatchResponse && validationResult instanceof SQSBatchResponse) {
                    SQSBatchResponse validationResponse = (SQSBatchResponse) validationResult;
                    SQSBatchResponse response = (SQSBatchResponse) result;
                    if (response.getBatchItemFailures() == null) {
                        response.setBatchItemFailures(validationResponse.getBatchItemFailures());
                    } else {
                        response.getBatchItemFailures().addAll(validationResponse.getBatchItemFailures());
                    }
                } else if (result instanceof StreamsEventResponse && validationResult instanceof StreamsEventResponse) {
                    StreamsEventResponse validationResponse = (StreamsEventResponse) validationResult;
                    StreamsEventResponse response = (StreamsEventResponse) result;
                    if (response.getBatchItemFailures() == null) {
                        response.setBatchItemFailures(validationResponse.getBatchItemFailures());
                    } else {
                        response.getBatchItemFailures().addAll(validationResponse.getBatchItemFailures());
                    }
                }
            }

            if (result != null && validationNeeded && !validation.outboundSchema().isEmpty()) {
                JsonSchema outboundJsonSchema = getJsonSchema(validation.outboundSchema(), true);

                Object overridenResponse = null;
                // The normal behavior of @Validation is to throw an exception if response's validation fails.
                // but in the case of APIGatewayProxyResponseEvent and APIGatewayV2HTTPResponse we want to return
                // a 400 response with the validation errors instead of throwing an exception.
                if (result instanceof APIGatewayProxyResponseEvent) {
                    APIGatewayProxyResponseEvent response = (APIGatewayProxyResponseEvent) result;
                    overridenResponse =
                            validateAPIGatewayProxyBody(response.getBody(), outboundJsonSchema, response.getHeaders(),
                                    response.getMultiValueHeaders());
                } else if (result instanceof APIGatewayV2HTTPResponse) {
                    APIGatewayV2HTTPResponse response = (APIGatewayV2HTTPResponse) result;
                    overridenResponse =
                            validateAPIGatewayV2HTTPBody(response.getBody(), outboundJsonSchema, response.getHeaders(),
                                    response.getMultiValueHeaders());
                    // all type of below responses will throw an exception if validation fails
                } else if (result instanceof APIGatewayV2WebSocketResponse) {
                    APIGatewayV2WebSocketResponse response = (APIGatewayV2WebSocketResponse) result;
                    validate(response.getBody(), outboundJsonSchema);
                } else if (result instanceof ApplicationLoadBalancerResponseEvent) {
                    ApplicationLoadBalancerResponseEvent response = (ApplicationLoadBalancerResponseEvent) result;
                    validate(response.getBody(), outboundJsonSchema);
                } else if (result instanceof KinesisAnalyticsInputPreprocessingResponse) {
                    KinesisAnalyticsInputPreprocessingResponse response =
                            (KinesisAnalyticsInputPreprocessingResponse) result;
                    response.getRecords().forEach(record -> validate(decode(record.getData()), outboundJsonSchema));
                } else {
                    LOG.warn(
                            "Unhandled response type {}, please use the 'envelope' parameter to specify what to validate",
                            result.getClass().getName());
                }

                if (overridenResponse != null) {
                    result = overridenResponse;
                    LOG.error("API response failed outbound schema validation.");
                }
            }
        }

        return result;
    }

    /**
     * Validate each Kinesis record body. If an error occurs, do not fail the whole batch but only add invalid items in BatchItemFailure.
     * Note that the valid records will be decoded twice (during validation and within the handler by the user), which will slightly reduce performance.
     * @param records Kinesis records
     * @param inboundJsonSchema validation schema
     * @return the stream response with items in failure
     */
    private StreamsEventResponse validateKinesisEventRecords(List<KinesisEvent.KinesisEventRecord> records,
                                                             JsonSchema inboundJsonSchema) {
        StreamsEventResponse response = StreamsEventResponse.builder().withBatchItemFailures(new ArrayList<>()).build();

        ListIterator<KinesisEvent.KinesisEventRecord> listIterator = records.listIterator(); // using iterator to remove while browsing
        while (listIterator.hasNext()) {
            KinesisEvent.KinesisEventRecord eventRecord = listIterator.next();
            try {
                validate(decode(eventRecord.getKinesis().getData()), inboundJsonSchema);
            } catch (ValidationException e) {
                LOG.error("Validation error on message {}: {}", eventRecord.getKinesis().getSequenceNumber(),
                        e.getMessage());
                listIterator.remove();
                response.getBatchItemFailures().add(StreamsEventResponse.BatchItemFailure.builder()
                        .withItemIdentifier(eventRecord.getKinesis().getSequenceNumber()).build());
            }
        }
        return response;
    }

    /**
     * Validate each SQS message body. If an error occurs, do not fail the whole batch but only add invalid items in BatchItemFailure.
     *
     * @param messages          SQS messages
     * @param inboundJsonSchema validation schema
     * @return the SQS batch response
     */
    private SQSBatchResponse validateSQSEventMessages(List<SQSEvent.SQSMessage> messages,
                                                      JsonSchema inboundJsonSchema) {
        SQSBatchResponse response = SQSBatchResponse.builder().withBatchItemFailures(new ArrayList<>()).build();
        ListIterator<SQSEvent.SQSMessage> listIterator = messages.listIterator(); // using iterator to remove while browsing
        while (listIterator.hasNext()) {
            SQSEvent.SQSMessage message = listIterator.next();
            try {
                validate(message.getBody(), inboundJsonSchema);
            } catch (ValidationException e) {
                LOG.error("Validation error on message {}: {}", message.getMessageId(), e.getMessage());
                listIterator.remove();
                response.getBatchItemFailures()
                        .add(SQSBatchResponse.BatchItemFailure.builder().withItemIdentifier(message.getMessageId())
                                .build());
            }
        }
        return response;
    }

    /**
     * Validates the given body against the provided JsonSchema. If validation fails the ValidationException
     * will be catched and transformed to a 400, bad request, API response
     *
     * @param body       body of the event to validate
     * @param jsonSchema validation schema
     * @return null if validation passed, or a 400 response object otherwise
     */
    private APIGatewayProxyResponseEvent validateAPIGatewayProxyBody(final String body, final JsonSchema jsonSchema,
                                                                     final Map<String, String> headers,
                                                                     Map<String, List<String>> multivalueHeaders) {
        APIGatewayProxyResponseEvent result = null;
        try {
            validate(body, jsonSchema);
        } catch (ValidationException e) {
            LOG.error("There were validation errors: {}", e.getMessage());
            result = new APIGatewayProxyResponseEvent();
            result.setBody(e.getMessage());
            result.setHeaders(headers == null ? Collections.emptyMap() : headers);
            result.setMultiValueHeaders(multivalueHeaders == null ? Collections.emptyMap() : multivalueHeaders);
            result.setStatusCode(400);
            result.setIsBase64Encoded(false);
        }
        return result;
    }

    /**
     * Validates the given body against the provided JsonSchema. If validation fails the ValidationException
     * will be catched and transformed to a 400, bad request, API response
     *
     * @param body       body of the event to validate
     * @param jsonSchema validation schema
     * @return null if validation passed, or a 400 response object otherwise
     */
    private APIGatewayV2HTTPResponse validateAPIGatewayV2HTTPBody(final String body, final JsonSchema jsonSchema,
                                                                  final Map<String, String> headers,
                                                                  Map<String, List<String>> multivalueHeaders) {
        APIGatewayV2HTTPResponse result = null;
        try {
            validate(body, jsonSchema);
        } catch (ValidationException e) {
            LOG.error("There were validation errors: {}", e.getMessage());
            result = new APIGatewayV2HTTPResponse();
            result.setBody(e.getMessage());
            result.setHeaders(headers == null ? Collections.emptyMap() : headers);
            result.setMultiValueHeaders(multivalueHeaders == null ? Collections.emptyMap() : multivalueHeaders);
            result.setStatusCode(400);
            result.setIsBase64Encoded(false);
        }
        return result;
    }
}
