package com.example.locationpublisher

import android.location.Location

data class LocationMessage(
    val student_id: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

