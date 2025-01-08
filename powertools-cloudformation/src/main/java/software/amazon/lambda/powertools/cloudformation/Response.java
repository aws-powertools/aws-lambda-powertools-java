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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import software.amazon.awssdk.utils.StringUtils;

/**
 * Models the arbitrary data to be sent to the custom resource in response to a CloudFormation event. This object
 * encapsulates the data and the means to serialize it.
 */
public class Response {

    private final JsonNode jsonNode;
    private final Status status;
    private final String physicalResourceId;
    private final boolean noEcho;
    private final String reason;

    private Response(JsonNode jsonNode, Status status, String physicalResourceId, boolean noEcho) {
        this.jsonNode = jsonNode;
        this.status = status;
        this.physicalResourceId = physicalResourceId;
        this.noEcho = noEcho;
        this.reason = null;
    }

    private Response(JsonNode jsonNode, Status status, String physicalResourceId, boolean noEcho, String reason) {
        this.jsonNode = jsonNode;
        this.status = status;
        this.physicalResourceId = physicalResourceId;
        this.noEcho = noEcho;
        this.reason = reason;
    }

    /**
     * Creates a builder for constructing a Response wrapping the provided value.
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a failed Response with no physicalResourceId set. Powertools for AWS Lambda (Java) will set the physicalResourceId to the
     * Lambda LogStreamName
     * <p>
     * The value returned for a PhysicalResourceId can change custom resource update operations. If the value returned
     * is the same, it is considered a normal update. If the value returned is different, AWS CloudFormation recognizes
     * the update as a replacement and sends a delete request to the old resource. For more information,
     * see AWS::CloudFormation::CustomResource.
     *
     * @return a failed Response with no value.
     * @deprecated this method is not safe. Provide a physicalResourceId.
     */
    @Deprecated
    public static Response failed() {
        return new Response(null, Status.FAILED, null, false);
    }

    /**
     * Creates a failed Response with a given physicalResourceId.
     *
     * @param physicalResourceId The value must be a non-empty string and must be identical for all responses for the
     *                           same resource.
     *                           The value returned for a PhysicalResourceId can change custom resource update
     *                           operations. If the value returned is the same, it is considered a normal update. If the
     *                           value returned is different, AWS CloudFormation recognizes the update as a replacement
     *                           and sends a delete request to the old resource. For more information,
     *                           see AWS::CloudFormation::CustomResource.
     * @return a failed Response with physicalResourceId
     */
    public static Response failed(String physicalResourceId) {
        return new Response(null, Status.FAILED, physicalResourceId, false);
    }

    /**
     * Creates a successful Response with no physicalResourceId set. Powertools for AWS Lambda (Java) will set the physicalResourceId to the
     * Lambda LogStreamName
     * <p>
     * The value returned for a PhysicalResourceId can change custom resource update operations. If the value returned
     * is the same, it is considered a normal update. If the value returned is different, AWS CloudFormation recognizes
     * the update as a replacement and sends a delete request to the old resource. For more information,
     * see AWS::CloudFormation::CustomResource.
     *
     * @return a success Response with no physicalResourceId value.
     * @deprecated this method is not safe. Provide a physicalResourceId.
     */
    @Deprecated
    public static Response success() {
        return new Response(null, Status.SUCCESS, null, false);
    }

    /**
     * Creates a successful Response with a given physicalResourceId.
     *
     * @param physicalResourceId The value must be a non-empty string and must be identical for all responses for the
     *                           same resource.
     *                           The value returned for a PhysicalResourceId can change custom resource update
     *                           operations. If the value returned is the same, it is considered a normal update. If the
     *                           value returned is different, AWS CloudFormation recognizes the update as a replacement
     *                           and sends a delete request to the old resource. For more information,
     *                           see AWS::CloudFormation::CustomResource.
     * @return a success Response with physicalResourceId
     */
    public static Response success(String physicalResourceId) {
        return new Response(null, Status.SUCCESS, physicalResourceId, false);
    }

    /**
     * Returns a JsonNode representation of the Response.
     *
     * @return a non-null JsonNode representation
     */
    JsonNode getJsonNode() {
        return jsonNode;
    }

    /**
     * The success/failed status of the Response.
     *
     * @return a non-null Status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * The physical resource ID. If null, the default physical resource ID will be provided to the custom resource.
     *
     * @return a potentially null physical resource ID
     */
    public String getPhysicalResourceId() {
        return physicalResourceId;
    }

    /**
     * Whether to mask custom resource output (true) or not (false).
     *
     * @return true if custom resource output is to be masked, false otherwise
     */
    public boolean isNoEcho() {
        return noEcho;
    }

    /**
     * The reason for the failure.
     *
     * @return a potentially null reason
     */
    public String getReason() {
        return reason;
    }

    /**
     * Includes all Response attributes, including its value in JSON format
     *
     * @return a full description of the Response
     */
    @Override
    public String toString() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("JSON", jsonNode == null ? null : jsonNode.toString());
        attributes.put("Status", status);
        attributes.put("PhysicalResourceId", physicalResourceId);
        attributes.put("NoEcho", noEcho);
        attributes.put("Reason", reason);
        return attributes.entrySet().stream()
                .map(entry -> entry.getKey() + " = " + entry.getValue())
                .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Indicates whether a response is a success or failure.
     */
    public enum Status {
        SUCCESS, FAILED
    }

    /**
     * For building Response instances.
     */
    public static class Builder {
        private Object value;
        private ObjectMapper objectMapper;
        private Status status;
        private String physicalResourceId;
        private boolean noEcho;
        private String reason;

        private Builder() {
        }

        /**
         * Configures the value of this Response, typically a Map of name/value pairs.
         *
         * @param value if null, the Response will be empty
         * @return a reference to this builder
         */
        public Builder value(Object value) {
            this.value = value;
            return this;
        }

        /**
         * Configures a custom ObjectMapper for serializing the value object. Creates a copy of the mapper provided;
         * future mutations of the ObjectMapper made using the provided reference will not affect Response
         * serialization.
         *
         * @param objectMapper if null, a default mapper will be used
         * @return a reference to this builder
         */
        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper == null ? null : objectMapper.copy();
            return this;
        }

        /**
         * Configures the status of this response.
         *
         * @param status if null, SUCCESS will be assumed
         * @return a reference to this builder
         */
        public Builder status(Status status) {
            this.status = status;
            return this;
        }

        /**
         * A unique identifier for the custom resource being responded to. By default, the identifier is the name of the
         * Amazon CloudWatch Logs log stream associated with the Lambda function.
         *
         * @param physicalResourceId if null, the default resource ID will be used
         * @return a reference to this builder
         */
        public Builder physicalResourceId(String physicalResourceId) {
            this.physicalResourceId = physicalResourceId;
            return this;
        }

        /**
         * Indicates whether to mask the output of the custom resource when it's retrieved by using the Fn::GetAtt
         * function. If set to true, values will be masked with asterisks (*****), except for information stored in the
         * these locations:
         * <ul>
         * <li>The Metadata template section. CloudFormation does not transform, modify, or redact any information
         * included in the Metadata section. See
         * https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/metadata-section-structure.html</li>
         * <li>The Outputs template section. See
         * https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/outputs-section-structure.html</li>
         * <li>The Metadata attribute of a resource definition. See
         * https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-attribute-metadata.html</li>
         * </ul>
         * <p>
         * We strongly recommend not using these mechanisms to include sensitive information, such as passwords or
         * secrets.
         * <p>
         * For more information about using noEcho to mask sensitive information, see
         * https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/best-practices.html#creds
         * <p>
         * By default, this value is false.
         *
         * @param noEcho when true, masks certain output
         * @return a reference to this builder
         */
        public Builder noEcho(boolean noEcho) {
            this.noEcho = noEcho;
            return this;
        }

        /**
         * Reason for the response.
         * Reason is optional for Success responses, but required for Failed responses.
         * If not provided it will be replaced with cloudwatch log stream name.
         *
         * @param reason if null, the default reason will be used
         * @return a reference to this builder
         */

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        /**
         * Builds a Response object for the value.
         *
         * @return a Response object wrapping the initially provided value.
         */
        public Response build() {
            JsonNode node;
            if (value == null) {
                node = null;
            } else {
                ObjectMapper mapper = objectMapper != null ? objectMapper : CloudFormationResponse.ResponseBody.MAPPER;
                node = mapper.valueToTree(value);
            }
            Status responseStatus = this.status != null ? this.status : Status.SUCCESS;
            if (StringUtils.isNotBlank(this.reason)) {
                return new Response(node, responseStatus, physicalResourceId, noEcho, reason);
            }
            return new Response(node, responseStatus, physicalResourceId, noEcho);
        }
    }
}
