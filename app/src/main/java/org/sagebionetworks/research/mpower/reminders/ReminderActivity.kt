/*
 * BSD 3-Clause License
 *
 * Copyright 2018  Sage Bionetworks. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2.  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3.  Neither the name of the copyright holder(s) nor the names of any contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. No license is granted to the trademarks of
 * the copyright holders even if such marks are included in this software.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sagebionetworks.research.mpower.reminders

import android.app.Activity
import android.app.TimePickerDialog
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_reminder.reminder_checkbox
import kotlinx.android.synthetic.main.activity_reminder.reminder_done_button
import kotlinx.android.synthetic.main.activity_reminder.reminder_time_button
import org.researchstack.backbone.result.StepResult
import org.researchstack.backbone.result.TaskResult
import org.researchstack.backbone.step.Step
import org.researchstack.backbone.ui.ViewTaskActivity.EXTRA_TASK_RESULT
import org.sagebionetworks.research.mpower.R
import org.sagebionetworks.research.mpower.research.MpIdentifier.STUDY_BURST_REMINDER
import org.sagebionetworks.research.mpower.viewmodel.StudyBurstViewModel
import org.sagebionetworks.research.sageresearch.extensions.toThreeTenLocalDateTime
import org.threeten.bp.LocalDateTime
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Calendar.HOUR_OF_DAY
import java.util.Calendar.MILLISECOND
import java.util.Calendar.MINUTE
import java.util.Calendar.SECOND
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ReminderActivity: AppCompatActivity() {

    private val timePickerDialog: TimePickerDialog by lazy {
        val hourMinutePair =
                viewModel.timeLiveData.value ?:
                Pair(viewModel.defaultHour, viewModel.defaultMinute)
        val dialog = TimePickerDialog(this, TimePickerDialog.OnTimeSetListener {
            _, hour, minute ->
            viewModel.hourMinutePair = Pair(hour, minute)
        }, hourMinutePair.first, hourMinutePair.second, false)
        dialog
    }

    private val reminderManager: MpReminderManager by lazy {
        MpReminderManager(this)
    }

    protected val viewModel: ReminderActivityViewModel by lazy {
        ViewModelProviders.of(this).get(ReminderActivityViewModel::class.java)
    }

    /**
     * @property studyBurstViewModel encapsulates all read/write data operations
     */
    private val studyBurstViewModel: StudyBurstViewModel by lazy {
        ViewModelProviders.of(this, studyBurstViewModelFactory).get(StudyBurstViewModel::class.java)
    }

    /**
     * @property firstStudyBurstScheduledOn the LocalDateTime of the first study burst schedule
     */
    private var firstStudyBurstScheduledOn: LocalDateTime? = null

    /**
     * @property studyBurstViewModelFactory used to create a StudyBurstViewModel instance injected through Dagger
     */
    @Inject
    lateinit var studyBurstViewModelFactory: StudyBurstViewModel.Factory

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminder)

        reminder_done_button.setOnClickListener { onDoneButtonClicked() }
        reminder_time_button.setOnClickListener { timePickerDialog.show() }
        reminder_checkbox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.doNotRemindMe = isChecked
        }

        viewModel.timeLiveData.observe(this, Observer { hourMinutePair ->
            hourMinutePair?.let {
                reminder_time_button.text = viewModel.toString(it)
                timePickerDialog.updateTime(it.first, it.second)
            }
        })
        viewModel.doNotRemindMeLiveData.observe(this, Observer { doNotRemindMe ->
            doNotRemindMe?.let {
                reminder_checkbox.isChecked = it
            }
        })

        studyBurstViewModel.liveData().observe(this, Observer {
            firstStudyBurstScheduledOn = it?.earliestStudyBurstScheduledOn
        })
    }

    /**
     * This function is called when the bottom done button is clicked
     */
    protected fun onDoneButtonClicked() {
        val hourMinutePair = viewModel.timeLiveData.value ?: run {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val doNotRemindMe = viewModel.doNotRemindMeLiveData.value ?: run {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val scheduledOn = firstStudyBurstScheduledOn ?:
            studyBurstViewModel.studyStartDate()?.toThreeTenLocalDateTime() ?:
            LocalDateTime.now()

        val initialReminderTime = LocalDateTime.now()
                .withHour(hourMinutePair.first)
                .withMinute(hourMinutePair.second)
                .withSecond(0)
                .withNano(0)

        val reminder = reminderManager.createStudyBurstReminder(
                this, scheduledOn, initialReminderTime)

        if (doNotRemindMe) {
            reminderManager.cancelReminder(this, reminder)
        } else {
            reminderManager.scheduleReminder(this, reminder)
        }

        val taskResult = TaskResult(STUDY_BURST_REMINDER)
        taskResult.startDate = viewModel.startedOnTime
        taskResult.endDate = Date()

        val formStepResultTime: StepResult<StepResult<String>> = StepResult(Step("ReminderTime"))
        val stepResultTime = StepResult<String>(Step("reminderTime"))
        stepResultTime.result = viewModel.toResultString(hourMinutePair)
        formStepResultTime.result = stepResultTime
        taskResult.results["ReminderTime"] = formStepResultTime

        val formStepResultNoReminder: StepResult<StepResult<Boolean>> = StepResult(Step("NoReminder"))
        val stepResultNoReminder = StepResult<Boolean>(Step("noReminder"))
        stepResultNoReminder.result = doNotRemindMe
        formStepResultNoReminder.result = stepResultNoReminder
        taskResult.results["NoReminder"] = formStepResultNoReminder

        val resultIntent = Intent()
        resultIntent.putExtra(EXTRA_TASK_RESULT, taskResult)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}

/**
 * ReminderActivityViewModel holds the data for the ReminderActivity across life cycle changes
 */
class ReminderActivityViewModel: ViewModel() {

    /**
     * @property startedOnTime when the user landed on the screen
     */
    val startedOnTime = Date()

    /**
     * @property defaultHour of the time
     */
    val defaultHour: Int
        get() {
            return 9
        }

    /**
     * @property defaultMinute of the time
     */
    val defaultMinute: Int
        get() {
            return 0
        }

    /**
     * @property hourMinutePair to be set for the reminder time picker
     */
    var hourMinutePair = Pair(defaultHour, defaultMinute)
        set(value) {
            field = value
            timeMutableLiveData.value = hourMinutePair
        }

    /**
     * @property doNotRemindMe stores the state of the checkbox
     */
    var doNotRemindMe: Boolean = false
        set(value) {
            field = value
            doNotRemindMutableLiveData.value = value
        }

    private val timeMutableLiveData: MutableLiveData<Pair<Int, Int>> = MutableLiveData()
    val timeLiveData: LiveData<Pair<Int, Int>>
        get() {
            timeMutableLiveData.value = Pair(hourMinutePair.first, hourMinutePair.second)
            return timeMutableLiveData
        }

    private val doNotRemindMutableLiveData: MutableLiveData<Boolean> = MutableLiveData()
    val doNotRemindMeLiveData: LiveData<Boolean>
        get() {
            doNotRemindMutableLiveData.value = doNotRemindMe
            return doNotRemindMutableLiveData
        }

    private fun dateForTime(hourMinutePair: Pair<Int, Int>): Date {
        val calendar = Calendar.getInstance()
        calendar.set(HOUR_OF_DAY, hourMinutePair.first)
        calendar.set(MINUTE, hourMinutePair.second)
        calendar.set(SECOND, 0)
        calendar.set(MILLISECOND, 0)
        return calendar.time
    }

    /**
     * Converts a hour minute pair to a time string
     */
    fun toString(hourMinutePair: Pair<Int, Int>): String {
        val formatter = SimpleDateFormat("h:mm aaa", Locale.getDefault())
        return formatter.format(dateForTime(hourMinutePair))
    }

    /**
     * Converts a hour minute pair to a time string for the task result
     */
    fun toResultString(hourMinutePair: Pair<Int, Int>): String {
        val formatter = SimpleDateFormat("HH:mm:00.000", Locale.getDefault())
        return formatter.format(dateForTime(hourMinutePair))
    }
}