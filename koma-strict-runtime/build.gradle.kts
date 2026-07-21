plugins {
    id("koma.strict.kmp")
    id("koma.strict.publish")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Stay が koma.core.State を実装し、nextState 系の KClass<out State> 境界でも
            // 公開 API に露出するため api 依存 (backend 中立制約は撤廃済み — design doc 参照)
            api(libs.koma.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
