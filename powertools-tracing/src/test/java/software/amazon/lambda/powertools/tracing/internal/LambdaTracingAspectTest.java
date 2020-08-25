/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates.
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
package software.amazon.lambda.powertools.tracing.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.xray.AWSXRay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import software.amazon.lambda.powertools.core.internal.LambdaHandlerProcessor;
import software.amazon.lambda.powertools.tracing.handlers.PowerToolDisabled;
import software.amazon.lambda.powertools.tracing.handlers.PowerToolDisabledForStream;
import software.amazon.lambda.powertools.tracing.handlers.PowerTracerToolEnabled;
import software.amazon.lambda.powertools.tracing.handlers.PowerTracerToolEnabledForStream;
import software.amazon.lambda.powertools.tracing.handlers.PowerTracerToolEnabledForStreamWithNoMetaData;
import software.amazon.lambda.powertools.tracing.handlers.PowerTracerToolEnabledWithException;
import software.amazon.lambda.powertools.tracing.handlers.PowerTracerToolEnabledWithNoMetaData;

import static org.apache.commons.lang3.reflect.FieldUtils.writeStaticField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

class LambdaTracingAspectTest {
    private RequestHandler<Object, Object> requestHandler;
    private RequestStreamHandler streamHandler;

    @Mock
    private Context context;

    @BeforeEach
    void setUp() throws IllegalAccessException {
        initMocks(this);
        writeStaticField(LambdaHandlerProcessor.class, "IS_COLD_START", null, true);
        setupContext();
        requestHandler = new PowerTracerToolEnabled();
        streamHandler = new PowerTracerToolEnabledForStream();
        AWSXRay.beginSegment(LambdaTracingAspectTest.class.getName());
    }

    @AfterEach
    void tearDown() {
        AWSXRay.endSegment();
    }

    @Test
    void shouldCaptureTraces() {
        requestHandler.handleRequest(new Object(), context);

        assertThat(AWSXRay.getTraceEntity().getSubsegments())
                .hasSize(1)
                .allSatisfy(subsegment -> {
                    assertThat(subsegment.getAnnotations())
                            .hasSize(1)
                            .containsEntry("ColdStart", true);

                    assertThat(subsegment.getMetadata())
                            .hasSize(1)
                            .containsKey("lambdaHandler");
                });
    }

    @Test
    void shouldCaptureTracesWithExceptionMetaData() {
        requestHandler = new PowerTracerToolEnabledWithException();

        Throwable exception = catchThrowable(() -> requestHandler.handleRequest(new Object(), context));

        assertThat(AWSXRay.getTraceEntity().getSubsegments())
                .hasSize(1)
                .allSatisfy(subsegment -> {
                    assertThat(subsegment.getAnnotations())
                            .hasSize(1)
                            .containsEntry("ColdStart", true);

                    assertThat(subsegment.getMetadata())
                            .hasSize(1)
                            .containsKey("lambdaHandler");

                    assertThat(subsegment.getMetadata().get("lambdaHandler"))
                            .satisfies(stringObjectMap -> assertThat(stringObjectMap)
                                    .containsEntry("handleRequest error", exception));

                    assertThat(subsegment.isFault())
                            .isTrue();

                    assertThat(subsegment.getCause().getExceptions())
                            .hasSize(1)
                            .allSatisfy(ex -> assertThat(ex.getThrowable())
                                    .isEqualTo(exception));
                });
    }

    @Test
    void shouldCaptureTracesForStream() throws IOException {
        streamHandler.handleRequest(new ByteArrayInputStream("test".getBytes()), new ByteArrayOutputStream(), context);

        assertThat(AWSXRay.getTraceEntity().getSubsegments())
                .hasSize(1)
                .allSatisfy(subsegment -> {
                    assertThat(subsegment.getAnnotations())
                            .hasSize(1)
                            .containsEntry("ColdStart", true);

                    assertThat(subsegment.getMetadata())
                            .hasSize(1)
                            .containsKey("streamHandler");
                });
    }

    @Test
    void shouldNotCaptureTracesNotEnabled() throws IOException {
        requestHandler = new PowerToolDisabled();
        requestHandler.handleRequest(new Object(), context);

        assertThat(AWSXRay.getTraceEntity().getSubsegments())
                .isEmpty();

        streamHandler = new PowerToolDisabledForStream();
        streamHandler.handleRequest(new ByteArrayInputStream("test".getBytes()), new ByteArrayOutputStream(), context);

        assertThat(AWSXRay.getTraceEntity().getSubsegments())
                .isEmpty();
    }

    @Test
    void shouldCaptureTracesWithNoMetadata() {
        requestHandler = new PowerTracerToolEnabledWithNoMetaData();

        requestHandler.handleRequest(new Object(), context);

        assertThat(AWSXRay.getTraceEntity().getSubsegments())
                .hasSize(1)
                .allSatisfy(subsegment -> {
                    assertThat(subsegment.getAnnotations())
                            .hasSize(1)
                            .containsEntry("ColdStart", true);

                    assertThat(subsegment.getMetadata())
                            .isEmpty();
                });
    }

    @Test
    void shouldCaptureTracesForStreamWithNoMetadata() throws IOException {
        streamHandler = new PowerTracerToolEnabledForStreamWithNoMetaData();

        streamHandler.handleRequest(new ByteArrayInputStream("test".getBytes()), new ByteArrayOutputStream(), context);

        assertThat(AWSXRay.getTraceEntity().getSubsegments())
                .hasSize(1)
                .allSatisfy(subsegment -> {
                    assertThat(subsegment.getAnnotations())
                            .hasSize(1)
                            .containsEntry("ColdStart", true);

                    assertThat(subsegment.getMetadata())
                            .isEmpty();
                });
    }


    private void setupContext() {
        when(context.getFunctionName()).thenReturn("testFunction");
        when(context.getInvokedFunctionArn()).thenReturn("testArn");
        when(context.getFunctionVersion()).thenReturn("1");
        when(context.getMemoryLimitInMB()).thenReturn(10);
    }
}