package software.amazon.lambda.powertools.parameters;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationRequest;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationResponse;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionRequest;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionResponse;
import software.amazon.awssdk.utils.IoUtils;
import software.amazon.lambda.powertools.parameters.cache.CacheManager;
import software.amazon.lambda.powertools.parameters.transform.TransformationManager;
import software.amazon.lambda.powertools.parameters.transform.Transformer;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.temporal.ChronoUnit;
import java.util.Map;

public class AppConfigProvider extends BaseProvider {

    private AppConfigDataClient client;

    AppConfigProvider() {
        this(new CacheManager());
    }

    AppConfigProvider(CacheManager cacheManager) {
        this(cacheManager, defaultClient());
    }

    AppConfigProvider(CacheManager cacheManager, AppConfigDataClient client) {
        super(cacheManager);
        this.client = client;
    }

    /**
     * Create a builder that can be used to configure and create a {@link AppConfigProvider}.
     *
     * @return a new instance of {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected String getValue(String key) {
        String[] profile = key.split("/");
        if (profile.length < 3) {
            throw new IllegalArgumentException("Your key is incorrect, please specify an 'application', an 'environment' and the 'configuration' separated with '/', eg. '/myapp/prod/myvar'");
        }
        int index = key.startsWith("/") ? 1 : 0;
        String application = profile[index];
        String environment = profile[index + 1];
        String configuration = profile[index + 2];
        
        if (useAppConfigExtension()) {
            return getValueWithExtension(application, environment, configuration);
        } else {
            return getValueWithClient(application, environment, configuration);
        }
    }

    private String getValueWithClient(String application, String environment, String configuration) {
        StartConfigurationSessionResponse sessionResponse = client.startConfigurationSession(StartConfigurationSessionRequest.builder()
                .applicationIdentifier(application)
                .environmentIdentifier(environment)
                .configurationProfileIdentifier(configuration)
                .build());
        GetLatestConfigurationResponse configurationResponse = client.getLatestConfiguration(GetLatestConfigurationRequest.builder()
                .configurationToken(sessionResponse.initialConfigurationToken())
                .build());
        return configurationResponse.configuration().asUtf8String();
    }

    private String getValueWithExtension(String application, String environment, String configuration) {
        try {
            HttpURLConnection connection = connectToExtension(application, environment, configuration);
            if (connection.getResponseCode() == 200) {
                InputStream responseStream = connection.getInputStream();
                return IoUtils.toUtf8String(responseStream);
            }
            throw new IOException("Error " + connection.getResponseCode() + ": " + connection.getResponseMessage());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Your key is incorrect, please specify an 'application', an 'environment' and the 'configuration' separated with '/', eg. '/myapp/prod/myvar'", e);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot connect to the AppConfig extension, please add the extension layer to your function (see https://docs.aws.amazon.com/appconfig/latest/userguide/appconfig-integration-lambda-extensions-versions.html)", e);
        }
    }

    HttpURLConnection connectToExtension(String application, String environment, String configuration) throws IOException {
        URL url = new URL(getExtensionUrl(application, environment, configuration));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "*/*");
        return connection;
    }

    String getExtensionUrl(String application, String environment, String configuration) {
        return String.format("http://localhost:2772/applications/%s/environments/%s/configurations/%s",
                application, environment, configuration);
    }

    /**
     * @throws UnsupportedOperationException as it is not possible to get multiple values simultaneously from App Config
     */
    @Override
    protected Map<String, String> getMultipleValues(String path) {
        throw new UnsupportedOperationException("Impossible to get multiple values from AWS Secrets Manager");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppConfigProvider defaultMaxAge(int maxAge, ChronoUnit unit) {
        super.defaultMaxAge(maxAge, unit);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppConfigProvider withMaxAge(int maxAge, ChronoUnit unit) {
        super.withMaxAge(maxAge, unit);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppConfigProvider withTransformation(Class<? extends Transformer> transformerClass) {
        super.withTransformation(transformerClass);
        return this;
    }

    public static boolean useAppConfigExtension() {
        String appConfigExtensionEnv = System.getenv().get("POWERTOOLS_APPCONFIG_EXTENSION");
        return appConfigExtensionEnv != null && !appConfigExtensionEnv.equalsIgnoreCase("false");
    }

    private static AppConfigDataClient defaultClient() {
        return useAppConfigExtension() ? null : AppConfigDataClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .region(Region.of(System.getenv(SdkSystemSetting.AWS_REGION.environmentVariable())))
                .build();
    }

    static class Builder {

        private AppConfigDataClient client;
        private CacheManager cacheManager;
        private TransformationManager transformationManager;

        /**
         * Create a {@link AppConfigProvider} instance.
         *
         * @return a {@link AppConfigProvider}
         */
        public AppConfigProvider build() {
            if (cacheManager == null) {
                throw new IllegalStateException("No CacheManager provided, please provide one");
            }
            AppConfigProvider provider;
            if (client != null) {
                provider = new AppConfigProvider(cacheManager, client);
            } else {
                provider = new AppConfigProvider(cacheManager);
            }
            if (transformationManager != null) {
                provider.setTransformationManager(transformationManager);
            }
            return provider;
        }

        /**
         * Set custom {@link AppConfigDataClient} to pass to the {@link AppConfigProvider}. <br/>
         * Use it if you want to customize the region or any other part of the client.
         *
         * @param client Custom client
         * @return the builder to chain calls (eg. <pre>builder.withClient().build()</pre>)
         */
        public Builder withClient(AppConfigDataClient client) {
            this.client = client;
            return this;
        }

        /**
         * <b>Mandatory</b>. Provide a CacheManager to the {@link AppConfigProvider}
         *
         * @param cacheManager the manager that will handle the cache of parameters
         * @return the builder to chain calls (eg. <pre>builder.withCacheManager().build()</pre>)
         */
        public Builder withCacheManager(CacheManager cacheManager) {
            this.cacheManager = cacheManager;
            return this;
        }

        /**
         * Provide a transformationManager to the {@link AppConfigProvider}
         *
         * @param transformationManager the manager that will handle transformation of parameters
         * @return the builder to chain calls (eg. <pre>builder.withTransformationManager().build()</pre>)
         */
        public Builder withTransformationManager(TransformationManager transformationManager) {
            this.transformationManager = transformationManager;
            return this;
        }
    }
}