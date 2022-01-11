package com.android.systemui.statusbar.policy

import android.app.StatusBarManager
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings.System.SECURE_LOCKSCREEN_QS_DISABLED

import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.util.settings.SystemSettings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@SysUISingleton
class SecureLockscreenQSDisabler @Inject constructor(
    private val context: Context,
    private val commandQueue: CommandQueue,
    private val systemSettings: SystemSettings,
    private val keyguardStateController: KeyguardStateController,
    @Main handler: Handler,
    @Background private val bgScope: CoroutineScope,
) {

    private var disableQSOnSecureLockscreen: Boolean = shouldDisableQS()

    init {
        val settingsObserver = object: ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                disableQSOnSecureLockscreen = shouldDisableQS()
                recomputeDisableFlags()
            }
        }
        bgScope.launch {
            systemSettings.registerContentObserverForUser(
                SECURE_LOCKSCREEN_QS_DISABLED,
                settingsObserver,
                UserHandle.USER_ALL
            )
        }
    }

    fun adjustDisableFlags(state2: Int): Int {
        return if (disableQSOnSecureLockscreen &&
                !keyguardStateController.isUnlocked()) {
            state2 or StatusBarManager.DISABLE2_QUICK_SETTINGS
        } else {
            state2
        }
    }

    private fun shouldDisableQS(): Boolean =
        systemSettings.getIntForUser(
            SECURE_LOCKSCREEN_QS_DISABLED,
            0,
            UserHandle.USER_CURRENT
        ) == 1

    private fun recomputeDisableFlags() {
        commandQueue.recomputeDisableFlags(context.displayId, true /** animate */)
    }
}
