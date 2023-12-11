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

package software.amazon.lambda.powertools.parameters.ssm;

import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.utils.StringUtils;
import software.amazon.lambda.powertools.parameters.BaseProvider;
import software.amazon.lambda.powertools.parameters.cache.CacheManager;
import software.amazon.lambda.powertools.parameters.transform.TransformationManager;

/**
 * AWS System Manager Parameter Store Provider <br/><br/>
 *
 * <u>Samples:</u>
 * <pre>
 *     SSMProvider provider = SSMProvider.builder().build();
 *
 *     String value = provider.get("key");
 *     System.out.println(value);
 *     >>> "value"
 *
 *     // Get a value and cache it for 30 seconds (all others values will now be cached for 30 seconds)
 *     String value = provider.defaultMaxAge(30, ChronoUnit.SECONDS).get("key");
 *
 *     // Get a value and cache it for 1 minute (all others values are cached for 5 seconds by default)
 *     String value = provider.withMaxAge(1, ChronoUnit.MINUTES).get("key");
 *
 *     // Get a base64 encoded value, decoded into a String, and store it in the cache
 *     String value = provider.withTransformation(Transformer.base64).get("key");
 *
 *     // Get a json value, transform it into an Object, and store it in the cache
 *     TargetObject = provider.withTransformation(Transformer.json).get("key", TargetObject.class);
 *
 *     // Get a decrypted value, and store it in the cache
 *     String value = provider.withDecryption().get("key");
 *
 *     // Get multiple parameter values starting with the same path
 *     Map<String, String> params = provider.getMultiple("/path/to/paramters");
 *     >>> /path/to/parameters/key1 -> value1
 *     >>> /path/to/parameters/key2 -> value2
 *
 *     // Get multiple parameter values starting with the same path and recursively
 *     Map<String, String> params = provider.recursive().getMultiple("/path/to/paramters");
 *     >>> /path/to/parameters/key1 -> value1
 *     >>> /path/to/parameters/key2 -> value2
 *     >>> /path/to/parameters/others/key3 -> value3
 * </pre>
 */
public class SSMProvider extends BaseProvider {

    private final SsmClient client;
    private boolean decrypt = false;
    private boolean recursive = false;

    /**
     * Constructor with custom {@link SsmClient}. <br/>
     * Use when you need to customize region or any other attribute of the client.<br/><br/>
     * <p>
     * Use the {@link SSMProviderBuilder} to create an instance of it.
     *
     * @param client                custom client you would like to use.
     * @param transformationManager Null, or a transformation manager
     */
    SSMProvider(CacheManager cacheManager, TransformationManager transformationManager, SsmClient client) {
        super(cacheManager, transformationManager);
        this.client = client;
    }

    /**
     * Create a builder that can be used to configure and create a {@link SSMProvider}.
     *
     * @return a new instance of {@link SSMProviderBuilder}
     */
    public static SSMProviderBuilder builder() {
        return new SSMProviderBuilder();
    }

    /**
     * Create a SSMProvider with all default settings.
     */
    public static SSMProvider create() {
        return new SSMProviderBuilder().build();
    }

    /**
     * Retrieve the parameter value from the AWS System Manager Parameter Store.
     *
     * @param key key of the parameter
     * @return the value of the parameter identified by the key
     */
    @Override
    public String getValue(String key) {
        GetParameterRequest request = GetParameterRequest.builder()
                .name(key)
                .withDecryption(decrypt)
                .build();
        return client.getParameter(request).parameter().value();
    }

    /**
     * Tells System Manager Parameter Store to decrypt the parameter value.<br/>
     * By default, parameter values are not decrypted.<br/>
     * Valid both for get and getMultiple.
     *
     * @return the provider itself in order to chain calls (eg. <pre>provider.withDecryption().get("key")</pre>).
     */
    public SSMProvider withDecryption() {
        this.decrypt = true;
        return this;
    }

    /**
     * Tells System Manager Parameter Store to retrieve all parameters starting with a path (all levels)<br/>
     * Only used with {@link #getMultiple(String)}.
     *
     * @return the provider itself in order to chain calls (eg. <pre>provider.recursive().getMultiple("key")</pre>).
     */
    public SSMProvider recursive() {
        this.recursive = true;
        return this;
    }

    /**
     * Retrieve multiple parameter values from AWS System Manager Parameter Store.<br/>
     * Retrieve all parameters starting with the path provided in parameter.<br/>
     * eg. getMultiple("/foo/bar") will retrieve /foo/bar/baz, foo/bar/biz<br/>
     * Using {@link #recursive()}, getMultiple("/foo/bar") will retrieve /foo/bar/baz, foo/bar/biz and foo/bar/buz/boz<br/>
     * Cache all values with the 'path' as the key and also individually to be able to {@link #get(String)} a single value later<br/>
     * <i>Does not support transformation.</i>
     *
     * @param path path of the parameter
     * @return a map containing parameters keys and values. The key is a subpart of the path<br/>
     * eg. getMultiple("/foo/bar") will retrieve [key="baz", value="valuebaz"] for parameter "/foo/bar/baz"
     */
    @Override
    protected Map<String, String> getMultipleValues(String path) {
        return getMultipleBis(path, null);
    }

    /**
     * Recursive method to deal with pagination (nextToken)
     */
    private Map<String, String> getMultipleBis(String path, String nextToken) {
        GetParametersByPathRequest request = GetParametersByPathRequest.builder()
                .path(path)
                .withDecryption(decrypt)
                .recursive(recursive)
                .nextToken(nextToken)
                .build();

        Map<String, String> params = new HashMap<>();

        // not using the client.getParametersByPathPaginator() as hardly testable
        GetParametersByPathResponse res = client.getParametersByPath(request);
        if (res.hasParameters()) {
            res.parameters().forEach(parameter ->
            {
                /* Standardize the parameter name
                   The parameter name returned by SSM will contain the full path.
                   However, for readability, we should return only the part after
                   the path.
                 */
                String name = parameter.name();
                if (name.startsWith(path)) {
                    name = name.replaceFirst(path, "");
                }
                name = name.replaceFirst("/", "");
                params.put(name, parameter.value());
            });
        }

        if (!StringUtils.isEmpty(res.nextToken())) {
            params.putAll(getMultipleBis(path, res.nextToken()));
        }

        return params;
    }

    @Override
    protected void resetToDefaults() {
        super.resetToDefaults();
        recursive = false;
        decrypt = false;
    }

    // For tests purpose only
    SsmClient getClient() {
        return client;
    }

}
