package com.focusblock.ui.common

import com.focusblock.domain.model.RuleType
import com.focusblock.domain.model.Schedule
import java.time.LocalDateTime

/** The editable fields of one limit, shared by the app and website limit editors. */
interface LimitForm {
    var type: RuleType
    var dailyLimitMinutes: Int
    var hourlyLimitMinutes: Int
    var launchLimit: Int
    var blockUntil: LocalDateTime
    var schedule: Schedule

    /** Saves the form as a rule (replacing any existing rule of the same type). */
    fun addCurrent()
}
