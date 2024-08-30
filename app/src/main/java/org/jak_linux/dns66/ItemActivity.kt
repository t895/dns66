/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText

class ItemActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "ItemActivity"

        private const val READ_REQUEST_CODE = 1
    }

    private lateinit var locationText: TextInputEditText
    private lateinit var titleText: TextInputEditText
    private lateinit var stateSpinner: Spinner
    private var stateSwitch: Switch? = null
    private lateinit var imageView: ImageView

    fun performFileSearch() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(intent, READ_REQUEST_CODE)
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    public override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == READ_REQUEST_CODE && resultCode == RESULT_OK) {
            if (resultData != null) {
                val uri = resultData.data!!
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    locationText.setText(uri.toString())
                } catch (e: SecurityException) {
                    AlertDialog.Builder(this)
                        .setIcon(R.drawable.ic_warning)
                        .setTitle(R.string.permission_denied)
                        .setMessage(R.string.persistable_uri_permission_failed)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = getIntent()
        if (intent.getIntExtra("STATE_CHOICES", 3) == 2) {
            setContentView(R.layout.activity_item_dns)
            setTitle(R.string.activity_edit_dns_server)
        } else {
            setContentView(R.layout.activity_item)
            setTitle(R.string.activity_edit_filter)
        }

        titleText = findViewById(R.id.title)
        locationText = findViewById(R.id.location)
        stateSpinner = findViewById(R.id.state_spinner)
        stateSwitch = findViewById(R.id.state_switch)
        imageView = findViewById(R.id.image_view)

        if (intent.hasExtra("ITEM_TITLE")) {
            titleText.setText(intent.getStringExtra("ITEM_TITLE"))
        }
        if (intent.hasExtra("ITEM_LOCATION")) {
            locationText.setText(intent.getStringExtra("ITEM_LOCATION"))
        }
        if (intent.hasExtra("ITEM_STATE")) {
            stateSpinner.setSelection(intent.getIntExtra("ITEM_STATE", 0))
        }
        if (intent.hasExtra("ITEM_STATE")) {
            stateSwitch?.setChecked(intent.getIntExtra("ITEM_STATE", 0) % 2 != 0)
        }

        stateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                when (position) {
                    Configuration.Item.STATE_ALLOW -> imageView.setImageDrawable(
                        ContextCompat.getDrawable(this@ItemActivity, R.drawable.ic_state_allow)
                    )

                    Configuration.Item.STATE_DENY -> imageView.setImageDrawable(
                        ContextCompat.getDrawable(this@ItemActivity, R.drawable.ic_state_deny)
                    )

                    Configuration.Item.STATE_IGNORE -> imageView.setImageDrawable(
                        ContextCompat.getDrawable(this@ItemActivity, R.drawable.ic_state_ignore)
                    )
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        // We have an attachment icon for host files
        if (intent.getIntExtra("STATE_CHOICES", 3) == 3) {
            locationText.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val isAttachIcon =
                        if (locationText.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR) {
                            event.rawX >= locationText.right - locationText.totalPaddingRight
                        } else {
                            event.rawX <= locationText.totalPaddingLeft - locationText.left
                        }
                    if (isAttachIcon) {
                        performFileSearch()
                        true
                    }
                }
                false
            }
        }

        // Tint the attachment icon, if any.
        val typedValue = TypedValue()
        getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)

        val compoundDrawables = locationText.getCompoundDrawablesRelative()
        for (drawable in compoundDrawables) {
            if (drawable != null) {
                drawable.setTint(ContextCompat.getColor(this, typedValue.resourceId))
                Log.d(TAG, "onCreate: Setting tint")
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.item, menu)

        // We are creating an item
        if (!intent.hasExtra("ITEM_LOCATION")) {
            menu?.findItem(R.id.action_delete)?.setVisible(false)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            R.id.action_delete -> {
                setResult(RESULT_OK, Intent().putExtra("DELETE", true))
                finish()
            }

            R.id.action_save -> {
                val intent = Intent()
                    .putExtra("ITEM_TITLE", titleText.getText().toString())
                    .putExtra("ITEM_LOCATION", locationText.getText().toString())
                    .putExtra("ITEM_STATE", stateSpinner.selectedItemPosition)
                    .putExtra("ITEM_STATE", if (stateSwitch?.isChecked == true) 1 else 0)
                setResult(RESULT_OK, intent)
                finish()
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
