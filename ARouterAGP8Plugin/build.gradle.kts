plugins {
    `java-gradle-plugin`
    id("org.jetbrains.kotlin.jvm")
    id("com.gradle.plugin-publish") version "1.2.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

gradlePlugin {
    plugins {
        register("ARouterAGP8Plugin") {
            id = "com.zhouz.plugin.ARouterAGP8Plugin"
            implementationClass = "com.zhouz.plugin.ARouterAGP8Plugin"
        }
    }
}

group = "com.zhouz.plugin"
version = "1.0.0"


publishing {
    publications {
        create<MavenPublication>("ARouterAGP8Plugin") {
            groupId = "com.zhouz.plugin"
            version = "1.0.0"
        }
    }
    repositories {
        maven(uri("$rootDir/repo/"))
    }
}


dependencies {
    implementation(gradleKotlinDsl())
    compileOnly(libs.gradle.api)
    compileOnly(libs.asm.commons)
    compileOnly(libs.asm.tree)
    //implementation(files("libs/javassist-3.26.0-GA.jar"))
}