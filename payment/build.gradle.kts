plugins {
    `java-library`
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common"))

    runtimeOnly("org.postgresql:postgresql")

    testRuntimeOnly("com.h2database:h2")
}
