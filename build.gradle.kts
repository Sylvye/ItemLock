plugins {
    java
}

group = "com.bountysmp"
version = "0.1.4"

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.120-stable")

    testImplementation("io.papermc.paper:paper-api:26.2.build.120-stable")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.2:4.116.1") {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.junit.platform")
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.test {
    useJUnitPlatform()
}
