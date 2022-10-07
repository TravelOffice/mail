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
	maven("https://jitpack.io")
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.security:spring-security-jwt:1.1.1.RELEASE")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("com.amazonaws:aws-java-sdk-bom:1.12.307")
	implementation("com.amazonaws:aws-java-sdk-dynamodb:1.12.307")
	implementation("software.amazon.awssdk:regions:2.17.276")
	implementation("software.amazon.awssdk:secretsmanager:2.17.276")
	implementation("software.amazon.awssdk:sqs:2.17.276")
	implementation("com.google.code.gson:gson:2.9.1")
	implementation("org.jsoup:jsoup:1.15.3")
	implementation("com.github.TravelOffice:email_mime_parser:v1.0.6")
	implementation("javax.xml.bind:jaxb-api:2.4.0-b180830.0359")
	implementation("com.squareup.okhttp3:okhttp:4.10.0")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
	implementation("org.jsoup:jsoup:1.15.3")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict", "-opt-in=kotlin.RequiresOptIn")
		jvmTarget = "17"
	}
}
