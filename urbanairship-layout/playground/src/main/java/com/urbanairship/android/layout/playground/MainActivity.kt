package com.urbanairship.android.layout.playground

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.urbanairship.UALog
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.actions.ActionRunRequestFactory
import com.urbanairship.actions.PermissionResultReceiver
import com.urbanairship.actions.PromptPermissionAction
import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.ThomasListener
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.playground.databinding.ActivityMainBinding
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.iam.InAppActionUtils
import com.urbanairship.json.JsonValue
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus

class MainActivity : AppCompatActivity() {

    companion object {

        private const val PREF_NAME = "layout-playground"
        private const val PREF_KEY = "selected-layout"
        private const val SAMPLE_LAYOUTS_PATH = "sample_layouts"
    }

    private lateinit var binding: ActivityMainBinding

    private val sharedPrefs by lazy {
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private val adapter by lazy {
        ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line)
    }

    private var previousSelection: String?
        get() = sharedPrefs.getString(PREF_KEY, null)
        set(value) = sharedPrefs.edit(commit = true) {
            putString(PREF_KEY, value)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        initViews(binding)

        // Enable webview debugging via Chrome for debug builds.
        if (0 != applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    override fun onResume() {
        super.onResume()

        if (binding.layoutSpinnerText.adapter != adapter) {
            binding.layoutSpinnerText.setAdapter(adapter)
        }
    }

    private fun initViews(binding: ActivityMainBinding) {
        val layoutFiles = ResourceUtils.listJsonAssets(this, SAMPLE_LAYOUTS_PATH)
        adapter.addAll(layoutFiles)

        previousSelection?.let { selected ->
            if (adapter.getPosition(selected) > -1) {
                binding.layoutSpinnerText.setText(selected, false)
            }
        }

        binding.layoutSpinnerText.setOnItemClickListener { _, _, position, _ ->
            if (position == ListView.INVALID_POSITION) return@setOnItemClickListener
            previousSelection = adapter.getItem(position)
        }

        binding.showModal.setOnClickListener { v ->
            v.isEnabled = false
            displayLayout(binding.layoutSpinnerText.text.toString())
            v.postDelayed({ v.isEnabled = true }, 150)
        }

        binding.startAndroidActivity.setOnClickListener {
            startActivity(Intent(this, OtherAndroidActivity::class.java))
        }

        binding.startAppcompatActivity.setOnClickListener {
            startActivity(Intent(this, OtherAppCompatActivity::class.java))
        }
    }

    private fun displayLayout(fileName: String) {
        try {
            val jsonMap = ResourceUtils.readJsonAsset(this, "sample_layouts/$fileName")
            if (jsonMap == null) {
                UALog.e("Failed to display layout! Not a valid JSON object: '$fileName'")
                Toast.makeText(this, "Not a valid JSON object", Toast.LENGTH_LONG).show()
                return
            }
            val payload = LayoutInfo(jsonMap)
            Thomas.prepareDisplay(payload)
                .setInAppActivityMonitor(GlobalActivityMonitor.shared(applicationContext))
                .setListener(thomasListener)
                .display(this)
        } catch (e: Exception) {
            UALog.e(e)
            Toast.makeText(this, "Error trying to display layout", Toast.LENGTH_LONG).show()
        }
    }

    private val thomasListener = object : ThomasListener {
        private val events = mutableListOf<String>()

        override fun onPageView(pagerData: PagerData, state: LayoutData, displayedAt: Long) {
            "onPageView(pagerData: $pagerData, state: $state, displayedAt: $displayedAt)".let {
                events.add(it)
                UALog.d(it)
            }
        }

        override fun onPageSwipe(pagerData: PagerData, toPageIndex: Int, toPageId: String, fromPageIndex: Int, fromPageId: String, state: LayoutData) {
            "onPageSwipe(pagerData: $pagerData, toPageIndex: $toPageIndex, toPageId: $toPageId, fromPageIndex: $fromPageIndex, fromPageId: $fromPageId, state: $state)".let {
                events.add(it)
                UALog.d(it)
            }
        }

        override fun onButtonTap(buttonId: String, reportingMetadata: JsonValue?, state: LayoutData) {
            "onButtonTap(buttonId: $buttonId, state: $state)".let {
                events.add(it)
                UALog.d(it)
            }
        }

        override fun onDismiss(displayTime: Long) {
            "onDismiss(displayTime: $displayTime)".let {
                events.add(it)
                UALog.d(it)
            }
            dumpEvents()
        }

        override fun onDismiss(buttonId: String, buttonDescription: String?, cancel: Boolean, displayTime: Long, state: LayoutData) {
            "onDismiss(buttonId: $buttonId, buttonDescription: $buttonDescription, cancel: $cancel, displayTime: $displayTime, state: $state".let {
                events.add(it)
                UALog.d(it)
            }
            dumpEvents()
        }

        override fun onFormResult(formData: FormData.BaseForm, state: LayoutData) {
            "onFormResult(formData: ${formData.toJsonValue()}, state: $state)".let {
                events.add(it)
                UALog.d(it)
            }
        }

        override fun onFormDisplay(formInfo: FormInfo, state: LayoutData) {
            "onFormDisplay(formInfo: $formInfo, state: $state)".let {
                events.add(it)
                UALog.d(it)
            }
        }

        override fun onRunActions(actions: MutableMap<String, JsonValue>, state: LayoutData) {
            "onRunActions(actions: $actions, state: $state)".let {
                events.add(it)
                UALog.d(it)
            }

            val permissionResultReceiver = object : PermissionResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onResult(permission: Permission, before: PermissionStatus, after: PermissionStatus) {
                    "onPermissionResult(permission: $permission, before: $before, after: $after, state: $state)".let {
                        events.add(it)
                        UALog.d(it)
                    }
                }
            }

            InAppActionUtils.runActions(actions, ActionRunRequestFactory {
                val bundle = Bundle()
                bundle.putParcelable(PromptPermissionAction.RECEIVER_METADATA, permissionResultReceiver)

                ActionRunRequest.createRequest(it).setMetadata(bundle)
            })
        }

        override fun onPagerGesture(
            gestureId: String,
            reportingMetadata: JsonValue?,
            state: LayoutData
        ) {
            "onPagerGesture(gestureId: $gestureId, reportingMetadata: $reportingMetadata, state: $state)".let {
                events.add(it)
                UALog.d(it)
            }
        }

        override fun onPagerAutomatedAction(
            actionId: String,
            reportingMetadata: JsonValue?,
            state: LayoutData
        ) {
            "onPagerAutomatedAction(gestureId: $actionId, reportingMetadata: $reportingMetadata, state: $state)".let {
                events.add(it)
                UALog.d(it)
            } }

        private fun dumpEvents() {
            UALog.d("\n")
            UALog.d("---- LAYOUT EVENTS ----")
            events.forEachIndexed { index, s -> UALog.d("$index: $s") }
            UALog.d("-----------------------")
            UALog.d("\n")
            events.clear()
        }
    }
}
