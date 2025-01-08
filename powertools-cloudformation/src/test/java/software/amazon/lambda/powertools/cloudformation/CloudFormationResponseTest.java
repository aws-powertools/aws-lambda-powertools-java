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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.CloudFormationCustomResourceEvent;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.utils.IoUtils;
import software.amazon.awssdk.utils.StringInputStream;
import software.amazon.lambda.powertools.cloudformation.CloudFormationResponse.ResponseBody;

public class CloudFormationResponseTest {

    /**
     * Creates a mock CloudFormationCustomResourceEvent with a non-null response URL.
     */
    static CloudFormationCustomResourceEvent mockCloudFormationCustomResourceEvent() {
        CloudFormationCustomResourceEvent event = mock(CloudFormationCustomResourceEvent.class);
        when(event.getResponseUrl()).thenReturn("https://aws.amazon.com");
        return event;
    }

    /**
     * Creates a CloudFormationResponse that does not make actual HTTP requests. The HTTP response body is the request
     * body.
     */
    static CloudFormationResponse testableCloudFormationResponse() {
        SdkHttpClient client = mock(SdkHttpClient.class);
        ExecutableHttpRequest executableRequest = mock(ExecutableHttpRequest.class);

        when(client.prepareRequest(any(HttpExecuteRequest.class))).thenAnswer(args ->
        {
            HttpExecuteRequest request = args.getArgument(0, HttpExecuteRequest.class);
            assertThat(request.contentStreamProvider()).isPresent();

            InputStream inputStream = request.contentStreamProvider().get().newStream();
            HttpExecuteResponse response = mock(HttpExecuteResponse.class);
            when(response.responseBody()).thenReturn(Optional.of(AbortableInputStream.create(inputStream)));
            when(executableRequest.call()).thenReturn(response);
            return executableRequest;
        });

        return new CloudFormationResponse(client);
    }

    static String responseAsString(HttpExecuteResponse response) throws IOException {
        assertThat(response.responseBody()).isPresent();
        InputStream bodyStream = response.responseBody().orElse(null);
        return bodyStream == null ? null : IoUtils.toUtf8String(bodyStream);
    }

    @Test
    void clientRequiredToCreateInstance() {
        assertThatThrownBy(() -> new CloudFormationResponse(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void eventRequiredToSend() {
        SdkHttpClient client = mock(SdkHttpClient.class);
        CloudFormationResponse response = new CloudFormationResponse(client);

        Context context = mock(Context.class);
        assertThatThrownBy(() -> response.send(null, context))
                .isInstanceOf(CustomResourceResponseException.class);
    }

    @Test
    void contextRequiredToSend() {
        SdkHttpClient client = mock(SdkHttpClient.class);
        CloudFormationResponse response = new CloudFormationResponse(client);

        Context context = mock(Context.class);
        assertThatThrownBy(() -> response.send(null, context))
                .isInstanceOf(CustomResourceResponseException.class);
    }

    @Test
    void eventResponseUrlRequiredToSend() {
        SdkHttpClient client = mock(SdkHttpClient.class);
        CloudFormationResponse response = new CloudFormationResponse(client);

        CloudFormationCustomResourceEvent event = mock(CloudFormationCustomResourceEvent.class);
        Context context = mock(Context.class);
        // not a CustomResourceResponseException since the URL is not part of the response but
        // rather the location the response is sent to
        assertThatThrownBy(() -> response.send(event, context))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void customPhysicalResponseId() {
        CloudFormationCustomResourceEvent event = mockCloudFormationCustomResourceEvent();
        when(event.getPhysicalResourceId()).thenReturn("This-Is-Ignored");

        Context context = mock(Context.class);
        when(context.getLogStreamName()).thenReturn("My-Log-Stream-Name");

        String customPhysicalResourceId = "Custom-Physical-Resource-ID";
        ResponseBody body = new ResponseBody(
                event, Response.Status.SUCCESS, customPhysicalResourceId, false,
                "See the details in CloudWatch Log Stream: " + context.getLogStreamName());
        assertThat(body.getPhysicalResourceId()).isEqualTo(customPhysicalResourceId);
    }

    @Test
    void responseBodyWithNullDataNode() {
        CloudFormationCustomResourceEvent event = mockCloudFormationCustomResourceEvent();
        Context context = mock(Context.class);

        ResponseBody responseBody = new ResponseBody(event, Response.Status.FAILED, null, true,
                "See the details in CloudWatch Log Stream: " + context.getLogStreamName());
        String actualJson = responseBody.toObjectNode(null).toString();

        String expectedJson = "{" +
                "\"Status\":\"FAILED\"," +
                "\"Reason\":\"See the details in CloudWatch Log Stream: null\"," +
                "\"PhysicalResourceId\":null," +
                "\"StackId\":null," +
                "\"RequestId\":null," +
                "\"LogicalResourceId\":null," +
                "\"NoEcho\":true," +
                "\"Data\":null" +
                "}";
        assertThat(actualJson).isEqualTo(expectedJson);
    }

    @Test
    void responseBodyWithNonNullDataNode() {
        CloudFormationCustomResourceEvent event = mockCloudFormationCustomResourceEvent();
        Context context = mock(Context.class);
        ObjectNode dataNode = ResponseBody.MAPPER.createObjectNode();
        dataNode.put("foo", "bar");
        dataNode.put("baz", 10);

        ResponseBody responseBody = new ResponseBody(event, Response.Status.FAILED, null, true,
                "See the details in CloudWatch Log Stream: " + context.getLogStreamName());
        String actualJson = responseBody.toObjectNode(dataNode).toString();

        String expectedJson = "{" +
                "\"Status\":\"FAILED\"," +
                "\"Reason\":\"See the details in CloudWatch Log Stream: null\"," +
                "\"PhysicalResourceId\":null," +
                "\"StackId\":null," +
                "\"RequestId\":null," +
                "\"LogicalResourceId\":null," +
                "\"NoEcho\":true," +
                "\"Data\":{\"foo\":\"bar\",\"baz\":10}" +
                "}";
        assertThat(actualJson).isEqualTo(expectedJson);
    }

    @Test
    void defaultStatusIsSuccess() {
        CloudFormationCustomResourceEvent event = mockCloudFormationCustomResourceEvent();
        Context context = mock(Context.class);

        ResponseBody body = new ResponseBody(
                event, null, null, false, "See the details in CloudWatch Log Stream: " + context.getLogStreamName());
        assertThat(body.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void customStatus() {
        CloudFormationCustomResourceEvent event = mockCloudFormationCustomResourceEvent();
        Context context = mock(Context.class);

        ResponseBody body = new ResponseBody(
                event, Response.Status.FAILED, null, false,
                "See the details in CloudWatch Log Stream: " + context.getLogStreamName());
        assertThat(body.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void reasonIncludesLogStreamName() {
        CloudFormationCustomResourceEvent event = mockCloudFormationCustomResourceEvent();

        String logStreamName = "My-Log-Stream-Name";
        Context context = mock(Context.class);
        when(context.getLogStreamName()).thenReturn(logStreamName);

        ResponseBody body = new ResponseBody(
                event, Response.Status.SUCCESS, null, false,
                "See the details in CloudWatch Log Stream: " + context.getLogStreamName());
        assertThat(body.getReason()).contains(logStreamName);
    }

    @Test
    public void sendWithNoResponseData() throws Exception {
        CloudFormationCustomResourceEvent event = mockCloudFormationCustomResourceEvent();
        Context context = mock(Context.class);
        CloudFormationResponse cfnResponse = testableCloudFormationResponse();

        HttpExecuteResponse response = cfnResponse.send(event, context);

        String actualJson = responseAsString(response);
        String expectedJson = "{" +
                "\"Status\":\"SUCCESS\"," +
                "\"Reason\":\"See the details in CloudWatch Log Stream: null\"," +
                "\"PhysicalResourceId\":null," +
                "\"StackId\":null," +
                "\"RequestId\":null," +
                "\"LogicalResourceId\":null," +
                "\"NoEcho\":false," +
                "\"Data\":null" +
                "}";
        assertThat(actualJson).isEqualTo(expectedJson);
    }

    @Test
    public void sendWithNonNullResponseData() throws Exception {
        CloudFormationCustomResourceEvent event = mockCloudFormationCustomResourceEvent();
        Context context = mock(Context.class);
        CloudFormationResponse cfnResponse = testableCloudFormationResponse();

        Map<String, String> responseData = new LinkedHashMap<>();
        responseData.put("Property", "Value");
        Response resp = Response.builder().value(responseData).build();

        HttpExecuteResponse response = cfnResponse.send(event, context, resp);

        String actualJson = responseAsString(response);
        String expectedJson = "{" +
                "\"Status\":\"SUCCESS\"," +
                "\"Reason\":\"See the details in CloudWatch Log Stream: null\"," +
                "\"PhysicalResourceId\":null," +
                "\"StackId\":null," +
                "\"RequestId\":null," +
                "\"LogicalResourceId\":null," +
                "\"NoEcho\":false," +
                "\"Data\":{\"Property\":\"Value\"}" +
                "}";
        assertThat(actualJson).isEqualTo(expectedJson);
    }

    @Test
    void responseBodyStreamNullResponseDefaultsToSuccessStatus() throws Exception {
        CloudFormationCustomResourceEvent event = mockCloudFormationCustomResourceEvent();
        Context context = mock(Context.class);
        CloudFormationResponse cfnResponse = testableCloudFormationResponse();

        StringInputStream stream = cfnResponse.responseBodyStream(event, context, null);

        String expectedJson = "{" +
                "\"Status\":\"SUCCESS\"," +
                "\"Reason\":\"See the details in CloudWatch Log Stream: null\"," +
                "\"PhysicalResourceId\":null," +
                "\"StackId\":null," +
                "\"RequestId\":null," +
                "\"LogicalResourceId\":null," +
                "\"NoEcho\":false," +
                "\"Data\":null" +
                "}";
        assertThat(stream.getString()).isEqualTo(expectedJson);
    }

    @Test
    void responseBodyStreamSuccessResponse() throws Exception {
        CloudFormationCustomResourceEvent event = mockCloudFormationCustomResourceEvent();
        Context context = mock(Context.class);
        CloudFormationResponse cfnResponse = testableCloudFormationResponse();

        StringInputStream stream = cfnResponse.responseBodyStream(event, context, Response.success());

        String expectedJson = "{" +
                "\"Status\":\"SUCCESS\"," +
                "\"Reason\":\"See the details in CloudWatch Log Stream: null\"," +
                "\"PhysicalResourceId\":null," +
                "\"StackId\":null," +
                "\"RequestId\":null," +
                "\"LogicalResourceId\":null," +
                "\"NoEcho\":false," +
                "\"Data\":null" +
                "}";
        assertThat(stream.getString()).isEqualTo(expectedJson);
    }

    @Test
    void responseBodyStreamFailedResponse() throws Exception {
        CloudFormationCustomResourceEvent event = mockCloudFormationCustomResourceEvent();
        Context context = mock(Context.class);
        CloudFormationResponse cfnResponse = testableCloudFormationResponse();

        StringInputStream stream = cfnResponse.responseBodyStream(event, context, Response.failed());

        String expectedJson = "{" +
                "\"Status\":\"FAILED\"," +
                "\"Reason\":\"See the details in CloudWatch Log Stream: null\"," +
                "\"PhysicalResourceId\":null," +
                "\"StackId\":null," +
                "\"RequestId\":null," +
                "\"LogicalResourceId\":null," +
                "\"NoEcho\":false," +
                "\"Data\":null" +
                "}";
        assertThat(stream.getString()).isEqualTo(expectedJson);
    }

    @Test
    void responseBodyStreamFailedResponseWithReason() throws Exception {
        CloudFormationCustomResourceEvent event = mockCloudFormationCustomResourceEvent();
        Context context = mock(Context.class);
        CloudFormationResponse cfnResponse = testableCloudFormationResponse();
        String failureReason = "Failed test reason";
        Response failedResponseWithReason = Response.builder().
                status(Response.Status.FAILED).reason(failureReason).build();
        StringInputStream stream = cfnResponse.responseBodyStream(event, context, failedResponseWithReason);

        String expectedJson = "{" +
                "\"Status\":\"FAILED\"," +
                "\"Reason\":\"" + failureReason + "\"," +
                "\"PhysicalResourceId\":null," +
                "\"StackId\":null," +
                "\"RequestId\":null," +
                "\"LogicalResourceId\":null," +
                "\"NoEcho\":false," +
                "\"Data\":null" +
                "}";
        assertThat(stream.getString()).isEqualTo(expectedJson);
    }
}
