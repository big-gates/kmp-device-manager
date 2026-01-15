import java.util.Properties
import java.io.FileInputStream

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.androidLint) apply false
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

// 로드한 속성을 모든 서브 모듈(permission, device 등)에서 사용할 수 있게 설정
allprojects {
    localProperties.forEach { (key, value) ->
        // 이미 설정된 값이 없다면 local.properties 값을 사용
        if (!project.hasProperty(key.toString())) {
            project.extensions.extraProperties.set(key.toString(), value)
        }
    }
}