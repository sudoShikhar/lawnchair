package app.lawnchair.allapps

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.lawnchair.data.folder.model.FolderOrderUtils
import app.lawnchair.data.folder.model.FolderViewModel
import app.lawnchair.launcher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.util.categorizeAppsWithSystemAndGoogle
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem
import com.android.launcher3.allapps.PrivateProfileManager
import com.android.launcher3.allapps.WorkProfileManager
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.views.ActivityContext
import com.patrykmichalik.opto.core.onEach
import java.util.function.Predicate

@Suppress("SYNTHETIC_PROPERTY_WITHOUT_JAVA_ORIGIN")
class LawnchairAlphabeticalAppsList<T>(
    private val context: T,
    private val appsStore: AllAppsStore<T>,
    workProfileManager: WorkProfileManager?,
    privateProfileManager: PrivateProfileManager?,
) : AlphabeticalAppsList<T>(context, appsStore, workProfileManager, privateProfileManager),
    OnIDPChangeListener
    where T : Context, T : ActivityContext {

    private var hiddenApps: Set<String> = setOf()
    private val prefs2 = PreferenceManager2.getInstance(context)
    private val prefs = PreferenceManager.getInstance(context)

    private val viewModel: FolderViewModel by (context as ComponentActivity).viewModels()
    private var folderList = mutableListOf<FolderInfo>()
    private val filteredList = mutableListOf<AppInfo>()

    private val folderOrder = FolderOrderUtils.stringToIntList(prefs.drawerListOrder.get())

    init {
        context.launcher.deviceProfile.inv.addOnChangeListener(this)
        try {
            prefs2.hiddenApps.onEach(launchIn = context.launcher.lifecycleScope) {
                hiddenApps = it
                onAppsUpdated()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize hidden apps", t)
        }
        observeFolders()
    }

    private fun observeFolders() {
        viewModel.foldersLiveData.observe(context as LifecycleOwner) { folders ->
            folderList = folders
                .sortedBy { folderOrder.indexOf(it.id) }
                .toMutableList()
            updateAdapterItems()
        }
    }

    override fun updateItemFilter(itemFilter: Predicate<ItemInfo>?) {
        mItemFilter = Predicate { info ->
            require(info is AppInfo) { "`info` must be an instance of `AppInfo`." }
            val componentKey = info.toComponentKey().toString()
            (itemFilter?.test(info) != false) && !hiddenApps.contains(componentKey)
        }
        onAppsUpdated()
    }

    override fun addAppsWithSections(appList: List<AppInfo?>?, startPosition: Int): Int {
        // For the classic A–Z drawer layout, rely entirely on the base implementation to:
        // - sort apps alphabetically
        // - group them by sectionName
        // - feed fast-scroll sections
        // Hidden apps filtering is already handled via updateItemFilter above.
        return super.addAppsWithSections(appList, startPosition)
    }

    override fun onIdpChanged(modelPropertiesChanged: Boolean) {
        onAppsUpdated()
    }
}
