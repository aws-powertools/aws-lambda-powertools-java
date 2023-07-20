package software.amazon.lambda.powertools.batch;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import software.amazon.lambda.powertools.batch.handler.BatchMessageHandler;
import software.amazon.lambda.powertools.batch.handler.SqsBatchMessageHandler;

import java.util.function.BiConsumer;

/**
 * Builds a batch processor for the SQS event source.
 */
public class SqsBatchMessageHandlerBuilder extends AbstractMessageHandlerBuilder<SQSEvent.SQSMessage,
        SqsBatchMessageHandlerBuilder,
        SQSEvent,
        SQSBatchResponse> {


    @Override
    public BatchMessageHandler<SQSEvent, SQSBatchResponse> buildWithRawMessageHandler(BiConsumer<SQSEvent.SQSMessage, Context> rawMessageHandler) {
            return new SqsBatchMessageHandler<Void>(
                    null,
                    null,
                    rawMessageHandler,
                    successHandler,
                    failureHandler
            );
    }

    @Override
    public <M> BatchMessageHandler<SQSEvent, SQSBatchResponse> buildWithMessageHandler(BiConsumer<M, Context> messageHandler, Class<M> messageClass) {
        return new SqsBatchMessageHandler<>(
                messageHandler,
                messageClass,
                null,
                successHandler,
                failureHandler
        );
    }


    @Override
    protected SqsBatchMessageHandlerBuilder getThis() {
        return this;
    }




}
