/*
 * Copyright 2024 Amazon.com, Inc. or its affiliates.
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

package software.amazon.lambda.powertools.validation.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import java.util.ArrayList;
import software.amazon.lambda.powertools.validation.Validation;

public class SQSHandlerWithError implements RequestHandler<SQSEvent, SQSBatchResponse> {

    @Override
    @Validation(inboundSchema = "classpath:/schema_v7.json")
    public SQSBatchResponse handleRequest(SQSEvent input, Context context) {
        SQSBatchResponse response = SQSBatchResponse.builder().withBatchItemFailures(new ArrayList<>()).build();
        assert input.getRecords().size() == 2; // invalid messages have been removed from the input
        response.getBatchItemFailures().add(SQSBatchResponse.BatchItemFailure.builder().withItemIdentifier("1234").build());
        return response;
    }
}
