package com.example.dialermobilepoc

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.dialermobilepoc.model.Agent
import com.example.dialermobilepoc.network.RetrofitClient
import com.example.dialermobilepoc.util.normalizePhone
import kotlinx.coroutines.launch

class AddAgentActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_agent)

        val agentIdInput = findViewById<EditText>(R.id.agentIdInput)
        val agentNameInput = findViewById<EditText>(R.id.agentNameInput)
        val agentPhoneInput = findViewById<EditText>(R.id.agentPhoneInput)
        val saveAgentButton = findViewById<Button>(R.id.saveAgentButton)
        val addAgentStatus = findViewById<TextView>(R.id.addAgentStatus)
        val backButton = findViewById<Button>(R.id.backButton)

        backButton.setOnClickListener {
            finish()
        }

        saveAgentButton.setOnClickListener {
            val agentId = agentIdInput.text.toString().trim()
            val agentName = agentNameInput.text.toString().trim()
            val agentPhoneNumber = normalizePhone(agentPhoneInput.text.toString())

            if (agentId.isEmpty() || agentName.isEmpty() || agentPhoneNumber.isEmpty()) {
                addAgentStatus.text = "Please fill in Agent ID, Agent Name, and Phone Number"
                return@setOnClickListener
            }

            addAgentStatus.text = "Saving..."
            saveAgentButton.isEnabled = false

            lifecycleScope.launch {
                try {
                    val duplicate = try {
                        RetrofitClient.api.getAgents().firstOrNull { existing ->
                            existing.agentId != agentId && normalizePhone(existing.agentPhoneNumber) == agentPhoneNumber
                        }
                    } catch (e: Exception) {
                        null
                    }

                    if (duplicate != null) {
                        addAgentStatus.text =
                            "This number is already registered to agent ${duplicate.agentId} (${duplicate.agentName})."
                        return@launch
                    }

                    val response = RetrofitClient.api.saveAgent(
                        Agent(
                            agentId = agentId,
                            agentName = agentName,
                            deviceId = "mobile",
                            agentPhoneNumber = agentPhoneNumber,
                            fcmToken = ""
                        )
                    )
                    if (response.isSuccessful) {
                        addAgentStatus.text = "Agent saved! Phone: $agentPhoneNumber"
                    } else {
                        addAgentStatus.text = "Error: ${response.code()} ${response.message()}"
                    }
                } catch (e: Exception) {
                    addAgentStatus.text = "Error: ${e.message}"
                } finally {
                    saveAgentButton.isEnabled = true
                }
            }
        }
    }
}