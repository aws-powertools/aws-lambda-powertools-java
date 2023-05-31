package software.amazon.lambda.powertools;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import software.amazon.lambda.powertools.testutils.Infrastructure;
import software.amazon.lambda.powertools.testutils.lambda.InvocationResult;
import software.amazon.lambda.powertools.testutils.metrics.MetricsFetcher;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.lambda.powertools.testutils.lambda.LambdaInvoker.invokeFunction;

public class MetricsE2ET {
    private static final String namespace = "MetricsE2ENamespace_"+UUID.randomUUID();
    private static final String service = "MetricsE2EService_"+UUID.randomUUID();
    private static Infrastructure infrastructure;
    private static String functionName;

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public static void setup() {
        infrastructure = Infrastructure.builder()
                .testName(MetricsE2ET.class.getSimpleName())
                .pathToFunction("metrics")
                .environmentVariables(
                        Stream.of(new String[][]{
                                        {"POWERTOOLS_METRICS_NAMESPACE", namespace},
                                        {"POWERTOOLS_SERVICE_NAME", service}
                                })
                                .collect(Collectors.toMap(data -> data[0], data -> data[1])))
                .build();
        functionName = infrastructure.deploy();
    }

    @AfterAll
    public static void tearDown() {
        if (infrastructure != null)
            infrastructure.destroy();
    }

    @Test
    public void test_recordMetrics()  {
        // GIVEN
        String event1 = "{ \"metrics\": {\"orders\": 1, \"products\": 4}, \"dimensions\": { \"Environment\": \"test\"} }";

        // WHEN
        InvocationResult invocationResult = invokeFunction(functionName, event1);

        // THEN
        MetricsFetcher metricsFetcher = new MetricsFetcher();
        List<Double> coldStart = metricsFetcher.fetchMetrics(invocationResult.getStart(), invocationResult.getEnd(), 60, namespace, "ColdStart", Stream.of(new String[][]{
                {"FunctionName", functionName},
                {"Service", service}}
        ).collect(Collectors.toMap(data -> data[0], data -> data[1])));
        assertThat(coldStart.get(0)).isEqualTo(1);
        List<Double> orderMetrics = metricsFetcher.fetchMetrics(invocationResult.getStart(), invocationResult.getEnd(), 60, namespace, "orders", Collections.singletonMap("Environment", "test"));
        assertThat(orderMetrics.get(0)).isEqualTo(1);
        List<Double> productMetrics = metricsFetcher.fetchMetrics(invocationResult.getStart(), invocationResult.getEnd(), 60, namespace, "products", Collections.singletonMap("Environment", "test"));
        assertThat(productMetrics.get(0)).isEqualTo(4);
        orderMetrics = metricsFetcher.fetchMetrics(invocationResult.getStart(), invocationResult.getEnd(), 60, namespace, "orders", Collections.singletonMap("Service", service));
        assertThat(orderMetrics.get(0)).isEqualTo(1);
        productMetrics = metricsFetcher.fetchMetrics(invocationResult.getStart(), invocationResult.getEnd(), 60, namespace, "products", Collections.singletonMap("Service", service));
        assertThat(productMetrics.get(0)).isEqualTo(4);
    }
}
