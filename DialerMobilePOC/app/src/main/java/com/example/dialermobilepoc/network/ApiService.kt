package com.example.dialermobilepoc.network

import com.example.dialermobilepoc.model.Agent
import com.example.dialermobilepoc.model.BridgeCallRequest
import com.example.dialermobilepoc.model.CallLog
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ApiService {

    @GET("api/calls")
    suspend fun getCalls(): List<CallLog>

    @POST("api/telephony/bridge-call")
    suspend fun bridgeCall(@Body request: BridgeCallRequest): Response<CallLog>

    @POST("api/agents")
    suspend fun saveAgent(@Body agent: Agent): Response<Agent>

    @GET("api/agents")
    suspend fun getAgents(): List<Agent>

    @GET("api/telephony/check-recording")
    suspend fun checkRecording(@Query("callLogId") callLogId: Long): Response<CallLog>

    @Streaming
    @GET("api/recordings/local/{callLogId}")
    suspend fun downloadRecordingLocal(@Path("callLogId") callLogId: Long): Response<ResponseBody>
}