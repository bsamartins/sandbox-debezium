plugins {
    kotlin("jvm") version "1.6.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.13.0"))
    implementation(platform("org.apache.logging.log4j:log4j-bom:2.15.0"))
    implementation(platform("org.apache.logging.log4j:log4j-api-kotlin-parent:1.1.0"))

    implementation("org.apache.kafka:kafka-clients:3.0.0")
    implementation("org.apache.kafka:kafka-streams:3.0.0")

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.apache.logging.log4j:log4j-api-kotlin")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl")
}
