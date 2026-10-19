plugins {
    java
    id("org.springframework.boot") version "4.1.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.google.cloud.tools.jib") version "3.4.5" apply false
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    group = "com.eda"
    version = "0.0.1-SNAPSHOT"

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    repositories {
        mavenCentral()
    }

    configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        }
    }

    dependencies {
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

val appPorts = mapOf("order" to "8081", "payment" to "8082", "product" to "8083", "stock" to "8084", "notification" to "8085")

configure(subprojects.filter { it.name in appPorts }) {
    apply(plugin = "com.google.cloud.tools.jib")

    configure<com.google.cloud.tools.jib.gradle.JibExtension> {
        from {
            image = "eclipse-temurin:21-jre-alpine"
            platforms {
                platform {
                    architecture = "arm64"
                    os = "linux"
                }
            }
        }
        to {
            image = "eda-${project.name}"
            tags = setOf("latest")
        }
        container {
            ports = listOf(appPorts.getValue(project.name))
            creationTime.set("USE_CURRENT_TIMESTAMP")
        }
    }
}
