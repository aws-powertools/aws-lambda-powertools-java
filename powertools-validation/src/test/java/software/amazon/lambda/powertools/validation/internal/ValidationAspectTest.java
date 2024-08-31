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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.amazonaws.services.lambda.runtime.events.ActiveMQEvent;
import com.amazonaws.services.lambda.runtime.events.ApplicationLoadBalancerRequestEvent;
import com.amazonaws.services.lambda.runtime.events.CloudFormationCustomResourceEvent;
import com.amazonaws.services.lambda.runtime.events.CloudWatchLogsEvent;
import com.amazonaws.services.lambda.runtime.events.KafkaEvent;
import com.amazonaws.services.lambda.runtime.events.KinesisAnalyticsFirehoseInputPreprocessingEvent;
import com.amazonaws.services.lambda.runtime.events.KinesisAnalyticsStreamsInputPreprocessingEvent;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.lambda.runtime.events.KinesisFirehoseEvent;
import com.amazonaws.services.lambda.runtime.events.RabbitMQEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse;
import com.amazonaws.services.lambda.runtime.tests.annotations.Event;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.lambda.powertools.validation.Validation;
import software.amazon.lambda.powertools.validation.ValidationConfig;
import software.amazon.lambda.powertools.validation.ValidationException;
import software.amazon.lambda.powertools.validation.handlers.GenericSchemaV7APIGatewayProxyRequestEventHandler;
import software.amazon.lambda.powertools.validation.handlers.GenericSchemaV7StringHandler;
import software.amazon.lambda.powertools.validation.handlers.KinesisHandlerWithError;
import software.amazon.lambda.powertools.validation.handlers.SQSHandlerWithError;
import software.amazon.lambda.powertools.validation.handlers.SQSWithCustomEnvelopeHandler;
import software.amazon.lambda.powertools.validation.handlers.SQSWithWrongEnvelopeHandler;
import software.amazon.lambda.powertools.validation.handlers.StandardKinesisHandler;
import software.amazon.lambda.powertools.validation.handlers.StandardSQSHandler;
import software.amazon.lambda.powertools.validation.handlers.ValidationInboundAPIGatewayV2HTTPEventHandler;
import software.amazon.lambda.powertools.validation.model.MyCustomEvent;


class ValidationAspectTest {

    @Mock
    Validation validation;
    @Mock
    Signature signature;
    @Mock
    private Context context;
    @Mock
    private ProceedingJoinPoint pjp;
    private ValidationAspect validationAspect = new ValidationAspect();

    private static Stream<Arguments> provideEventAndEventType() {
        return Stream.of(
                Arguments.of("/sns_event.json", SNSEvent.class),
                Arguments.of("/scheduled_event.json", ScheduledEvent.class),
                Arguments.of("/alb_event.json", ApplicationLoadBalancerRequestEvent.class),
                Arguments.of("/cwl_event.json", CloudWatchLogsEvent.class),
                Arguments.of("/cfcr_event.json", CloudFormationCustomResourceEvent.class),
                Arguments.of("/kf_event.json", KinesisFirehoseEvent.class),
                Arguments.of("/kafka_event.json", KafkaEvent.class),
                Arguments.of("/amq_event.json", ActiveMQEvent.class),
                Arguments.of("/rabbitmq_event.json", RabbitMQEvent.class),
                Arguments.of("/kafip_event.json", KinesisAnalyticsFirehoseInputPreprocessingEvent.class),
                Arguments.of("/kasip_event.json", KinesisAnalyticsStreamsInputPreprocessingEvent.class),
                Arguments.of("/custom_event.json", MyCustomEvent.class)

        );
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @ParameterizedTest
    @ArgumentsSource(ResponseEventsArgumentsProvider.class)
    void testValidateOutboundJsonSchemaWithExceptions(Object object) throws Throwable {
        when(validation.schemaVersion()).thenReturn(SpecVersion.VersionFlag.V7);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getSignature().getDeclaringType()).thenReturn(RequestHandler.class);
        Object[] args = {new Object(), context};
        when(pjp.getArgs()).thenReturn(args);
        when(pjp.proceed(args)).thenReturn(object);
        when(validation.inboundSchema()).thenReturn("");
        when(validation.outboundSchema()).thenReturn("classpath:/schema_v7.json");

        assertThatExceptionOfType(ValidationException.class).isThrownBy(() ->
        {
            validationAspect.around(pjp, validation);
        });
    }
    
    @ParameterizedTest
    @ArgumentsSource(HandledResponseEventsArgumentsProvider.class)
    void testValidateOutboundJsonSchemaWithHandledExceptions(Object object) throws Throwable {
        when(validation.schemaVersion()).thenReturn(SpecVersion.VersionFlag.V7);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getSignature().getDeclaringType()).thenReturn(RequestHandler.class);
        Object[] args = {new Object(), context};
        when(pjp.getArgs()).thenReturn(args);
        when(pjp.proceed(args)).thenReturn(object);
        when(validation.inboundSchema()).thenReturn("");
        when(validation.outboundSchema()).thenReturn("classpath:/schema_v7.json");

        Object response = validationAspect.around(pjp, validation);
        assertThat(response).isInstanceOfAny(APIGatewayProxyResponseEvent.class, APIGatewayV2HTTPResponse.class);

        List<String> headerValues = new ArrayList<>();
        headerValues.add("value1");
        headerValues.add("value2");
        headerValues.add("value3");

        if (response instanceof APIGatewayProxyResponseEvent) {
        	assertThat(response).isInstanceOfSatisfying(APIGatewayProxyResponseEvent.class, t -> {
        		assertThat(t.getStatusCode()).isEqualTo(400);
        		assertThat(t.getBody()).isNotBlank();
        		assertThat(t.getIsBase64Encoded()).isFalse();
            assertThat(t.getHeaders()).containsEntry("header1", "value1,value2,value3");
            assertThat(t.getMultiValueHeaders()).containsEntry("header1", headerValues);
        	});        	
        } else if (response instanceof APIGatewayV2HTTPResponse) {
        	assertThat(response).isInstanceOfSatisfying(APIGatewayV2HTTPResponse.class, t -> {
        		assertThat(t.getStatusCode()).isEqualTo(400);
        		assertThat(t.getBody()).isNotBlank();
        		assertThat(t.getIsBase64Encoded()).isFalse();
            assertThat(t.getHeaders()).containsEntry("header1", "value1,value2,value3");
            assertThat(t.getMultiValueHeaders()).containsEntry("header1", headerValues);
        	});        	
        } else {
        	fail();
        }
    }

    @Test
    void testValidateOutboundJsonSchema_APIGWV2() throws Throwable {
        when(validation.schemaVersion()).thenReturn(SpecVersion.VersionFlag.V7);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getSignature().getDeclaringType()).thenReturn(RequestHandler.class);
        Object[] args = {new Object(), context};
        when(pjp.getArgs()).thenReturn(args);
        APIGatewayV2HTTPResponse apiGatewayV2HTTPResponse = new APIGatewayV2HTTPResponse();
        apiGatewayV2HTTPResponse.setBody("{" +
                "    \"id\": 1," +
                "    \"name\": \"Lampshade\"," +
                "    \"price\": 42" +
                "}");
        when(pjp.proceed(args)).thenReturn(apiGatewayV2HTTPResponse);
        when(validation.inboundSchema()).thenReturn("");
        when(validation.outboundSchema()).thenReturn("classpath:/schema_v7.json");

        assertThatNoException().isThrownBy(() -> validationAspect.around(pjp, validation));
    }

    @Test
    void validate_inputOK_schemaInClasspath_shouldValidate() {
    	GenericSchemaV7APIGatewayProxyRequestEventHandler handler = new GenericSchemaV7APIGatewayProxyRequestEventHandler();
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setBody("{" +
                "    \"id\": 1," +
                "    \"name\": \"Lampshade\"," +
                "    \"price\": 42" +
                "}");
        
        
        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);
        assertThat(response.getBody()).isEqualTo("valid-test");
        assertThat(response.getStatusCode()).isEqualTo(200);
        
    }

    @Test
    void validate_inputKO_schemaInClasspath_shouldThrowValidationException() {
    	GenericSchemaV7APIGatewayProxyRequestEventHandler handler = new GenericSchemaV7APIGatewayProxyRequestEventHandler();

        Map<String, String> headers = new HashMap<>();
        headers.put("header1", "value1");
        Map<String, List<String>> headersList = new HashMap<>();
        List<String> headerValues = new ArrayList<>();
        headerValues.add("value1");
        headersList.put("header1", headerValues);

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setBody("{" +
              "    \"id\": 1," +
              "    \"name\": \"Lampshade\"," +
              "    \"price\": -2" +
              "}");
        event.setHeaders(headers);
        event.setMultiValueHeaders(headersList);

        // price is negative
        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);
        assertThat(response.getBody()).isNotBlank();
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getHeaders()).isEmpty();
        assertThat(response.getMultiValueHeaders()).isEmpty();
    }

    @Test
    void validate_inputOK_schemaInString_shouldValidate() {
    	ValidationInboundAPIGatewayV2HTTPEventHandler handler = new ValidationInboundAPIGatewayV2HTTPEventHandler();
        APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        event.setBody("{" +
              "    \"id\": 1," +
              "    \"name\": \"Lampshade\"," +
              "    \"price\": 42" +
              "}");
      
        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);
        assertThat(response.getBody()).isEqualTo("valid-test");
        assertThat(response.getStatusCode()).isEqualTo(200);
    }

    
    @Test
    void validate_inputKO_schemaInString_shouldThrowValidationException() {
    	ValidationInboundAPIGatewayV2HTTPEventHandler handler = new ValidationInboundAPIGatewayV2HTTPEventHandler();

        Map<String, String> headers = new HashMap<>();
        headers.put("header1", "value1");

        APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        event.setBody("{" +
              "    \"id\": 1," +
              "    \"name\": \"Lampshade\"" +
              "}");
        event.setHeaders(headers);

        APIGatewayV2HTTPResponse response = handler.handleRequest(event, context);
        assertThat(response.getBody()).isNotBlank();
        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getHeaders()).isEmpty();
        assertThat(response.getMultiValueHeaders()).isEmpty();
    }

    @ParameterizedTest
    @Event(value = "sqs.json", type = SQSEvent.class)
    void validate_SQS(SQSEvent event) {
        GenericSchemaV7StringHandler<Object> handler = new GenericSchemaV7StringHandler<>();
        assertThat(handler.handleRequest(event, context)).isEqualTo("OK");
    }

    @ParameterizedTest
    @Event(value = "sqs_invalid_messages.json", type = SQSEvent.class)
    void validate_SQS_with_validation_partial_failure(SQSEvent event) {
        StandardSQSHandler handler = new StandardSQSHandler();
        SQSBatchResponse response = handler.handleRequest(event, context);
        assertThat(response.getBatchItemFailures()).hasSize(2);
        assertThat(response.getBatchItemFailures().stream().map(SQSBatchResponse.BatchItemFailure::getItemIdentifier).collect(
                Collectors.toList())).contains("d9144555-9a4f-4ec3-99a0-fc4e625a8db3", "d9144555-9a4f-4ec3-99a0-fc4e625a8db5");
    }

    @ParameterizedTest
    @Event(value = "sqs_invalid_messages.json", type = SQSEvent.class)
    void validate_SQS_with_partial_failure(SQSEvent event) {
        SQSHandlerWithError handler = new SQSHandlerWithError();
        SQSBatchResponse response = handler.handleRequest(event, context);
        assertThat(response.getBatchItemFailures()).hasSize(3);
        assertThat(response.getBatchItemFailures().stream().map(SQSBatchResponse.BatchItemFailure::getItemIdentifier).collect(
                Collectors.toList())).contains("d9144555-9a4f-4ec3-99a0-fc4e625a8db3", "d9144555-9a4f-4ec3-99a0-fc4e625a8db5", "1234");
    }

    @ParameterizedTest
    @Event(value = "sqs_message.json", type = SQSEvent.class)
    void validate_SQS_CustomEnvelopeTakePrecedence(SQSEvent event) {
        SQSWithCustomEnvelopeHandler handler = new SQSWithCustomEnvelopeHandler();
        assertThat(handler.handleRequest(event, context)).isEqualTo("OK");
    }

    @ParameterizedTest
    @Event(value = "sqs_message.json", type = SQSEvent.class)
    void validate_SQS_WrongEnvelope_shouldThrowValidationException(SQSEvent event) {
        SQSWithWrongEnvelopeHandler handler = new SQSWithWrongEnvelopeHandler();
        assertThatExceptionOfType(ValidationException.class).isThrownBy(() -> handler.handleRequest(event, context));
    }

    @ParameterizedTest
    @Event(value = "kinesis.json", type = KinesisEvent.class)
    void validate_Kinesis(KinesisEvent event) {
        GenericSchemaV7StringHandler<Object> handler = new GenericSchemaV7StringHandler<>();
        assertThat(handler.handleRequest(event, context)).isEqualTo("OK");
    }

    @ParameterizedTest
    @Event(value = "kinesis_invalid_messages.json", type = KinesisEvent.class)
    void validate_Kinesis_with_validation_partial_failure(KinesisEvent event) {
        StandardKinesisHandler handler = new StandardKinesisHandler();
        StreamsEventResponse response = handler.handleRequest(event, context);
        assertThat(response.getBatchItemFailures()).hasSize(2);
        assertThat(response.getBatchItemFailures().stream().map(StreamsEventResponse.BatchItemFailure::getItemIdentifier).collect(
                Collectors.toList())).contains("49545115243490985018280067714973144582180062593244200962", "49545115243490985018280067714973144582180062593244200964");
    }

    @ParameterizedTest
    @Event(value = "kinesis_invalid_messages.json", type = KinesisEvent.class)
    void validate_Kinesis_with_partial_failure(KinesisEvent event) {
        KinesisHandlerWithError handler = new KinesisHandlerWithError();
        StreamsEventResponse response = handler.handleRequest(event, context);
        assertThat(response.getBatchItemFailures()).hasSize(3);
        assertThat(response.getBatchItemFailures().stream().map(StreamsEventResponse.BatchItemFailure::getItemIdentifier).collect(
                Collectors.toList())).contains("49545115243490985018280067714973144582180062593244200962", "49545115243490985018280067714973144582180062593244200964", "1234");
    }

    @ParameterizedTest
    @MethodSource("provideEventAndEventType")
    void validateEEvent(String jsonResource, Class eventClass) throws IOException {
        Object event = ValidationConfig.get().getObjectMapper()
                .readValue(this.getClass().getResourceAsStream(jsonResource), eventClass);

        GenericSchemaV7StringHandler<Object> handler = new GenericSchemaV7StringHandler<>();
        assertThat(handler.handleRequest(event, context)).isEqualTo("OK");
    }
}
