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
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse;
import software.amazon.lambda.powertools.validation.Validation;

public class StandardKinesisHandler implements RequestHandler<KinesisEvent, StreamsEventResponse> {

    @Override
    @Validation(inboundSchema = "classpath:/schema_v7.json")
    public StreamsEventResponse handleRequest(KinesisEvent input, Context context) {
        StreamsEventResponse response = StreamsEventResponse.builder().build();
        assert input.getRecords().size() == 2; // invalid messages have been removed from the input
        return response;
    }
}
