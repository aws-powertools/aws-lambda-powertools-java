package software.amazon.lambda.powertools.metrics.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;
import software.amazon.lambda.powertools.metrics.PowertoolsMetrics;

import static software.amazon.lambda.powertools.metrics.PowertoolsMetricsLogger.metricsLogger;

public class PowertoolsMetricsExceptionWhenNoMetricsHandler implements RequestHandler<Object, Object> {

    @Override
    @PowertoolsMetrics(namespace = "ExampleApplication", service = "booking", raiseOnEmptyMetrics = true)
    public Object handleRequest(Object input, Context context) {
        MetricsLogger metricsLogger = metricsLogger();
        metricsLogger.putMetadata("MetaData", "MetaDataValue");

        return null;
    }
}
