plugins {
    id("koma.strict.kmp")
    id("koma.strict.publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
