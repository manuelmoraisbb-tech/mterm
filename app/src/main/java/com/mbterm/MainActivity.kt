package com.mbterm

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var terminalView: TerminalView
    private var session: TerminalSession? = null
    private var ctrl = false
    private lateinit var ctrlButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        setContentView(root)

        if (Rootfs.isInstalled(this)) {
            startTerminal()
        } else {
            val msg = TextView(this).apply {
                setTextColor(Color.WHITE); textSize = 16f; setPadding(40, 80, 40, 40)
                text = "A preparar o Alpine (primeira vez)..."
            }
            root.addView(msg)
            thread {
                try {
                    Rootfs.install(this)
                    runOnUiThread { root.removeAllViews(); startTerminal() }
                } catch (e: Exception) {
                    runOnUiThread { msg.text = "Erro: ${e.message}\n\nConfirme que assets/rootfs.bin existe." }
                }
            }
        }
    }

    private fun startTerminal() {
        terminalView = TerminalView(this, null)
        terminalView.setTerminalViewClient(viewClient)
        terminalView.setTypeface(Typeface.MONOSPACE)
        terminalView.setTextSize(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics).toInt())
        root.addView(terminalView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(buildExtraKeys())

        session = newSession()
        terminalView.attachSession(session)
        terminalView.requestFocus()
    }

    private fun newSession(): TerminalSession {
        val nat = applicationInfo.nativeLibraryDir
        val rootfs = Rootfs.dir(this).absolutePath
        val env = arrayOf(
            "LD_LIBRARY_PATH=$nat",
            "PROOT_LOADER=$nat/libproot-loader.so",
            "PROOT_TMP_DIR=${File(cacheDir, "proot-tmp").absolutePath}",
            "PROOT_L2S_DIR=$rootfs/.l2s",
            "HOME=${filesDir.absolutePath}",
            "TERM=xterm-256color"
        )
        val args = arrayListOf(
            "proot", "--link2symlink", "-0", "-r", rootfs,
            "-b", "/dev", "-b", "/proc", "-b", "/sys", "-b", "/sdcard:/sdcard",
            "-w", "/root",
            "/usr/bin/env", "-i", "HOME=/root", "TERM=xterm-256color", "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "/bin/sh", "-l"
        )
        return TerminalSession("$nat/libproot.so", filesDir.absolutePath,
            args.toTypedArray(), env, 2000, sessionClient)
    }

    // ---------- teclas extra (Esc, Tab, Ctrl, setas...) ----------
    private fun buildExtraKeys(): HorizontalScrollView {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun key(label: String, action: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; minWidth = 0; minimumWidth = 0
            setTextColor(Color.WHITE); setBackgroundColor(Color.DKGRAY)
            setPadding(28, 8, 28, 8)
            setOnClickListener { action() }
            row.addView(this, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(3, 3, 3, 3) })
        }
        fun send(s: String) = { session?.write(s); Unit }
        key("ESC", send("\u001b"))
        key("TAB", send("\t"))
        ctrlButton = key("CTRL") {
            ctrl = !ctrl
            ctrlButton.setBackgroundColor(if (ctrl) Color.rgb(0, 120, 200) else Color.DKGRAY)
        }
        key("↑", send("\u001b[A")); key("↓", send("\u001b[B"))
        key("←", send("\u001b[D")); key("→", send("\u001b[C"))
        key("-", send("-")); key("/", send("/")); key("|", send("|"))
        key("~", send("~")); key("^C", send("\u0003")); key("^D", send("\u0004"))
        return HorizontalScrollView(this).apply {
            setBackgroundColor(Color.BLACK); addView(row)
        }
    }

    private fun showKeyboard() {
        terminalView.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(terminalView, 0)
    }

    // ---------- clientes da biblioteca de terminal ----------
    private val sessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) { terminalView.onScreenUpdated() }
        override fun onTitleChanged(changedSession: TerminalSession) {}
        override fun onSessionFinished(finishedSession: TerminalSession) { finish() }
        override fun onCopyTextToClipboard(s: TerminalSession, text: String?) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("term", text ?: ""))
        }
        override fun onPasteTextFromClipboard(s: TerminalSession?) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()
            if (!t.isNullOrEmpty()) session?.emulator?.paste(t)
        }
        override fun onBell(s: TerminalSession) {}
        override fun onColorsChanged(s: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        fun setTerminalShellPid(pid: Int) {}
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String?, message: String?) {}
        override fun logWarn(tag: String?, message: String?) {}
        override fun logInfo(tag: String?, message: String?) {}
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}
    }

    private val viewClient = object : TerminalViewClient {
        override fun onScale(scale: Float): Float = 1f
        override fun onSingleTapUp(e: MotionEvent?) { showKeyboard() }
        override fun shouldBackButtonBeMappedToEscape() = false
        override fun shouldEnforceCharBasedInput() = true
        override fun shouldUseCtrlSpaceWorkaround() = false
        override fun isTerminalViewSelected() = true
        override fun copyModeChanged(copyMode: Boolean) {}
        override fun onKeyDown(keyCode: Int, e: KeyEvent?, s: TerminalSession?) = false
        override fun onKeyUp(keyCode: Int, e: KeyEvent?) = false
        override fun onLongPress(event: MotionEvent?) = false
        override fun readControlKey() = ctrl
        override fun readAltKey() = false
        override fun readShiftKey() = false
        override fun readFnKey() = false
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, s: TerminalSession?): Boolean {
            if (ctrl) { ctrl = false; ctrlButton.setBackgroundColor(Color.DKGRAY) }
            return false
        }
        override fun onEmulatorSet() {}
        override fun logError(tag: String?, message: String?) {}
        override fun logWarn(tag: String?, message: String?) {}
        override fun logInfo(tag: String?, message: String?) {}
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}
    }

    override fun onDestroy() {
        session?.finishIfRunning()
        super.onDestroy()
    }
}
