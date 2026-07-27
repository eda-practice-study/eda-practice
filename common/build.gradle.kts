plugins {
    `java-library`
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework:spring-web")
    api("org.springframework.boot:spring-boot-starter-kafka")

    compileOnly("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.kafka:spring-kafka-test")
}
