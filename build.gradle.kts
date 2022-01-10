plugins {
    java
    `java-library`
}

group = "com.exactpro.reactive"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.reactivestreams:reactive-streams:1.0.3")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testImplementation("org.reactivestreams:reactive-streams-tck:1.0.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.support:testng-engine:1.0.1")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}