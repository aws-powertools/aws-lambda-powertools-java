plugins {
    id("io.freefair.aspectj.post-compile-weaving") version "8.2.2"
    kotlin("jvm") version "1.9.10"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.amazonaws:aws-lambda-java-core:1.2.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.13.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.2.2")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.2")
    implementation("org.aspectj:aspectjrt:1.9.7")
    aspect("software.amazon.lambda:powertools-tracing:2.0.0-SNAPSHOT")
    aspect("software.amazon.lambda:powertools-logging-log4j:2.0.0-SNAPSHOT")
    aspect("software.amazon.lambda:powertools-metrics:2.0.0-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

kotlin {
    jvmToolchain(11)
}