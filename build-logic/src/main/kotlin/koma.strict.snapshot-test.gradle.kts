plugins {
    id("koma.strict.test")
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    forkEvery = 25L

    systemProperty("koma.strict.snapshot.dir", layout.projectDirectory.dir("snapshots").asFile.absolutePath)

    providers.systemProperty("koma.strict.snapshot.update").orNull?.let {
        systemProperty("koma.strict.snapshot.update", it)
    }
}

val snapshotsDir = layout.projectDirectory.dir("snapshots")
if (snapshotsDir.asFile.isDirectory) {
    tasks.withType<Test>().configureEach {
        inputs.dir(snapshotsDir)
            .withPropertyName("komaStrictSnapshotGoldens")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
