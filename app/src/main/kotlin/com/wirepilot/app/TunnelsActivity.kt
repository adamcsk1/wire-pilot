package com.wirepilot.app

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.wirepilot.app.control.HomeController
import com.wirepilot.app.data.SplitTunnelMode
import com.wirepilot.app.control.TunnelRow
import com.wirepilot.app.platform.ConfigSplitMerger
import com.wirepilot.app.platform.ConfigZipIO
import com.wirepilot.app.ui.SystemBarInsets

class TunnelsActivity : AppCompatActivity() {
  private lateinit var controller: HomeController
  private lateinit var tunnelList: LinearLayout
  private lateinit var emptyTunnels: TextView
  private lateinit var exportTunnelButton: MaterialButton
  private val onTunnelSettled = {
    if (!isDestroyed) {
      refreshUi()
    }
  }

  private val importLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      importFrom(uri)
    }
  }

  private val exportLauncher = registerForActivityResult(
    ActivityResultContracts.CreateDocument("application/zip"),
  ) { uri ->
    if (uri != null) {
      exportTo(uri)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_tunnels)
    SystemBarInsets.apply(findViewById(R.id.screenRoot))
    controller = (application as WirePilotApp).container.homeController
    tunnelList = findViewById(R.id.tunnelList)
    emptyTunnels = findViewById(R.id.emptyTunnels)
    exportTunnelButton = findViewById(R.id.exportTunnelButton)
    findViewById<MaterialToolbar>(R.id.tunnelsToolbar).setNavigationOnClickListener {
      onBackPressedDispatcher.onBackPressed()
    }
    findViewById<MaterialButton>(R.id.addTunnelButton).setOnClickListener {
      startActivity(TunnelEditActivity.intent(this))
    }
    findViewById<MaterialButton>(R.id.importTunnelButton).setOnClickListener {
      importLauncher.launch(arrayOf("application/zip", "text/plain"))
    }
    exportTunnelButton.setOnClickListener { confirmExport() }
  }

  override fun onStart() {
    super.onStart()
    (application as WirePilotApp).container.tunnel.addSettledListener(onTunnelSettled)
  }

  override fun onResume() {
    super.onResume()
    refreshUi()
  }

  override fun onStop() {
    (application as WirePilotApp).container.tunnel.removeSettledListener(onTunnelSettled)
    super.onStop()
  }

  private fun refreshUi() {
    val state = controller.viewState()
    emptyTunnels.isVisible = state.tunnelRows.isEmpty()
    exportTunnelButton.isEnabled = state.tunnelRows.isNotEmpty()
    tunnelList.removeAllViews()
    state.tunnelRows.forEach { row ->
      tunnelList.addView(bindRow(row))
    }
  }

  private fun bindRow(row: TunnelRow): android.view.View {
    val item = layoutInflater.inflate(R.layout.item_tunnel, tunnelList, false)
    item.findViewById<TextView>(R.id.tunnelName).text = row.name
    item.findViewById<TextView>(R.id.tunnelStatus).text = statusText(row)
    item.setOnClickListener {
      startActivity(TunnelEditActivity.intent(this, row.name))
    }
    item.findViewById<MaterialButton>(R.id.tunnelMoreButton).setOnClickListener {
      showRowMenu(row)
    }
    return item
  }

  private fun statusText(row: TunnelRow): String {
    val base = when {
      row.selected && row.up -> getString(R.string.tunnel_status_default_connected)
      row.selected -> getString(R.string.tunnel_status_default_off)
      row.up -> getString(R.string.tunnel_status_connected)
      else -> getString(R.string.tunnel_status_off)
    }
    val extras = buildList {
      when (row.splitMode) {
        SplitTunnelMode.EXCLUDE_APPS -> add(getString(R.string.tunnel_status_exclude_apps, row.splitAppCount))
        SplitTunnelMode.INCLUDE_APPS -> add(getString(R.string.tunnel_status_include_apps, row.splitAppCount))
        SplitTunnelMode.ALL_APPS -> Unit
      }
      if (row.mobile) {
        add(getString(R.string.tunnel_status_mobile))
      }
      if (row.excludedSsidCount > 0) {
        add(getString(R.string.tunnel_status_skip_ssids, row.excludedSsidCount))
      }
    }
    return if (extras.isEmpty()) base else "$base · ${extras.joinToString(" · ")}"
  }

  private fun showRowMenu(row: TunnelRow) {
    val items = buildList {
      if (!row.selected) {
        add(getString(R.string.set_default_tunnel))
      }
      add(getString(R.string.split_tunnel))
      add(getString(R.string.skip_networks))
      add(getString(R.string.delete_tunnel))
    }
    AlertDialog.Builder(this)
      .setTitle(row.name)
      .setItems(items.toTypedArray()) { _, which ->
        val label = items[which]
        when (label) {
          getString(R.string.set_default_tunnel) -> {
            controller.selectImportedTunnel(row.name)
            refreshUi()
          }
          getString(R.string.split_tunnel) ->
            startActivity(SplitTunnelActivity.intent(this, row.name))
          getString(R.string.skip_networks) ->
            startActivity(TunnelNetworksActivity.intent(this, row.name))
          getString(R.string.delete_tunnel) -> confirmDelete(row.name)
        }
      }
      .show()
  }

  private fun confirmDelete(name: String) {
    AlertDialog.Builder(this)
      .setTitle(getString(R.string.delete_tunnel_title, name))
      .setMessage(R.string.delete_tunnel_message)
      .setPositiveButton(R.string.delete_tunnel) { _, _ ->
        controller.deleteImportedTunnel(name)
        refreshUi()
      }
      .setNegativeButton(R.string.cancel, null)
      .show()
  }

  private fun confirmExport() {
    AlertDialog.Builder(this)
      .setTitle(R.string.export_tunnels)
      .setMessage(R.string.export_contains_keys)
      .setPositiveButton(R.string.export_tunnels) { _, _ ->
        exportLauncher.launch("wirepilot-PRIVATE-KEYS.zip")
      }
      .setNegativeButton(R.string.cancel, null)
      .show()
  }

  private fun importFrom(uri: Uri) {
    val container = (application as WirePilotApp).container
    val displayName = displayName(uri)
    val isZip = displayName.endsWith(".zip", ignoreCase = true)
    val names = runCatching {
      contentResolver.openInputStream(uri)?.use { input ->
        ConfigZipIO.peekNames(input, isZip, displayName)
      }.orEmpty()
    }.getOrDefault(emptyList())
    if (names.isEmpty()) {
      Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show()
      return
    }
    val overlap = names.filter { it in container.catalog.names() }
    if (overlap.isEmpty()) {
      writeImported(uri, displayName, isZip)
      return
    }
    AlertDialog.Builder(this)
      .setTitle(R.string.import_overwrite_title)
      .setMessage(getString(R.string.import_overwrite_message, overlap.joinToString(", ")))
      .setPositiveButton(R.string.import_overwrite) { _, _ ->
        writeImported(uri, displayName, isZip)
      }
      .setNegativeButton(R.string.cancel, null)
      .show()
  }

  private fun writeImported(uri: Uri, displayName: String, isZip: Boolean) {
    val container = (application as WirePilotApp).container
    val imported = runCatching {
      contentResolver.openInputStream(uri)?.use { input ->
        ConfigZipIO.importAll(input, isZip, displayName, container.catalog, container.splitTunnels)
      }.orEmpty()
    }.getOrDefault(emptyList())
    if (imported.isEmpty()) {
      Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show()
      return
    }
    controller.reloadImported(imported)
    Toast.makeText(this, getString(R.string.import_success, imported.size), Toast.LENGTH_SHORT).show()
    refreshUi()
  }

  private fun exportTo(uri: Uri) {
    val container = (application as WirePilotApp).container
    val names = container.catalog.names()
    val ok = runCatching {
      val output = contentResolver.openOutputStream(uri) ?: return@runCatching false
      output.use { stream ->
        ConfigZipIO.exportZip(stream, container.catalog, names) { name, conf ->
          val parsed = ConfigZipIO.parseOrNull(conf) ?: return@exportZip conf
          ConfigSplitMerger.toConf(ConfigSplitMerger.merge(parsed, container.splitTunnels.read(name)))
        }
      }
      true
    }.getOrDefault(false)
    Toast.makeText(this, if (ok) R.string.export_success else R.string.export_failed, Toast.LENGTH_SHORT).show()
  }

  private fun displayName(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        return cursor.getString(0).orEmpty()
      }
    }
    return uri.lastPathSegment.orEmpty()
  }
}
