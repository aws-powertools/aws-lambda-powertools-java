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

package software.amazon.lambda.powertools.parameters;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import software.amazon.awssdk.annotations.NotThreadSafe;
import software.amazon.lambda.powertools.parameters.cache.CacheManager;
import software.amazon.lambda.powertools.parameters.exception.TransformationException;
import software.amazon.lambda.powertools.parameters.transform.BasicTransformer;
import software.amazon.lambda.powertools.parameters.transform.TransformationManager;
import software.amazon.lambda.powertools.parameters.transform.Transformer;

/**
 * Base class for all parameter providers.
 */
@NotThreadSafe
public abstract class BaseProvider implements ParamProvider {
    public static final String PARAMETERS = "parameters";

    protected final CacheManager cacheManager;
    private final TransformationManager transformationManager;
    private Clock clock;

    public BaseProvider(CacheManager cacheManager, TransformationManager transformationManager) {
        this.cacheManager = cacheManager;
        this.transformationManager = transformationManager;
    }

    /**
     * Retrieve the parameter value from the underlying parameter store.<br />
     * Abstract: Implement this method in a child class of {@link BaseProvider}
     *
     * @param key key of the parameter
     * @return the value of the parameter identified by the key
     */
    protected abstract String getValue(String key);

    /**
     * Retrieve multiple parameter values from the underlying parameter store.<br />
     * Abstract: Implement this method in a child class of {@link BaseProvider}
     *
     * @param path Parameter store path
     * @return Return map of parameter name value pairs
     */
    protected abstract Map<String, String> getMultipleValues(String path);

    /**
     * (Optional) Builder method to call before {@link #get(String)} or {@link #get(String, Class)}
     * to set cache max age for the parameter to get.<br/><br/>
     * The max age is reset to default (either 5 or a custom value that may be set on the CacheManager) after each get,
     * so you need to use this method for each parameter to cache with non-default max age.<br/><br/>
     *
     * <b>Not Thread Safe</b>: calling this method simultaneously by several threads
     * can lead to unwanted cache time for some parameters.<br/>
     *
     * @param maxAge Maximum time to cache the parameter, before calling the underlying parameter store.
     * @param unit   Unit of time
     * @return the provider itself in order to chain calls (eg. <pre>provider.withMaxAge(10, SECONDS).get("key")</pre>).
     */
    public BaseProvider withMaxAge(int maxAge, ChronoUnit unit) {
        cacheManager.setExpirationTime(Duration.of(maxAge, unit));
        return this;
    }

    /**
     * Builder method to call before {@link #get(String)} (Optional) or {@link #get(String, Class)} (Mandatory).
     * to provide a {@link Transformer} that will transform the String parameter into something else (String, Object, ...)<br/><br/>
     * <p>
     * {@link software.amazon.lambda.powertools.parameters.transform.Base64Transformer} and {@link software.amazon.lambda.powertools.parameters.transform.JsonTransformer}
     * are provided for respectively base64 and json content. You can also write your own (see {@link Transformer}).
     *
     * <b>Not Thread Safe</b>: calling this method simultaneously by several threads
     * can lead to errors (one Transformer for the wrong target type)<br/>
     *
     * @param transformerClass Class of the transformer to apply. For convenience, you can use {@link Transformer#json} or {@link Transformer#base64} shortcuts.
     * @return the provider itself in order to chain calls (eg. <pre>provider.withTransformation(json).get("key", MyObject.class)</pre>).
     */
    public BaseProvider withTransformation(Class<? extends Transformer> transformerClass) {
        if (transformationManager == null) {
            throw new IllegalStateException(
                    "Trying to add transformation while no TransformationManager has been provided.");
        }
        transformationManager.setTransformer(transformerClass);
        return this;
    }

    /**
     * Retrieve multiple parameter values either from the underlying store or a cached value (if not expired).<br/>
     * Cache all values with the 'path' as the key and also individually to be able to {@link #get(String)} a single value later<br/>
     * <i>Does not support transformation.</i>
     *
     * @param path path of the parameter
     * @return a map containing parameters keys and values. The key is a subpart of the path<br/>
     * eg. getMultiple("/foo/bar") will retrieve [key="baz", value="valuebaz"] for parameter "/foo/bar/baz"
     */
    @Override
    public Map<String, String> getMultiple(String path) {
        // remove trailing whitespace
        String pathWithoutTrailingSlash = path.replaceAll("\\/+$", "");
        try {
            return (Map<String, String>) cacheManager.getIfNotExpired(pathWithoutTrailingSlash, now()).orElseGet(() ->
            {
                Map<String, String> params = getMultipleValues(pathWithoutTrailingSlash);

                cacheManager.putInCache(pathWithoutTrailingSlash, params);

                params.forEach((k, v) -> cacheManager.putInCache(pathWithoutTrailingSlash + "/" + k, v));

                return params;
            });
        } finally {
            resetToDefaults();
        }
    }

    /**
     * Get the value of a parameter, either from the underlying store or a cached value (if not expired).<br/>
     * Using this method, you can apply a basic transformation (to String). <br/>
     * Set a {@link BasicTransformer} with {@link #withTransformation(Class)}.<br/><br/>
     * If you need a more complex transformation (to Object), use {@link #get(String, Class)} method instead of this one. <br/>
     *
     * @param key key of the parameter
     * @return the String value of the parameter
     * @throws IllegalStateException   if a wrong transformer class is provided through {@link #withTransformation(Class)}. Needs to be a {@link BasicTransformer}.
     * @throws TransformationException if the transformation could not be done, because of a wrong format or an error during transformation.
     */
    @Override
    public String get(final String key) {
        try {
            return (String) cacheManager.getIfNotExpired(key, now()).orElseGet(() ->
            {
                String value = getValue(key);

                String transformedValue = value;
                if (transformationManager != null && transformationManager.shouldTransform()) {
                    transformedValue = transformationManager.performBasicTransformation(value);
                }

                cacheManager.putInCache(key, transformedValue);

                return transformedValue;
            });
        } finally {
            // in all case, we reset options to default, for next call
            resetToDefaults();
        }
    }

    /**
     * Get the value of a parameter, either from the underlying store or a cached value (if not expired).<br/>
     * Using this method, you must apply a transformation (eg. json/xml to Object). <br/>
     * Set a {@link Transformer} with {@link #withTransformation(Class)}.<br/><br/>
     * If you need a simpler transformation (to String), use {@link #get(String)} method instead of this one.
     *
     * @param key         key of the parameter
     * @param targetClass class of the target Object (after transformation)
     * @return the Object (T) value of the parameter
     * @throws IllegalStateException   if no transformation class was provided through {@link #withTransformation(Class)}
     * @throws TransformationException if the transformation could not be done, because of a wrong format or an error during transformation.
     */
    @Override
    public <T> T get(final String key, final Class<T> targetClass) {
        try {
            return (T) cacheManager.getIfNotExpired(key, now()).orElseGet(() ->
            {
                String value = getValue(key);

                if (transformationManager == null) {
                    throw new IllegalStateException(
                            "Trying to transform value while no TransformationManager has been provided.");
                }
                T transformedValue = transformationManager.performComplexTransformation(value, targetClass);

                cacheManager.putInCache(key, transformedValue);

                return transformedValue;
            });
        } finally {
            // in all case, we reset options to default, for next call
            resetToDefaults();
        }
    }

    protected Instant now() {
        if (clock == null) {
            clock = Clock.systemDefaultZone();
        }
        return clock.instant();
    }

    protected void resetToDefaults() {
        cacheManager.resetExpirationTime();
        if (transformationManager != null) {
            transformationManager.setTransformer(null);
        }
    }

    /**
     * For test purpose
     *
     * @param clock
     */
    void setClock(Clock clock) {
        this.clock = clock;
    }
}
