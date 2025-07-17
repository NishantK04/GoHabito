package com.nishant.gohabito

import java.io.Serializable

data class Habit(
    val title: String = "",
    var daysCompleted: Int = 0,
    val goalDays: Int = 0,
    val startDate: String? = null,
    var completionDates: MutableList<String> = mutableListOf()
): Serializable
