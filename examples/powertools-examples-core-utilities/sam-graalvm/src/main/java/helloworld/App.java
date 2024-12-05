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

package helloworld;

import static software.amazon.lambda.powertools.logging.argument.StructuredArguments.entry;
import static software.amazon.lambda.powertools.metrics.MetricsUtils.metricsLogger;
import static software.amazon.lambda.powertools.metrics.MetricsUtils.withSingleMetric;
import static software.amazon.lambda.powertools.tracing.TracingUtils.putMetadata;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import software.amazon.cloudwatchlogs.emf.model.DimensionSet;
import software.amazon.cloudwatchlogs.emf.model.StorageResolution;
import software.amazon.cloudwatchlogs.emf.model.Unit;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.metrics.Metrics;
import software.amazon.lambda.powertools.tracing.CaptureMode;
import software.amazon.lambda.powertools.tracing.Tracing;
import software.amazon.lambda.powertools.tracing.TracingUtils;

/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    @Logging(logEvent = true, samplingRate = 0.7)
    @Tracing(captureMode = CaptureMode.RESPONSE_AND_ERROR)
    @Metrics(namespace = "ServerlessAirline", service = "payment", captureColdStart = true)
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        Map<String, String> headers = new HashMap<>();

        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");

        metricsLogger().putMetric("CustomMetric1", 1, Unit.COUNT);

        withSingleMetric("CustomMetrics2", 1, Unit.COUNT, "Another", (metric) ->
        {
            metric.setDimensions(DimensionSet.of("AnotherService", "CustomService"));
            metric.setDimensions(DimensionSet.of("AnotherService1", "CustomService1"));
        });

        metricsLogger().putMetric("CustomMetric3", 1, Unit.COUNT, StorageResolution.HIGH);

        MDC.put("test", "willBeLogged");

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);
        try {
            final String pageContents = this.getPageContents("https://checkip.amazonaws.com");
            log.info("", entry("ip", pageContents));
            TracingUtils.putAnnotation("Test", "New");
            String output = String.format("{ \"message\": \"hello world\", \"location\": \"%s\" }", pageContents);

            TracingUtils.withSubsegment("loggingResponse", subsegment ->
            {
                String sampled = "log something out";
                log.info(sampled);
                log.info(output);
            });

            log.info("After output");
            return response
                    .withStatusCode(200)
                    .withBody(output);
        } catch (RuntimeException | IOException e) {
            return response
                    .withBody("{}")
                    .withStatusCode(500);
        }
    }

    @Tracing
    private void log() {
        log.info("inside threaded logging for function");
    }

    @Tracing(namespace = "getPageContents", captureMode = CaptureMode.DISABLED)
    private String getPageContents(String address) throws IOException {
        URL url = new URL(address);
        putMetadata("getPageContents", address);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()))) {
            return br.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }
}
