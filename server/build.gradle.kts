plugins {
    scala
    application
}

val scala3Version = "3.3.1"
val upickleVersion = "3.1.3"

dependencies {
    implementation("org.scala-lang:scala3-library_3:$scala3Version")
    implementation("com.lihaoyi:os-lib_3:0.9.3")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("com.lihaoyi:upickle_3:$upickleVersion")

    testImplementation("org.scalatest:scalatest_3:3.2.19")
    testRuntimeOnly("org.scalatestplus:junit-5-10_3:3.2.19.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("Main")
}

sourceSets {
    main {
        scala {
            srcDir("../shared/src/main/scala")
        }
    }
    test {
        scala {
            srcDir("../shared/src/test/scala")
        }
    }
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
