import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "2.7.3"
	id("io.spring.dependency-management") version "1.0.13.RELEASE"
	kotlin("jvm") version "1.6.21"
	kotlin("plugin.spring") version "1.6.21"
}

group = "traveloffice"
version = "0.0.1"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("com.amazonaws:aws-java-sdk-bom:1.12.296")
	implementation("com.amazonaws:aws-java-sdk-dynamodb:1.12.296")
	implementation("software.amazon.awssdk:regions:2.17.267")
	implementation("software.amazon.awssdk:secretsmanager:2.17.267")
	implementation("software.amazon.awssdk:sqs:2.17.267")
	implementation("com.google.code.gson:gson:2.9.1")
	implementation("com.sun.mail:javax.mail:1.6.2")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "17"
	}
}
