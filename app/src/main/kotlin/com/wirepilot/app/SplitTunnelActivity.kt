package com.wirepilot.app

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.wirepilot.app.control.AppEntry
import com.wirepilot.app.control.AppListFilter
import com.wirepilot.app.control.HomeController
import com.wirepilot.app.control.SplitTunnelMode
import com.wirepilot.app.ui.SystemBarInsets

class SplitTunnelActivity : AppCompatActivity() {
  private lateinit var controller: HomeController
  private lateinit var tunnelName: String
  private lateinit var modeGroup: RadioGroup
  private lateinit var appControls: LinearLayout
  private lateinit var searchInput: TextInputEditText
  private lateinit var showSystemSwitch: MaterialSwitch
  private lateinit var adapter: SplitAppAdapter
  private val inventory = mutableListOf<ListedApp>()
  private val selected = mutableSetOf<String>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    tunnelName = intent.getStringExtra(EXTRA_TUNNEL_NAME).orEmpty()
    if (tunnelName.isBlank()) {
      finish()
      return
    }
    setContentView(R.layout.activity_split_tunnel)
    SystemBarInsets.apply(findViewById(R.id.screenRoot))
    controller = (application as WirePilotApp).container.homeController
    val toolbar = findViewById<MaterialToolbar>(R.id.splitToolbar)
    setSupportActionBar(toolbar)
    toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    title = getString(R.string.split_tunnel_for, tunnelName)
    modeGroup = findViewById(R.id.splitModeGroup)
    appControls = findViewById(R.id.splitAppControls)
    searchInput = findViewById(R.id.splitSearchInput)
    showSystemSwitch = findViewById(R.id.showSystemAppsSwitch)
    adapter = SplitAppAdapter(selected)
    val list = findViewById<RecyclerView>(R.id.splitAppList)
    list.layoutManager = LinearLayoutManager(this)
    list.adapter = adapter
    loadInventory()
    bindInitial()
    modeGroup.setOnCheckedChangeListener { _, _ -> refreshList() }
    showSystemSwitch.setOnCheckedChangeListener { _, _ -> refreshList() }
    searchInput.addTextChangedListener(QueryWatcher())
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.split_tunnel_toolbar, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.action_save_split -> {
        save()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun bindInitial() {
    val settings = controller.splitSettings(tunnelName)
    selected.clear()
    selected.addAll(settings.packages)
    modeGroup.check(
      when (settings.mode) {
        SplitTunnelMode.ALL_APPS -> R.id.splitAllApps
        SplitTunnelMode.EXCLUDE_APPS -> R.id.splitExcludeApps
        SplitTunnelMode.INCLUDE_APPS -> R.id.splitIncludeApps
      },
    )
    refreshList()
  }

  private fun loadInventory() {
    val packageManager = packageManager
    inventory.clear()
    packageManager.getInstalledApplications(0).forEach { info ->
      val launchable = packageManager.getLaunchIntentForPackage(info.packageName) != null
      val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
      inventory += ListedApp(
        entry = AppEntry(
          packageName = info.packageName,
          label = packageManager.getApplicationLabel(info).toString(),
          system = system,
          launchable = launchable,
        ),
        icon = runCatching { packageManager.getApplicationIcon(info) }.getOrNull(),
      )
    }
  }

  private fun refreshList() {
    val allApps = currentMode() == SplitTunnelMode.ALL_APPS
    appControls.isVisible = !allApps
    if (allApps) {
      return
    }
    val visible = AppListFilter.visible(
      apps = inventory.map { listed -> listed.entry },
      query = searchInput.text?.toString().orEmpty(),
      showSystem = showSystemSwitch.isChecked,
    )
    val byName = inventory.associateBy { listed -> listed.entry.packageName }
    adapter.submit(visible.mapNotNull { entry -> byName[entry.packageName] })
  }

  private fun save() {
    val mode = currentMode()
    val packages = if (mode == SplitTunnelMode.ALL_APPS) emptySet() else selected.toSet()
    controller.setSplitTunnel(mode, packages, tunnelName)
    Toast.makeText(this, R.string.split_saved, Toast.LENGTH_SHORT).show()
    finish()
  }

  private fun currentMode(): SplitTunnelMode {
    return when (modeGroup.checkedRadioButtonId) {
      R.id.splitExcludeApps -> SplitTunnelMode.EXCLUDE_APPS
      R.id.splitIncludeApps -> SplitTunnelMode.INCLUDE_APPS
      else -> SplitTunnelMode.ALL_APPS
    }
  }

  private inner class QueryWatcher : TextWatcher {
    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
    override fun afterTextChanged(text: Editable?) {
      refreshList()
    }
  }

  companion object {
    const val EXTRA_TUNNEL_NAME = "tunnel_name"

    fun intent(context: Context, tunnelName: String): Intent {
      return Intent(context, SplitTunnelActivity::class.java).putExtra(EXTRA_TUNNEL_NAME, tunnelName)
    }
  }
}

private data class ListedApp(
  val entry: AppEntry,
  val icon: android.graphics.drawable.Drawable?,
)

private class SplitAppAdapter(
  private val selected: MutableSet<String>,
) : RecyclerView.Adapter<SplitAppAdapter.Holder>() {
  private val items = mutableListOf<ListedApp>()

  fun submit(next: List<ListedApp>) {
    items.clear()
    items.addAll(next)
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_split_app, parent, false)
    return Holder(view)
  }

  override fun onBindViewHolder(holder: Holder, position: Int) {
    val item = items[position]
    holder.label.text = item.entry.label
    holder.packageName.text = item.entry.packageName
    holder.icon.setImageDrawable(item.icon)
    holder.checked.setOnCheckedChangeListener(null)
    holder.checked.isChecked = item.entry.packageName in selected
    holder.checked.setOnCheckedChangeListener { _, checked ->
      if (checked) {
        selected += item.entry.packageName
      } else {
        selected -= item.entry.packageName
      }
    }
    holder.itemView.setOnClickListener { holder.checked.toggle() }
  }

  override fun getItemCount(): Int = items.size

  class Holder(view: View) : RecyclerView.ViewHolder(view) {
    val icon: ImageView = view.findViewById(R.id.appIcon)
    val label: TextView = view.findViewById(R.id.appLabel)
    val packageName: TextView = view.findViewById(R.id.appPackage)
    val checked: MaterialCheckBox = view.findViewById(R.id.appChecked)
  }
}
