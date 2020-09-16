/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates.
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
package software.amazon.lambda.powertools.core.internal;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import org.aspectj.lang.ProceedingJoinPoint;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.of;

public final class LambdaHandlerProcessor {
    private static String SERVICE_NAME = null != System.getenv("POWERTOOLS_SERVICE_NAME")
            ? System.getenv("POWERTOOLS_SERVICE_NAME") : "service_undefined";
    private static Boolean IS_COLD_START = null;

    private LambdaHandlerProcessor() {
        // Hide default constructor
    }

    public static boolean isHandlerMethod(final ProceedingJoinPoint pjp) {
        return "handleRequest".equals(pjp.getSignature().getName());
    }

    public static boolean placedOnRequestHandler(final ProceedingJoinPoint pjp) {
        return RequestHandler.class.isAssignableFrom(pjp.getSignature().getDeclaringType())
                && pjp.getArgs().length == 2
                && pjp.getArgs()[1] instanceof Context;
    }

    public static boolean placedOnStreamHandler(final ProceedingJoinPoint pjp) {
        return RequestStreamHandler.class.isAssignableFrom(pjp.getSignature().getDeclaringType())
                && pjp.getArgs().length == 3
                && pjp.getArgs()[0] instanceof InputStream
                && pjp.getArgs()[1] instanceof OutputStream
                && pjp.getArgs()[2] instanceof Context;
    }

    public static Optional<Context> extractContext(final ProceedingJoinPoint pjp) {

        if (isHandlerMethod(pjp)) {
            if (placedOnRequestHandler(pjp)) {
                return of((Context) pjp.getArgs()[1]);
            }

            if (placedOnStreamHandler(pjp)) {
                return of((Context) pjp.getArgs()[2]);
            }
        }

        return empty();
    }

    public static String serviceName() {
        return SERVICE_NAME;
    }

    public static boolean isColdStart() {
        return IS_COLD_START == null;
    }

    public static void coldStartDone() {
        IS_COLD_START = false;
    }
}
