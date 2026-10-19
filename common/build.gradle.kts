plugins {
    `java-library`
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework:spring-web")
    api("org.springframework.boot:spring-boot-starter-kafka")
    api("org.springframework.boot:spring-boot-starter-jackson")
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("net.javacrumbs.shedlock:shedlock-spring:7.10.1")
    api("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.10.1")

    compileOnly("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.kafka:spring-kafka-test")
}
