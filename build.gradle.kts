plugins {
    scala
    application
}

repositories {
    mavenCentral()
}

scala {
    scalaVersion.set("3.8.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("com.lihaoyi:upickle_3:3.1.3")
    implementation("com.lihaoyi:scalatags_3:0.12.0")
    implementation("com.lihaoyi:os-lib_3:0.9.3")
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    testImplementation("org.scalatest:scalatest_3:3.2.19")
    testRuntimeOnly("org.scalatestplus:junit-5-10_3:3.2.19.1")
}

tasks.test {
    useJUnitPlatform {
        includeEngines("scalatest")
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }
}

application {
    mainClass.set("Main")
}
