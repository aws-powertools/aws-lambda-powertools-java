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
package software.amazon.lambda.powertools.parameters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.lambda.powertools.parameters.cache.CacheManager;
import software.amazon.lambda.powertools.parameters.cache.NowProvider;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.openMocks;

public class SecretsProviderTest {

    @Mock
    SecretsManagerClient client;

    @Spy
    NowProvider nowProvider;

    @Captor
    ArgumentCaptor<GetSecretValueRequest> paramCaptor;

    CacheManager cacheManager;

    SecretsProvider provider;

    @BeforeEach
    public void init() {
        openMocks(this);
        cacheManager = new CacheManager(nowProvider);
        provider = new SecretsProvider(cacheManager, client);
    }

    @Test
    public void getValue() {
        String key = "Key1";
        String expectedValue = "Value1";
        GetSecretValueResponse response = GetSecretValueResponse.builder().secretString(expectedValue).build();
        Mockito.when(client.getSecretValue(paramCaptor.capture())).thenReturn(response);

        String value = provider.getValue(key);

        assertThat(value).isEqualTo(expectedValue);
        assertThat(paramCaptor.getValue().secretId()).isEqualTo(key);
    }

    @Test
    public void getValueBase64() {
        String key = "Key2";
        String expectedValue = "Value2";
        byte[] valueb64 = Base64.getEncoder().encode(expectedValue.getBytes());
        GetSecretValueResponse response = GetSecretValueResponse.builder().secretBinary(SdkBytes.fromByteArray(valueb64)).build();
        Mockito.when(client.getSecretValue(paramCaptor.capture())).thenReturn(response);

        String value = provider.getValue(key);

        assertThat(value).isEqualTo(expectedValue);
        assertThat(paramCaptor.getValue().secretId()).isEqualTo(key);
    }
}
