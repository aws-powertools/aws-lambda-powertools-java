package software.amazon.lambda.powertools.batch.examples;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import software.amazon.lambda.powertools.batch.SQSBatchRequestHandler;

public class ExampleBatchRequestHandler extends SQSBatchRequestHandler {

    @Override
    public void processItem(SQSEvent.SQSMessage message, Context context) {
        // Process an SQS message without throwing
    }
}
