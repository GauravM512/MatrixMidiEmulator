package com.matrix.midiemulator.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.matrix.midiemulator.R
import com.matrix.midiemulator.util.AppPreferences

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        val hideFnSwitch = findViewById<SwitchMaterial>(R.id.hideFnSwitch)
        val hideTitleSwitch = findViewById<SwitchMaterial>(R.id.hideTitleSwitch)

        hideFnSwitch.isChecked = !AppPreferences.isFnVisible(this)
        hideTitleSwitch.isChecked = !AppPreferences.isTitleVisible(this)

        hideFnSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppPreferences.setFnVisible(this, !isChecked)
        }
        hideTitleSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppPreferences.setTitleVisible(this, !isChecked)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
