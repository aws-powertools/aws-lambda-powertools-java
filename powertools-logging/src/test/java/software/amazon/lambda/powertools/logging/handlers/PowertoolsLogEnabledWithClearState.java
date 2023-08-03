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

package software.amazon.lambda.powertools.logging.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.logging.LoggingUtils;

public class PowertoolsLogEnabledWithClearState implements RequestHandler<Object, Object> {
    private static final Logger LOG = LogManager.getLogger(PowertoolsLogEnabledWithClearState.class);
    public static int COUNT = 1;

    @Override
    @Logging(clearState = true)
    public Object handleRequest(Object input, Context context) {
        if (COUNT == 1) {
            LoggingUtils.appendKey("TestKey", "TestValue");
        }
        LOG.info("Test event");
        COUNT++;
        return null;
    }
}
