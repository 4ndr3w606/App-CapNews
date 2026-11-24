package com.example.newsapp.notifications

import android.content.DialogInterface
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import androidx.core.view.ViewCompat
import androidx.fragment.app.DialogFragment
import com.example.newsapp.R
import com.example.newsapp.data.HeadlinesSettings
import com.example.newsapp.data.LocalAuth
import com.example.newsapp.data.PreferencesManager
import com.example.newsapp.data.headlineCategories
import com.example.newsapp.databinding.DialogHeadlinesSettingsBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar

class HeadlinesSettingsDialog : DialogFragment() {
    private var hour = 8
    private var minute = 0
    private val selected = mutableSetOf<String>()

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val context = requireContext()
        val accountId = requireArguments().getLong(ARG_ACCOUNT)
        val preferences = PreferencesManager(context, accountId)
        val saved = preferences.headlinesSettings()
        hour = savedInstanceState?.getInt(STATE_HOUR) ?: saved.hour
        minute = savedInstanceState?.getInt(STATE_MINUTE) ?: saved.minute
        selected.clear()
        selected.addAll(savedInstanceState?.getStringArrayList(STATE_SELECTED) ?: saved.categories)

        val builder = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_NewsApp_Headlines)
        val inflater = LayoutInflater.from(builder.context)
        val binding = DialogHeadlinesSettingsBinding.inflate(inflater)
        ViewCompat.setAccessibilityPaneTitle(binding.root, getString(R.string.headlines_settings))
        val timeFormat = DateFormat.getTimeFormat(context)
        fun updateTimeLabel() {
            val date = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }.time
            val time = timeFormat.format(date)
            binding.tvHeadlinesTime.text = time
            binding.btnHeadlinesTime.contentDescription = getString(R.string.headlines_change_time_description, time)
        }
        updateTimeLabel()

        fun bindTimePicker(picker: MaterialTimePicker) {
            picker.clearOnPositiveButtonClickListeners()
            picker.addOnPositiveButtonClickListener {
                hour = picker.hour
                minute = picker.minute
                updateTimeLabel()
            }
        }
        (childFragmentManager.findFragmentByTag(TIME_PICKER_TAG) as? MaterialTimePicker)?.let(::bindTimePicker)
        binding.btnHeadlinesTime.setOnClickListener {
            if (childFragmentManager.findFragmentByTag(TIME_PICKER_TAG) != null) return@setOnClickListener
            val format = if (DateFormat.is24HourFormat(context)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
            val picker = MaterialTimePicker.Builder()
                .setTheme(R.style.ThemeOverlay_NewsApp_HeadlinesTimePicker)
                .setTitleText(R.string.headlines_pick_time)
                .setTimeFormat(format)
                .setHour(hour)
                .setMinute(minute)
                .build()
            bindTimePicker(picker)
            picker.showNow(childFragmentManager, TIME_PICKER_TAG)
        }

        val saveLabel = if (saved.enabled) R.string.headlines_save_changes else R.string.headlines_activate
        val dialog = builder.setView(binding.root)
            .setPositiveButton(saveLabel) { _, _ ->
                if (LocalAuth.activeUserId(context) != accountId) return@setPositiveButton
                val settings = HeadlinesSettings(true, hour, minute, selected.toSet())
                if (settings != preferences.headlinesSettings()) {
                    preferences.saveHeadlinesSettings(settings)
                    HeadlinesSchedule.cancel(context)
                }
                HeadlinesSchedule.ensureScheduled(context, accountId)
                parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle().apply { putBoolean(RESULT_ENABLED, true) })
            }
            .setNegativeButton(android.R.string.cancel, null)
            .apply {
                if (saved.enabled) {
                    setNeutralButton(R.string.headlines_disable) { _, _ ->
                        if (LocalAuth.activeUserId(context) == accountId) {
                            preferences.saveHeadlinesSettings(saved.copy(enabled = false))
                            HeadlinesSchedule.cancel(context)
                            parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle().apply {
                                putBoolean(RESULT_ENABLED, false)
                            })
                        }
                    }
                }
            }
            .create()

        for ((category, label) in headlineCategories) {
            val chip = (inflater.inflate(R.layout.item_headlines_category, binding.categoryOptions, false) as Chip).apply {
                setText(label)
                isChecked = category in selected
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(category) else selected.remove(category)
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = selected.isNotEmpty()
                }
            }
            binding.categoryOptions.addView(chip)
        }
        binding.tvHeadlinesStatus.setText(if (saved.enabled) R.string.headlines_enabled else R.string.headlines_disabled)
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = selected.isNotEmpty()
        }
        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_HOUR, hour)
        outState.putInt(STATE_MINUTE, minute)
        outState.putStringArrayList(STATE_SELECTED, ArrayList(selected))
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val TAG = "headlines_settings"
        const val RESULT_KEY = "headlines_settings_result"
        const val RESULT_ENABLED = "enabled"
        private const val ARG_ACCOUNT = "account_id"
        private const val STATE_HOUR = "hour"
        private const val STATE_MINUTE = "minute"
        private const val STATE_SELECTED = "selected"
        private const val TIME_PICKER_TAG = "headlines_time_picker"

        fun newInstance(accountId: Long) = HeadlinesSettingsDialog().apply {
            arguments = Bundle().apply { putLong(ARG_ACCOUNT, accountId) }
        }
    }
}
