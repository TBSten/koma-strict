plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral()

    coordinates(group.toString(), project.path.removePrefix(":").replace(":", "-"), version.toString())

    pom {
        name = "koma-strict"
        description = "Kotlin Multiplatform library"
        url = "github url" //todo

        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/licenses/MIT"
            }
        }

        developers {
            developer {
                id = "" //todo github nickname
                name = "" //todo full name
                email = "" //todo email
            }
        }

        scm {
            url = "github url" //todo
        }
    }
    // ローカル publish 時のみ署名をスキップ。CI (Maven Central publish) は
    // ORG_GRADLE_PROJECT_signingInMemoryKey* を注入する設計なので常に署名する (cream イディオム)
    if (!(gradle.startParameter.taskNames.contains("publishToMavenLocal"))) signAllPublications()
}
