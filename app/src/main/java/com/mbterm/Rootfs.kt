package com.mbterm

import android.content.Context
import java.io.File

/** Extrai o Alpine (assets/rootfs.bin = alpine-minirootfs aarch64 .tar.gz) para files/alpine. */
object Rootfs {
    fun dir(ctx: Context) = File(ctx.filesDir, "alpine")

    fun isInstalled(ctx: Context) = File(dir(ctx), "bin/busybox").exists()

    fun install(ctx: Context) {
        val tmp = File(ctx.cacheDir, "rootfs.tar.gz")
        ctx.assets.open("rootfs.bin").use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        dir(ctx).mkdirs()
        val p = ProcessBuilder("/system/bin/tar", "xzf", tmp.absolutePath, "-C", dir(ctx).absolutePath)
            .redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText() // esvazia a saída; avisos de mknod/chown são normais
        p.waitFor()
        tmp.delete()
        check(isInstalled(ctx)) { "Falha ao extrair o Alpine (bin/busybox não existe)" }
        File(dir(ctx), "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
        File(dir(ctx), ".l2s").mkdirs()
        File(ctx.cacheDir, "proot-tmp").mkdirs()
    }
}
