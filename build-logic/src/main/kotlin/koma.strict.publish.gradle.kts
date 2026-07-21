plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral()

    coordinates(group.toString(), project.path.removePrefix(":").replace(":", "-"), version.toString())

    pom {
        // 3 モジュール (runtime / ksp / ksp-shared) を Maven Central 上で区別できるよう artifact 名にする
        name = project.name
        description = "koma.kt をベースとした 状態管理 framework"
        url = "https://github.com/TBSten/koma-strict"

        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/licenses/MIT"
            }
        }

        developers {
            developer {
                id = "TBSten"
                name = "TBSten"
                email = "programmingcafeteria@gmail.com"
            }
        }

        scm {
            url = "https://github.com/TBSten/koma-strict.git"
        }
    }
    // ローカル publish 時のみ署名をスキップ。CI (Maven Central publish) は
    // ORG_GRADLE_PROJECT_signingInMemoryKey* を注入する設計なので常に署名する (cream イディオム)
    if (!(gradle.startParameter.taskNames.contains("publishToMavenLocal"))) signAllPublications()
}
