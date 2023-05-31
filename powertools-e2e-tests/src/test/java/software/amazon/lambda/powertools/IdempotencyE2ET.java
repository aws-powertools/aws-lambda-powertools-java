package software.amazon.lambda.powertools;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import software.amazon.lambda.powertools.testutils.Infrastructure;
import software.amazon.lambda.powertools.testutils.lambda.InvocationResult;

import java.time.Year;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static software.amazon.lambda.powertools.testutils.lambda.LambdaInvoker.invokeFunction;

public class IdempotencyE2ET {
    private static Infrastructure infrastructure;
    private static String functionName;

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public static void setup() {
        infrastructure = Infrastructure.builder()
                .testName(IdempotencyE2ET.class.getSimpleName())
                .pathToFunction("idempotency")
                .idempotencyTable("idempo")
                .environmentVariables(Collections.singletonMap("IDEMPOTENCY_TABLE", "idempo"))
                .build();
        functionName = infrastructure.deploy();
    }

    @AfterAll
    public static void tearDown() {
        if (infrastructure != null)
            infrastructure.destroy();
    }

    @Test
    public void test_ttlNotExpired_sameResult_ttlExpired_differentResult() throws InterruptedException {
        // GIVEN
        String event = "{\"message\":\"TTL 10sec\"}";

        // WHEN
        // First invocation
        InvocationResult result1 = invokeFunction(functionName, event);

        // Second invocation (should get same result)
        InvocationResult result2 = invokeFunction(functionName, event);

        Thread.sleep(12000);

        // Third invocation (should get different result)
        InvocationResult result3 = invokeFunction(functionName, event);

        // THEN
        Assertions.assertThat(result1.getResult()).contains(Year.now().toString());
        Assertions.assertThat(result2.getResult()).isEqualTo(result1.getResult());
        Assertions.assertThat(result3.getResult()).isNotEqualTo(result2.getResult());
    }
}
