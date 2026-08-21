package com.wirepilot.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.wirepilot.app.control.HomeController
import com.wirepilot.app.control.TunnelSaveResult
import com.wirepilot.app.platform.ConfigDraftIO
import com.wirepilot.app.platform.PeerDraft
import com.wirepilot.app.platform.TunnelDraft
import com.wirepilot.app.ui.SystemBarInsets

class TunnelEditActivity : AppCompatActivity() {
  private lateinit var controller: HomeController
  private lateinit var nameInput: TextInputEditText
  private lateinit var privateKeyInput: TextInputEditText
  private lateinit var publicKeyInput: TextInputEditText
  private lateinit var addressesInput: TextInputEditText
  private lateinit var listenPortInput: TextInputEditText
  private lateinit var dnsInput: TextInputEditText
  private lateinit var mtuInput: TextInputEditText
  private lateinit var peerList: LinearLayout
  private var originalName: String? = null
  private var suppressPublicKey = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    setContentView(R.layout.activity_tunnel_edit)
    SystemBarInsets.apply(findViewById(R.id.screenRoot))
    controller = (application as WirePilotApp).container.homeController
    bindViews()
    val toolbar = findViewById<MaterialToolbar>(R.id.tunnelEditToolbar)
    setSupportActionBar(toolbar)
    toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    originalName = intent.getStringExtra(EXTRA_TUNNEL_NAME)?.takeIf { it.isNotBlank() }
    supportActionBar?.setDisplayShowTitleEnabled(true)
    title = if (originalName == null) {
      getString(R.string.create_tunnel)
    } else {
      getString(R.string.edit_tunnel)
    }
    findViewById<MaterialButton>(R.id.generateKeyButton).setOnClickListener { generateKey() }
    findViewById<MaterialButton>(R.id.addPeerButton).setOnClickListener { addPeer(PeerDraft()) }
    privateKeyInput.addTextChangedListener(PrivateKeyWatcher())
    bindDraft(loadDraft())
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.tunnel_edit_toolbar, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.action_save_tunnel -> {
        save()
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun bindViews() {
    nameInput = findViewById(R.id.tunnelNameInput)
    privateKeyInput = findViewById(R.id.privateKeyInput)
    publicKeyInput = findViewById(R.id.publicKeyInput)
    publicKeyInput.keyListener = null
    publicKeyInput.isCursorVisible = false
    publicKeyInput.setTextIsSelectable(true)
    addressesInput = findViewById(R.id.addressesInput)
    listenPortInput = findViewById(R.id.listenPortInput)
    dnsInput = findViewById(R.id.dnsInput)
    mtuInput = findViewById(R.id.mtuInput)
    peerList = findViewById(R.id.peerList)
  }

  private fun loadDraft(): TunnelDraft {
    val name = originalName ?: return TunnelDraft()
    val conf = (application as WirePilotApp).container.catalog.readConf(name)
    if (conf == null) {
      originalName = null
      title = getString(R.string.create_tunnel)
      return TunnelDraft(name = name)
    }
    return ConfigDraftIO.fromConf(name, conf) ?: TunnelDraft(name = name)
  }

  private fun bindDraft(draft: TunnelDraft) {
    nameInput.setText(draft.name)
    setPrivateKey(draft.privateKey, draft.publicKey)
    addressesInput.setText(draft.addresses)
    listenPortInput.setText(draft.listenPort)
    dnsInput.setText(draft.dns)
    mtuInput.setText(draft.mtu)
    peerList.removeAllViews()
    val peers = draft.peers.ifEmpty { listOf(PeerDraft()) }
    peers.forEach { peer -> addPeer(peer) }
  }

  private fun addPeer(peer: PeerDraft) {
    val item = layoutInflater.inflate(R.layout.item_peer, peerList, false)
    item.findViewById<TextInputEditText>(R.id.peerPublicKeyInput).setText(peer.publicKey)
    item.findViewById<TextInputEditText>(R.id.peerPresharedKeyInput).setText(peer.presharedKey)
    item.findViewById<TextInputEditText>(R.id.peerAllowedIpsInput).setText(peer.allowedIps)
    item.findViewById<TextInputEditText>(R.id.peerEndpointInput).setText(peer.endpoint)
    item.findViewById<TextInputEditText>(R.id.peerKeepaliveInput).setText(peer.persistentKeepalive)
    item.findViewById<MaterialButton>(R.id.removePeerButton).setOnClickListener {
      peerList.removeView(item)
    }
    peerList.addView(item)
  }

  private fun generateKey() {
    val pair = ConfigDraftIO.generateKeyPair()
    setPrivateKey(pair.privateKey, pair.publicKey)
  }

  private fun setPrivateKey(privateKey: String, publicKey: String) {
    suppressPublicKey = true
    privateKeyInput.setText(privateKey)
    publicKeyInput.setText(publicKey)
    suppressPublicKey = false
  }

  private fun save() {
    val draft = collectDraft()
    val conf = ConfigDraftIO.toConf(draft).getOrNull()
    if (conf == null) {
      Toast.makeText(this, R.string.tunnel_save_invalid_conf, Toast.LENGTH_SHORT).show()
      return
    }
    when (controller.saveTunnel(draft.name, conf, originalName)) {
      TunnelSaveResult.SAVED -> {
        Toast.makeText(this, R.string.tunnel_saved, Toast.LENGTH_SHORT).show()
        finish()
      }
      TunnelSaveResult.INVALID_NAME ->
        Toast.makeText(this, R.string.tunnel_save_invalid_name, Toast.LENGTH_SHORT).show()
      TunnelSaveResult.INVALID_CONF ->
        Toast.makeText(this, R.string.tunnel_save_invalid_conf, Toast.LENGTH_SHORT).show()
      TunnelSaveResult.NAME_IN_USE ->
        Toast.makeText(this, R.string.tunnel_save_name_in_use, Toast.LENGTH_SHORT).show()
      TunnelSaveResult.WRITE_FAILED ->
        Toast.makeText(this, R.string.tunnel_save_failed, Toast.LENGTH_SHORT).show()
    }
  }

  private fun collectDraft(): TunnelDraft {
    val peers = (0 until peerList.childCount).map { index ->
      val item = peerList.getChildAt(index)
      PeerDraft(
        publicKey = textOf(item, R.id.peerPublicKeyInput),
        presharedKey = textOf(item, R.id.peerPresharedKeyInput),
        allowedIps = textOf(item, R.id.peerAllowedIpsInput),
        endpoint = textOf(item, R.id.peerEndpointInput),
        persistentKeepalive = textOf(item, R.id.peerKeepaliveInput),
      )
    }
    return TunnelDraft(
      name = nameInput.text?.toString().orEmpty(),
      privateKey = privateKeyInput.text?.toString().orEmpty(),
      publicKey = publicKeyInput.text?.toString().orEmpty(),
      addresses = addressesInput.text?.toString().orEmpty(),
      listenPort = listenPortInput.text?.toString().orEmpty(),
      dns = dnsInput.text?.toString().orEmpty(),
      mtu = mtuInput.text?.toString().orEmpty(),
      peers = peers,
    )
  }

  private fun textOf(item: View, id: Int): String {
    return item.findViewById<TextInputEditText>(id).text?.toString().orEmpty()
  }

  private inner class PrivateKeyWatcher : TextWatcher {
    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(text: Editable?) {
      if (suppressPublicKey) {
        return
      }
      publicKeyInput.setText(ConfigDraftIO.publicKeyFrom(text?.toString().orEmpty()).orEmpty())
    }
  }

  companion object {
    const val EXTRA_TUNNEL_NAME = "tunnel_name"

    fun intent(context: Context, name: String? = null): Intent {
      return Intent(context, TunnelEditActivity::class.java).apply {
        if (!name.isNullOrBlank()) {
          putExtra(EXTRA_TUNNEL_NAME, name)
        }
      }
    }
  }
}
