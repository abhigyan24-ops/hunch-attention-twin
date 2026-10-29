package com.dhyan.app.sync

import java.io.InputStream

object SyncTestVectorsLoader {
    fun openStream(): InputStream {
        return SyncTestVectorsLoader::class.java.classLoader?.getResourceAsStream("test-vectors.json")
            ?: Thread.currentThread().contextClassLoader?.getResourceAsStream("test-vectors.json")
            ?: SyncTestVectorsLoader::class.java.getResourceAsStream("/test-vectors.json")
            ?: java.io.File("src/test/resources/test-vectors.json").takeIf { it.exists() }?.inputStream()
            ?: java.io.File("${System.getProperty("user.dir")}/src/test/resources/test-vectors.json").takeIf { it.exists() }?.inputStream()
            ?: java.io.File("${System.getProperty("user.dir")}/../contracts/sync/test-vectors.json").takeIf { it.exists() }?.inputStream()
            ?: error("Could not locate test-vectors.json in classpath or filesystem")
    }

    fun readText(): String = openStream().bufferedReader().use { it.readText() }
}
