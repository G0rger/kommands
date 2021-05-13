plugins {
    java
    kotlin("jvm") version "1.4.32"
    `maven-publish`
}

repositories {
    mavenCentral()
    jcenter()
    maven("https://dl.bintray.com/kordlib/Kord")
}

dependencies {
    implementation(project(":core"))
    implementation(kotlin("stdlib"))

    implementation("com.gitlab.kordlib.kord", "kord-core", "0.6.10")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = group as String?
            artifactId = project.name
            version = project.version as String?

            from(components["java"])
        }
    }
}
