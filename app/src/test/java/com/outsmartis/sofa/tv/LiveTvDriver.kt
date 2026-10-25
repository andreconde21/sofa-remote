package com.outsmartis.sofa.tv

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Manual driver against a real TV (or the Google TV emulator), skipped unless SOFA_TV_DIR is set.
 * Append commands to $SOFA_TV_DIR/cmd.txt: `pair`, `code ABC123`, `connect`, `key 23`,
 * `text hello`, `launch https://…`, `quit`. Everything is logged to $SOFA_TV_DIR/log.txt.
 */
class LiveTvDriver {
    @Test fun drive() {
        val dir = System.getenv("SOFA_TV_DIR")?.let(::File)
        assumeTrue(dir != null)
        dir!!
        val host = System.getenv("SOFA_TV_HOST") ?: "127.0.0.1"
        val pairPort = System.getenv("SOFA_PAIR_PORT")?.toInt() ?: PairingSession.PAIRING_PORT
        val remotePort = System.getenv("SOFA_REMOTE_PORT")?.toInt() ?: RemoteSession.REMOTE_PORT
        val logFile = File(dir, "log.txt")
        fun log(s: String) = synchronized(logFile) { logFile.appendText("${System.currentTimeMillis() % 100000} $s\n") }
        val identity = Identity.loadOrCreate(File(dir, "client.p12"), "Sofa test")
        val cmd = File(dir, "cmd.txt").apply { writeText("") }
        var pairing: PairingSession? = null
        var remote: RemoteSession? = null
        var read = 0
        var lastState = ""
        val deadline = System.currentTimeMillis() + 15 * 60_000
        log("ready")
        while (System.currentTimeMillis() < deadline) {
            val lines = cmd.readLines()
            if (lines.size <= read) {
                Thread.sleep(200)
                val st = remote?.state?.value?.toString().orEmpty()
                if (st != lastState) { lastState = st; log("state $st") }
                continue
            }
            for (line in lines.drop(read)) {
                read++
                val (op, arg) = line.trim().split(" ", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                log("> $line")
                try {
                    when (op) {
                        "pair" -> { pairing = PairingSession(identity, host, pairPort).also { it.start("Sofa test") }; log("pairing started") }
                        "code" -> { pairing!!.finish(arg); pairing!!.close(); log("paired") }
                        "connect" -> { remote = RemoteSession(identity, host, remotePort, log = { log(it) }).also { it.start() } }
                        "key" -> remote!!.key(arg.toInt())
                        "long" -> { remote!!.key(arg.toInt(), Keys.START_LONG); Thread.sleep(800); remote!!.key(arg.toInt(), Keys.END_LONG) }
                        "text" -> remote!!.insert(arg)
                        "type" -> arg.split("|").let { remote!!.type(it[0], it[1]) }
                        "launch" -> remote!!.launch(arg)
                        "quit" -> { remote?.close(); log("bye"); return }
                    }
                } catch (e: Exception) {
                    log("error $e")
                }
            }
        }
    }
}
