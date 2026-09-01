plugins {
  id("java")
  id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.errorpurifier"
version = "1.0.0"

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")

  intellijPlatform {
    intellijIdea("2026.2.1")
    bundledPlugin("com.intellij.java")
  }
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(25))
  }

  sourceCompatibility = JavaVersion.VERSION_25
  targetCompatibility = JavaVersion.VERSION_25
}

intellijPlatform {
  pluginConfiguration {
    ideaVersion {
      sinceBuild.set("262")
      untilBuild.set(provider { null })
    }
  }

  pluginVerification {
    ides {
      current()
    }
  }

  signing {
    certificateChain.set(providers.environmentVariable("CERTIFICATE_CHAIN"))
    privateKey.set(providers.environmentVariable("PRIVATE_KEY"))
    password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
  }

  publishing {
    token.set(providers.environmentVariable("PUBLISH_TOKEN"))
  }
}

tasks {
  test {
    useJUnitPlatform()
  }

  processResources {
    from(rootProject.file("LICENSE")) {
      into("META-INF")
    }
  }
}
