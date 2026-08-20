package com.wirepilot.app.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object SystemBarInsets {
  fun apply(root: View) {
    val initialLeft = root.paddingLeft
    val initialTop = root.paddingTop
    val initialRight = root.paddingRight
    val initialBottom = root.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      view.setPadding(
        initialLeft + bars.left,
        initialTop + bars.top,
        initialRight + bars.right,
        initialBottom + bars.bottom,
      )
      insets
    }
    ViewCompat.requestApplyInsets(root)
  }
}
