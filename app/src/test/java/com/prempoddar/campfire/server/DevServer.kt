package com.prempoddar.campfire.server

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs a real Campfire on this computer for working on the web client:
 * `CAMPFIRE_DEV=1 ./gradlew testDebugUnitTest --tests '*DevServer*'`, then open
 * http://localhost:8080. The client is served from src/main/resources/web, so edits show on reload.
 * Skipped in normal test runs.
 */
class DevServer {
    @Test fun run() {
        assumeTrue(System.getenv("CAMPFIRE_DEV") != null)
        val web = File("src/main/resources/web").absoluteFile
        val server = CampfireServer(8080, "Dev Campfire", File("build/dev-files"), webDir = web)
        server.start()
        println("Campfire dev server on http://localhost:8080  host token: ${server.hub.hostToken}")
        File("build/dev-host-token").writeText(server.hub.hostToken)
        Thread.sleep(Long.MAX_VALUE)
    }
}
