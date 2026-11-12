package com.roottap.clicker.core

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.roottap.clicker.MainActivity
import com.roottap.clicker.R

/**
 * Home screen shortcuts, one per saved plan: tapping it applies the plan and starts clicking
 * straight away, without opening the app first.
 */
object Shortcuts {

    const val ActionRunPlan = "com.roottap.clicker.RUN_PLAN"
    const val ExtraPlanId = "plan_id"

    fun runPlanIntent(context: Context, planId: Long): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ActionRunPlan)
            .putExtra(ExtraPlanId, planId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** Asks the launcher to pin a shortcut; returns false when the launcher has no pin support. */
    fun pinPlan(context: Context, plan: ClickPlan): Boolean = runCatching {
        val shortcut = ShortcutInfoCompat.Builder(context, "plan-${plan.id}")
            .setShortLabel(plan.name.ifBlank { "方案" })
            .setLongLabel(plan.name.ifBlank { "运行方案" })
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(runPlanIntent(context, plan.id))
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }.isSuccess

    fun planIdOf(intent: Intent?): Long =
        if (intent?.action == ActionRunPlan) intent.getLongExtra(ExtraPlanId, 0L) else 0L
}
