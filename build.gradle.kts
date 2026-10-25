@file:Suppress("VulnerableLibrariesLocal", "UnstableApiUsage")

plugins {
    java
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

group = "io.github.balugaq"
version = "1.0.0"

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.withType<Javadoc>().configureEach {
    // 出错（含 doclint 之外的警告）也不让 javadoc 任务失败，避免阻断构建/发布
    isFailOnError = false
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        charSet = "UTF-8"
        addStringOption("Xdoclint", "none")
    }
}

tasks.withType<JavaExec>().configureEach {
    systemProperty("file.encoding", "UTF-8")
    systemProperty("sun.stdout.encoding", "UTF-8")
    systemProperty("sun.stderr.encoding", "UTF-8")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // 运行时最低 1.17：主代码只对 1.17.1 API 编译
    compileOnly(libs.paper.api)

    // 测试基建：MockBukkit v1.21 的 Paper-Version 是 1.21.11，测试 classpath 必须对齐
    testImplementation(libs.paper.api.test)
    testImplementation(libs.mockbukkit.v121)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        // MockBukkit 对未实现的 API 抛 AssumptionException 会静默跳过测试，
        // 把 skipped 也打出来，避免「绿色构建其实啥都没验」
        events("passed", "skipped", "failed")
    }
}

tasks.jar {
    enabled = false
}

tasks {
    processResources {
        // 只展开 plugin.yml 的 ${version}/${name}；lang/*.yml 保持原样，
        // 避免 Groovy 模板引擎碰到语言文件里的特殊字符
        filesMatching("plugin.yml") {
            expand(
                mapOf(
                    "version" to project.version,
                    "name" to project.name
                )
            )
        }
    }

    shadowJar {
        archiveBaseName.set("NoPrivateEnderPortal")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("")
        exclude("META-INF/*")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        dependsOn(shadowJar)
        val run = file(providers.gradleProperty("server.run.dir").orElse("run"))
        runDirectory.set(run)

        doFirst {
            run.resolve("eula.txt").writeText("eula=true")

            val pl = run.resolve("plugins")
            pl.mkdirs()
            copy {
                from(projectDir.resolve("build/libs")) {
                    include("${name}-${version}.jar")
                }
                into(pl)
            }
        }

        jvmArgs(
            "-Dfile.encoding=UTF-8",
            "-Dsun.jnu.encoding=UTF-8",
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005",
            "-Dnet.kyori.adventure.text.warn_when_legacy_formatting_detected=false"
        )
        maxHeapSize = "4G"
        minecraftVersion("1.21.11")
    }
}

// Set default tasks
defaultTasks("clean", "build")
