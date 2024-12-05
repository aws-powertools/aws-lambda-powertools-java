build-HelloWorldFunction:
	mvn clean package -P native-image
	chmod +x target/hello-world
	cp target/hello-world $(ARTIFACTS_DIR) # (ARTIFACTS_DIR --> https://github.com/aws/aws-lambda-builders/blob/develop/aws_lambda_builders/workflows/custom_make/DESIGN.md#implementation)
	chmod +x src/main/config/bootstrap
	cp src/main/config/bootstrap $(ARTIFACTS_DIR)
