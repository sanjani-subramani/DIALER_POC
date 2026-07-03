package com.example.dialermobilepoc.model

data class CallLog(
    val id: Long,
    val agentId: String,
    val customerNumber: String,
    val status: String,
    val recordingUrl: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val providerCallSid: String? = null,
    val twilioRecordingSid: String? = null,
    val localFilePath: String? = null,
    val firebaseUrl: String? = null
)