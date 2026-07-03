package com.example.dialermobilepoc

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.dialermobilepoc.model.Agent
import com.example.dialermobilepoc.model.BridgeCallRequest
import com.example.dialermobilepoc.network.RetrofitClient
import com.example.dialermobilepoc.util.normalizePhone
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var agentSpinner: Spinner
    private lateinit var callButton: Button
    private lateinit var statusText: TextView
    private var agents: List<Agent> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        agentSpinner = findViewById(R.id.agentSpinner)
        val customerNumberInput = findViewById<EditText>(R.id.customerNumberInput)
        callButton = findViewById(R.id.callButton)
        statusText = findViewById(R.id.statusText)
        val goToAddAgentButton = findViewById<Button>(R.id.goToAddAgentButton)
        val goToCallLogsButton = findViewById<Button>(R.id.goToCallLogsButton)

        callButton.isEnabled = false

        goToAddAgentButton.setOnClickListener {
            startActivity(Intent(this, AddAgentActivity::class.java))
        }

        goToCallLogsButton.setOnClickListener {
            startActivity(Intent(this, CallLogsActivity::class.java))
        }

        callButton.setOnClickListener {
            val agentId = agents.getOrNull(agentSpinner.selectedItemPosition)?.agentId
            val customerNumber = normalizePhone(customerNumberInput.text.toString())

            if (agentId.isNullOrEmpty() || customerNumber.isEmpty()) {
                statusText.text = "Please select an Agent and enter a Customer Number"
                return@setOnClickListener
            }

            statusText.text = "Calling $customerNumber..."
            callButton.isEnabled = false

            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.api.bridgeCall(
                        BridgeCallRequest(agentId, customerNumber)
                    )
                    if (response.isSuccessful) {
                        val callStatus = response.body()?.status ?: "unknown"
                        statusText.text = "Call initiated to $customerNumber! Status: $callStatus"
                    } else {
                        statusText.text = "Error: ${response.code()} ${response.message()}"
                    }
                } catch (e: Exception) {
                    statusText.text = "Error: ${e.message}"
                } finally {
                    callButton.isEnabled = true
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadAgents()
    }

    private fun loadAgents() {
        val previouslySelectedAgentId = agents.getOrNull(agentSpinner.selectedItemPosition)?.agentId

        lifecycleScope.launch {
            try {
                val fetched = RetrofitClient.api.getAgents()
                agents = fetched

                if (fetched.isEmpty()) {
                    agentSpinner.adapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        listOf("No agents — add one first")
                    )
                    callButton.isEnabled = false
                    statusText.text = "No agents registered yet. Add an agent first."
                } else {
                    val labels = fetched.map { "${it.agentId} · ${it.agentName} · ${it.agentPhoneNumber}" }
                    agentSpinner.adapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        labels
                    )
                    callButton.isEnabled = true

                    val restoreIndex = fetched.indexOfFirst { it.agentId == previouslySelectedAgentId }
                    if (restoreIndex >= 0) {
                        agentSpinner.setSelection(restoreIndex)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Couldn't load agents", Toast.LENGTH_SHORT).show()
                callButton.isEnabled = false
            }
        }
    }
}