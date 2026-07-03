package com.example.dialermobilepoc.model

data class Agent(
    val agentId: String,
    val agentName: String,
    val deviceId: String,
    val agentPhoneNumber: String,
    val fcmToken: String? = null
)