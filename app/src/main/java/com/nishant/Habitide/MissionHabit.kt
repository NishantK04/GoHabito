package com.nishant.Habitide

data class MissionHabit(
    val name: String = "",
    val habitTitle: String = "",
    var checked: Boolean = false,
    val userId: String = "",
    var progressUpdated: Boolean = false
)
