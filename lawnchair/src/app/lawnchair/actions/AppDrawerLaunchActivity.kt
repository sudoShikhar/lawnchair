package app.lawnchair.actions

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Dummy activity that acts as an App Drawer icon in the launcher.
 * Clicking this simply fires the toggle intent to open the drawer.
 */
class AppDrawerLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            val launcher = com.android.launcher3.Launcher.ACTIVITY_TRACKER.getCreatedActivity<com.android.launcher3.Launcher>()
            if (launcher != null) {
                launcher.stateManager.goToState(com.android.launcher3.LauncherState.ALL_APPS)
            } else {
                val homeIntent = Intent("launcher.intent_action_all_apps_toggle")
                homeIntent.addCategory(Intent.CATEGORY_HOME)
                homeIntent.setPackage(packageName)
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(homeIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        finish()
        overridePendingTransition(0, 0)
    }
}
