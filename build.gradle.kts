plugins {
    java
}

group = "at.eepy"
version = property("pluginVersion") as String

val javaVersion = (property("javaVersion") as String).toInt()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    compileOnly("net.luckperms:api:${property("luckpermsApiVersion")}")
    compileOnly("me.clip:placeholderapi:${property("placeholderApiVersion")}")

    testImplementation("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to project.version, "apiVersion" to project.property("apiVersion"))
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveFileName = "EepyTab-${project.version}.jar"
    from("LICENSE")
}
