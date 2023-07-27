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

package software.amazon.lambda.powertools.sqs;

import static com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.joining;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * When one or more {@link SQSMessage} fails and if any exception is thrown from {@link SqsMessageHandler#process(SQSMessage)}
 * during processing of a messages, this exception is with all the details of successful and failed messages.
 * </p>
 *
 * <p>
 * This exception can be thrown form:
 * <ul>
 *   <li>{@link SqsBatch}</li>
 *   <li>{@link SqsUtils#batchProcessor(SQSEvent, Class)}</li>
 *   <li>{@link SqsUtils#batchProcessor(SQSEvent, boolean, Class)}</li>
 *   <li>{@link SqsUtils#batchProcessor(SQSEvent, SqsMessageHandler)}</li>
 *   <li>{@link SqsUtils#batchProcessor(SQSEvent, boolean, SqsMessageHandler)}</li>
 * </ul>
 * </p>
 */
public class SQSBatchProcessingException extends RuntimeException {

    private final List<Exception> exceptions;
    private final List<SQSMessage> failures;
    private final List<Object> returnValues;

    public <T> SQSBatchProcessingException(final List<Exception> exceptions,
                                           final List<SQSMessage> failures,
                                           final List<T> successReturns) {
        super(exceptions.stream()
                .map(Throwable::toString)
                .collect(joining("\n")));

        this.exceptions = new ArrayList<>(exceptions);
        this.failures = new ArrayList<>(failures);
        this.returnValues = new ArrayList<>(successReturns);
    }

    /**
     * Details for exceptions that occurred while processing messages in {@link SqsMessageHandler#process(SQSMessage)}
     *
     * @return List of exceptions that occurred while processing messages
     */
    public List<Exception> getExceptions() {
        return unmodifiableList(exceptions);
    }

    /**
     * List of returns from {@link SqsMessageHandler#process(SQSMessage)} that were successfully processed.
     *
     * @return List of returns from successfully processed messages
     */
    public List<Object> successMessageReturnValues() {
        return unmodifiableList(returnValues);
    }

    /**
     * Details of {@link SQSMessage} that failed in {@link SqsMessageHandler#process(SQSMessage)}
     *
     * @return List of failed messages
     */
    public List<SQSMessage> getFailures() {
        return unmodifiableList(failures);
    }

    @Override
    public void printStackTrace() {
        for (Exception exception : exceptions) {
            exception.printStackTrace();
        }
    }
}
