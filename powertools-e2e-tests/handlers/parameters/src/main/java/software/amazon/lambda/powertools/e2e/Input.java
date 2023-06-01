package software.amazon.lambda.powertools.e2e;

import java.util.Map;

public class Input {

    private String app;
    private String environment;
    private String key;

    public Input() {

    }

    public void setApp(String app) {
        this.app = app;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getApp() {
        return app;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getKey() {
        return key;
    }

}
