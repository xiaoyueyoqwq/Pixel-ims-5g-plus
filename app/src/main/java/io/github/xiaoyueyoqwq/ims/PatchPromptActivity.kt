package io.github.xiaoyueyoqwq.ims

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.xiaoyueyoqwq.ims.system.NotificationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class PatchPromptActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stopService(Intent(this, PromptService::class.java))
        setContentView(R.layout.activity_patch_prompt)
        applySystemBarInsets()
        requestNotificationPermissionIfNeeded()

        val status = findViewById<TextView>(R.id.status)
        val code = findViewById<EditText>(R.id.pairing_code)
        val pair = findViewById<Button>(R.id.action_pair)
        val apply = findViewById<Button>(R.id.action_apply)
        val dismiss = findViewById<Button>(R.id.action_dismiss)

        dismiss.setOnClickListener { finish() }
        pair.setOnClickListener {
            val value = code.text?.toString().orEmpty()
            if (value.isBlank()) {
                status.setText(R.string.prompt_need_code)
                return@setOnClickListener
            }
            runAction(status, pair, apply) { PatchCoordinator.pairAndApply(this, value) }
        }
        apply.setOnClickListener {
            runAction(status, pair, apply) { PatchCoordinator.apply(this) }
        }

        scope.launch {
            status.setText(R.string.prompt_checking)
            val authorized = runCatching { PatchCoordinator.isAuthorized(this@PatchPromptActivity) }
                .getOrDefault(false)
            if (isFinishing) return@launch
            if (authorized) {
                status.setText(R.string.prompt_authorized)
                code.visibility = View.GONE
                pair.visibility = View.GONE
                apply.visibility = View.VISIBLE
            } else {
                status.setText(R.string.prompt_need_pair)
                code.visibility = View.VISIBLE
                pair.visibility = View.VISIBLE
                apply.visibility = View.GONE
            }
        }
    }

    private fun runAction(
        status: TextView,
        pair: Button,
        apply: Button,
        block: suspend () -> Result<Unit>,
    ) {
        pair.isEnabled = false
        apply.isEnabled = false
        status.setText(R.string.prompt_working)
        scope.launch {
            val result = withTimeoutOrNull(45.seconds) { block() }
                ?: Result.failure(IllegalStateException("timed out"))
            NotificationController(this@PatchPromptActivity).let { notifications ->
                notifications.cancelPrompt()
                result.fold(
                    onSuccess = {
                        notifications.showResult(true)
                        finish()
                    },
                    onFailure = {
                        status.text = getString(R.string.prompt_failed, it.message.orEmpty())
                        pair.isEnabled = true
                        apply.isEnabled = true
                    },
                )
            }
        }
    }

    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.root)
        val start = root.paddingStart
        val top = root.paddingTop
        val end = root.paddingEnd
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPaddingRelative(
                start + bars.left,
                top + bars.top,
                end + bars.right,
                bottom + bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
