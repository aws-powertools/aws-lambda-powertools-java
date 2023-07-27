package software.amazon.lambda.powertools.batch.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A batch message processor for DynamoDB Streams batches.
 *
 * @see <a href="https://docs.aws.amazon.com/lambda/latest/dg/with-ddb.html#services-ddb-batchfailurereporting">DynamoDB Streams batch failure reporting</a>
 */
public class DynamoDbBatchMessageHandler implements BatchMessageHandler<DynamodbEvent, StreamsEventResponse> {
    private final static Logger LOGGER = LoggerFactory.getLogger(DynamoDbBatchMessageHandler.class);

    private final Consumer<DynamodbEvent.DynamodbStreamRecord> successHandler;
    private final BiConsumer<DynamodbEvent.DynamodbStreamRecord, Throwable> failureHandler;
    private final BiConsumer<DynamodbEvent.DynamodbStreamRecord, Context> rawMessageHandler;

    public DynamoDbBatchMessageHandler(Consumer<DynamodbEvent.DynamodbStreamRecord> successHandler,
                                       BiConsumer<DynamodbEvent.DynamodbStreamRecord, Throwable> failureHandler,
                                       BiConsumer<DynamodbEvent.DynamodbStreamRecord, Context> rawMessageHandler) {
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
        this.rawMessageHandler = rawMessageHandler;
    }

    @Override
    public StreamsEventResponse processBatch(DynamodbEvent event, Context context) {
        List<StreamsEventResponse.BatchItemFailure> batchFailures = new ArrayList<>();

        for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {
            try {

                rawMessageHandler.accept(record, context);
                // Report success if we have a handler
                if (this.successHandler != null) {
                    this.successHandler.accept(record);
                }
            } catch (Throwable t) {
                String sequenceNumber = record.getDynamodb().getSequenceNumber();
                LOGGER.error("Error while processing record with id {}: {}, adding it to batch item failures",
                        sequenceNumber, t.getMessage());
                batchFailures.add(new StreamsEventResponse.BatchItemFailure(sequenceNumber));

                // Report failure if we have a handler
                if (this.failureHandler != null) {
                    // A failing failure handler is no reason to fail the batch
                    try {
                        this.failureHandler.accept(record, t);
                    } catch (Throwable t2) {
                        LOGGER.warn("failureHandler threw handling failure", t2);
                    }
                }
            }
        }

        return new StreamsEventResponse(batchFailures);
    }
}
