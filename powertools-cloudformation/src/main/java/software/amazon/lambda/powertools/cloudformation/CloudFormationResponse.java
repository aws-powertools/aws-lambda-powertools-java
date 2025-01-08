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

package software.amazon.lambda.powertools.cloudformation;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.CloudFormationCustomResourceEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.Header;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.utils.StringInputStream;
import software.amazon.awssdk.utils.StringUtils;

/**
 * Client for sending responses to AWS CloudFormation custom resources by way of a response URL, which is an Amazon S3
 * pre-signed URL.
 * <p>
 * See https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/cfn-lambda-function-code-cfnresponsemodule.html
 * <p>
 * This class is thread-safe provided the SdkHttpClient instance used is also thread-safe.
 */
class CloudFormationResponse {

    private static final Logger LOG = LoggerFactory.getLogger(CloudFormationResponse.class);
    private final SdkHttpClient client;

    /**
     * Creates a new CloudFormationResponse that uses the provided HTTP client and default JSON serialization format.
     *
     * @param client HTTP client to use for sending requests; cannot be null
     */
    CloudFormationResponse(SdkHttpClient client) {
        this.client = Objects.requireNonNull(client, "SdkHttpClient cannot be null");
    }

    /**
     * The underlying SdkHttpClient used by this class.
     *
     * @return a non-null client
     */
    SdkHttpClient getClient() {
        return client;
    }

    /**
     * Forwards a response containing a custom payload to the target resource specified by the event. The payload is
     * formed from the event and context data. Status is assumed to be SUCCESS.
     *
     * @param event   custom CF resource event. Cannot be null.
     * @param context used to specify when the function and any callbacks have completed execution, or to
     *                access information from within the Lambda execution environment. Cannot be null.
     * @return the response object
     * @throws IOException                     when unable to send the request
     * @throws CustomResourceResponseException when unable to synthesize or serialize the response payload
     */
    public HttpExecuteResponse send(CloudFormationCustomResourceEvent event,
                                    Context context) throws IOException, CustomResourceResponseException {
        return send(event, context, null);
    }

    /**
     * Forwards a response containing a custom payload to the target resource specified by the event. The payload is
     * formed from the event, context, and response data.
     *
     * @param event        custom CF resource event. Cannot be null.
     * @param context      used to specify when the function and any callbacks have completed execution, or to
     *                     access information from within the Lambda execution environment. Cannot be null.
     * @param responseData response to send, e.g. a list of name-value pairs. If null, an empty success is assumed.
     * @return the response object
     * @throws IOException                     when unable to generate or send the request
     * @throws CustomResourceResponseException when unable to serialize the response payload
     */
    public HttpExecuteResponse send(CloudFormationCustomResourceEvent event,
                                    Context context,
                                    Response responseData) throws IOException, CustomResourceResponseException {
        // no need to explicitly close in-memory stream
        StringInputStream stream = responseBodyStream(event, context, responseData);
        URI uri = URI.create(event.getResponseUrl());
        SdkHttpRequest request = SdkHttpRequest.builder()
                .uri(uri)
                .method(SdkHttpMethod.PUT)
                .headers(headers(stream.available()))
                .build();
        HttpExecuteRequest httpExecuteRequest = HttpExecuteRequest.builder()
                .request(request)
                .contentStreamProvider(() -> stream)
                .build();
        return client.prepareRequest(httpExecuteRequest).call();
    }

    /**
     * Generates HTTP headers to be supplied in the CloudFormation request.
     *
     * @param contentLength the length of the payload
     * @return HTTP headers
     */
    protected Map<String, List<String>> headers(int contentLength) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(Header.CONTENT_TYPE, Collections.emptyList()); // intentionally empty
        headers.put(Header.CONTENT_LENGTH, Collections.singletonList(Integer.toString(contentLength)));
        return headers;
    }

    /**
     * Returns the response body as an input stream, for supplying with the HTTP request to the custom resource.
     * <p>
     * If PhysicalResourceId is null at this point it will be replaced with the Lambda LogStreamName.
     *
     * @throws CustomResourceResponseException if unable to generate the response stream
     */
    StringInputStream responseBodyStream(CloudFormationCustomResourceEvent event,
                                         Context context,
                                         Response resp) throws CustomResourceResponseException {
        try {
            String reason = "See the details in CloudWatch Log Stream: " + context.getLogStreamName();
            if (resp == null) {
                String physicalResourceId = event.getPhysicalResourceId() != null ? event.getPhysicalResourceId() :
                        context.getLogStreamName();

                ResponseBody body = new ResponseBody(event, Response.Status.SUCCESS, physicalResourceId, false, reason);
                LOG.debug("ResponseBody: {}", body);
                ObjectNode node = body.toObjectNode(null);
                return new StringInputStream(node.toString());
            } else {
                if (!StringUtils.isBlank(resp.getReason())) {
                    reason = resp.getReason();
                }
                String physicalResourceId = resp.getPhysicalResourceId() != null ? resp.getPhysicalResourceId() :
                        event.getPhysicalResourceId() != null ? event.getPhysicalResourceId() :
                                context.getLogStreamName();

                ResponseBody body =
                        new ResponseBody(event, resp.getStatus(), physicalResourceId, resp.isNoEcho(), reason);
                LOG.debug("ResponseBody: {}", body);
                ObjectNode node = body.toObjectNode(resp.getJsonNode());
                return new StringInputStream(node.toString());
            }
        } catch (RuntimeException e) {
            LOG.error(e.getMessage());
            throw new CustomResourceResponseException("Unable to generate response body.", e);
        }
    }

    /**
     * Internal representation of the payload to be sent to the event target URL. Retains all properties of the payload
     * except for "Data". This is done so that the serialization of the non-"Data" properties and the serialization of
     * the value of "Data" can be handled by separate ObjectMappers, if need be. The former properties are dictated by
     * the custom resource but the latter is dictated by the implementor of the custom resource handler.
     */
    @SuppressWarnings("unused")
    static class ResponseBody {
        static final ObjectMapper MAPPER = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE);
        private static final String DATA_PROPERTY_NAME = "Data";

        private final String status;
        private final String reason;
        private final String physicalResourceId;
        private final String stackId;
        private final String requestId;
        private final String logicalResourceId;
        private final boolean noEcho;

        ResponseBody(CloudFormationCustomResourceEvent event,
                     Response.Status responseStatus,
                     String physicalResourceId,
                     boolean noEcho,
                     String reason) {
            Objects.requireNonNull(event, "CloudFormationCustomResourceEvent cannot be null");

            this.physicalResourceId = physicalResourceId;
            this.reason = reason;
            this.status = responseStatus == null ? Response.Status.SUCCESS.name() : responseStatus.name();
            this.stackId = event.getStackId();
            this.requestId = event.getRequestId();
            this.logicalResourceId = event.getLogicalResourceId();
            this.noEcho = noEcho;
        }

        public String getStatus() {
            return status;
        }

        public String getReason() {
            return reason;
        }

        public String getPhysicalResourceId() {
            return physicalResourceId;
        }

        public String getStackId() {
            return stackId;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getLogicalResourceId() {
            return logicalResourceId;
        }

        public boolean isNoEcho() {
            return noEcho;
        }

        /**
         * Returns this ResponseBody as an ObjectNode with the provided JsonNode as the value of its "Data" property.
         *
         * @param dataNode the value of the "Data" property for the returned node; may be null
         * @return an ObjectNode representation of this ResponseBody and the provided dataNode
         */
        ObjectNode toObjectNode(JsonNode dataNode) {
            ObjectNode node = MAPPER.valueToTree(this);
            if (dataNode == null) {
                node.putNull(DATA_PROPERTY_NAME);
            } else {
                node.set(DATA_PROPERTY_NAME, dataNode);
            }
            return node;
        }

        @Override
        public String toString() {
            final StringBuffer sb = new StringBuffer("ResponseBody{");
            sb.append("status='").append(status).append('\'');
            sb.append(", reason='").append(reason).append('\'');
            sb.append(", physicalResourceId='").append(physicalResourceId).append('\'');
            sb.append(", stackId='").append(stackId).append('\'');
            sb.append(", requestId='").append(requestId).append('\'');
            sb.append(", logicalResourceId='").append(logicalResourceId).append('\'');
            sb.append(", noEcho=").append(noEcho);
            sb.append('}');
            return sb.toString();
        }
    }
}
