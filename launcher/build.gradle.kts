// Versões centralizadas aqui. AGP 8.5 exige JDK 17; Kotlin 2.0 traz o
// compilador do Compose como plugin próprio (não há mais composeCompiler version).
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
}
