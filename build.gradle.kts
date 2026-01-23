plugins {
    scala
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.scala-lang:scala3-library_3:3.3.1")
    implementation("com.lihaoyi:upickle_3:3.1.3")
    implementation("com.lihaoyi:scalatags_3:0.12.0")
    implementation("com.lihaoyi:os-lib_3:0.9.3")
}

application {
    mainClass.set("MapBuilder")
}
