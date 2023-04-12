package com.furkan.pekautomotiveapp

import android.app.ProgressDialog
import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.furkan.pekautomotiveapp.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val CONNECTION_TIMEOUT = 20_000L
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var etInput: EditText
    private lateinit var btnSend: Button
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var cbManualIP: CheckBox
    private lateinit var etManualIP: EditText
    private lateinit var progressDialog: ProgressDialog
    private lateinit var toolbar: Toolbar
    private lateinit var ipList: HashSet<String>
    private lateinit var refusedList: MutableList<String>
    private var isConnected = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        btnSend = binding.btnSend
        etInput = binding.etInput
        cbManualIP = binding.cbManualIP
        etManualIP = binding.etManualIP
        toolbar = binding.toolbar
        setSupportActionBar(toolbar)
        ipList = HashSet()
        refusedList = mutableListOf()
        sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val isFirstRun = sharedPreferences.getBoolean("is_first_run", true)
        if (isFirstRun) {
            sharedPreferences.edit().putBoolean("is_first_run", false).apply()
        }
        cbManualIP.setOnCheckedChangeListener { _, isChecked -> etManualIP.isEnabled = isChecked }
        showConnectionDialog(isFirstRun)
        btnSend.setOnClickListener {
            val input = etInput.text.toString()
            if (input.isEmpty()) {
                Snackbar.make(binding.root, "Please enter your message", Snackbar.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            lifecycleScope.launch { sendToServer(input, ipList, refusedList) }
            if (!isConnected && !cbManualIP.isChecked) {
                Snackbar.make(
                    binding.root,
                    "NO CONNECTION. " +
                            "Please make sure the server is running and try connecting again. " +
                            "To refresh the connection, click the button on the top right corner.",
                    Snackbar.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset -> {
                showResetConfirmationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private suspend fun attemptConnection(ip: String): Boolean {
        val result: Boolean
        val socketTimeout = 3000
        withContext(Dispatchers.IO) {
            result = try {
                Log.d("Network", "attemptConnection: Attempting connection for IP $ip")
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, 8000), socketTimeout)
                Log.d("Network", "attemptConnection: Socket created for IP $ip") // Add this log
                socket.close()
                Log.d("Network", "attemptConnection: Connection for IP $ip is successful")
                isConnected = true
                true
            } catch (e: ConnectException) {
                if (e.toString().contains("Connection refused")) {
                    Log.d("Network", "attemptConnection: CONNECTION REFUSED FOR IP : $ip")
                }
                isConnected = false
                false
            } catch (e: UnknownHostException) {
                Log.d("Network", "attemptConnection: UnknownHost Exception for IP: $ip")
                e.printStackTrace()
                isConnected = false
                false
            } catch (e: IOException) {
                Log.d("Network", "attemptConnection: IO Exception for IP: $ip")
                e.printStackTrace()
                isConnected = false
                false
            } catch (e: SocketTimeoutException) {
                Log.d("Network", "attemptConnection: Socket timeout for IP: $ip")
                e.printStackTrace()
                isConnected = false
                false
            }
        }
        return result
    }

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset Connection")
            .setMessage("Are you sure you want to reset the connection?")
            .setPositiveButton("Yes") { _, _ -> resetConnection() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun resetConnection() {
        cbManualIP.isChecked = false
        sharedPreferences.edit().remove("server_ip").apply()
        showConnectionDialog(false)
    }

    private fun showConnectionDialog(isFirstRun: Boolean) {
        AlertDialog.Builder(this)
            .setTitle("Connection Warning")
            .setMessage("Please make sure the server is running before clicking OK, otherwise the recorded IP will be reset and the app will try to connect again.")
            .setPositiveButton("OK") { _, _ ->
                progressDialog = ProgressDialog(this).apply {
                    setTitle("Establishing Connection")
                    setMessage(
                        if (isFirstRun) "First time running the app... Configuring initial setup for connection."
                        else "Please wait..."
                    )
                    setCancelable(false)
                    show()
                }
                lifecycleScope.launch { connectToServer() }
            }.setCancelable(false).show()
    }

    private suspend fun connectToServer() {
        val maxRetries = 3
        var currentRetry = 0
        try {
            withTimeout(CONNECTION_TIMEOUT) {
                while (currentRetry < maxRetries) {
                    Log.d("Network", "connectToServer: Inside while loop")
                    Log.d("Network", "Connection retry : ${currentRetry + 1}")
                    isConnected = false
                    if (cbManualIP.isChecked && etManualIP.text.toString().isNotEmpty()) {
                        Log.d("Network", "connectToServer: Manual IP checked")
                        val manualIp = etManualIP.text.toString()
                        if (testConnection(this@MainActivity, manualIp)) {
                            Log.d("Network", "connectToServer: Manual IP connection successful")
                            ipList.clear()
                            ipList.add(manualIp)
                            isConnected = true
                            break
                        }
                    } else {
                        Log.d("Network", "connectToServer: Manual IP connection unsuccessful")
                        ipList = getLocalIpAddress()
                        Log.d("Network", "connectToServer: Obtained IP list")
                        Log.d(
                            "Network",
                            "IP RETRIEVAL PROCESS FINISHED, AVAILABLE IP ADDRESSES : $ipList"
                        )
                        if (ipList.isNotEmpty()) {
                            val iterator = ipList.iterator()
                            while (iterator.hasNext()) {
                                Log.d("Network", "connectToServer: Inside iterator loop")
                                val ip = iterator.next()
                                Log.d("Network", "connectToServer: Testing connection for IP $ip")
                                if (testConnection(this@MainActivity, ip)) {
                                    Log.d("Network", "connectToServer: IP connection successful")
                                    isConnected = true
                                    ipList.clear()
                                    refusedList.clear()
                                    ipList.add(ip)
                                    break
                                }
                                Log.d("Network", "connectToServer: IP connection unsuccessful")
                            }
                        }
                    }
                    if (isConnected) {
                        Log.d("Network", "connectToServer: isConnected is true")
                        break
                    } else {
                        currentRetry++
                        Log.d("Network", "connectToServer: Incrementing currentRetry")
                        if (currentRetry < maxRetries) {
                            delay(1000)
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.d("Network", "Connection process timed out.")
        } finally {
            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                if (isConnected) {
                    Snackbar.make(
                        binding.root,
                        "Connection successful. You can send messages now.",
                        Snackbar.LENGTH_LONG
                    ).show()
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Connection Failed")
                        .setMessage(
                            "Failed to connect to the server. " +
                                    "Please make sure the server is running and try again or enter a manual IP. " +
                                    "To refresh the connection, click the button on the top right corner."
                        )
                        .setPositiveButton("OK", null).setCancelable(false).show()
                }
            }
        }
    }

    private fun showTestConnectionProgressDialog(context: Context, ip: String): ProgressDialog {
        return ProgressDialog(context).apply {
            setTitle("Testing Connection")
            setMessage("Testing connection for IP: $ip")
            setCancelable(false)
            show()
        }
    }

    private suspend fun testConnection(context: Context, ip: String): Boolean {
        Log.d("Network", "testConnection: Starting test for IP $ip")
        val progressDialog = withContext(Dispatchers.Main) {
            if (cbManualIP.isChecked && etManualIP.text.toString().isNotEmpty()) {
                showTestConnectionProgressDialog(context, etManualIP.text.toString())
            } else {
                null
            }
        }
        val testTimeout = 3000L // 3 seconds
        var result = false
        try {
            withTimeout(testTimeout) {
                result = attemptConnection(ip)
                Log.d(
                    "Network",
                    "testConnection: Connection attempt finished for IP $ip, result: $result"
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.d("Network", "testConnection: Test connection timed out for IP: $ip")
        }
        if (progressDialog != null) {
            withContext(Dispatchers.Main) { progressDialog.dismiss() }
        }
        Log.d("Network", "testConnection: Finished test for IP $ip, final result: $result")
        return result
    }

    private suspend fun sendToServer(
        text: String,
        ipList: HashSet<String>,
        refusedList: MutableList<String>
    ) {
        withContext(Dispatchers.IO) {
            val manualIp = etManualIP.text.toString()
            if (cbManualIP.isChecked && manualIp.isNotEmpty()) {
                val progressDialog = withContext(Dispatchers.Main) {
                    showTestConnectionProgressDialog(this@MainActivity, manualIp)
                }
                Log.d("Network", "sendToServer: Sending ip to attemptConnection for IP $manualIp")
                if (attemptConnection(manualIp)) {
                    withContext(Dispatchers.Main) { progressDialog.dismiss() }
                    Log.d("Network", "sendToServer: Sending ip to sentToIp for IP $manualIp")
                    sendToIp(manualIp, text)
                } else {
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Snackbar.make(
                            binding.root,
                            "IP is not valid. Check if the server is running, retry to connect or enter another IP.",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
                return@withContext
            }
            for (ip in ipList) {
                if (!refusedList.contains(ip)) {
                    if (attemptConnection(ip)) {
                        sendToIp(ip, text)
                    } else {
                        Log.d("Network", "REFUSED FOR IP : $ip")
                        refusedList.add(ip)
                    }
                }
            }
        }
    }

    private suspend fun sendToIp(ip: String, text: String) {
        withContext(Dispatchers.IO) {
            try {
                val socket = Socket(ip, 8000)
                val outputStream = socket.getOutputStream()
                val outputStreamWriter = OutputStreamWriter(outputStream)
                val bufferedWriter = BufferedWriter(outputStreamWriter)
                bufferedWriter.write(text)
                bufferedWriter.flush()
                socket.close()
                Log.d("Network", "Sending to IP : $ip")
                runOnUiThread {
                    Snackbar.make(
                        binding.root,
                        "Message Sent!",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("Network", "Error sending to IP: $ip")
                runOnUiThread {
                    Snackbar.make(
                        binding.root,
                        "Error While Trying to Send Message!",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private suspend fun getLocalIpAddress(): HashSet<String> {
        val ipList = HashSet<String>()
        val wifi: WifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        val d = wifi.dhcpInfo
        val host: InetAddress
        withContext(Dispatchers.IO) {
            try {
                host = InetAddress.getByName(intToIp(d.dns1))
                val ip: ByteArray = host.address
                val jobs = mutableListOf<Deferred<Any>>()
                for (i in 1..254) {
                    jobs.add(async {
                        ip[3] = i.toByte()
                        val address: InetAddress = InetAddress.getByAddress(ip)
                        if (address.isReachable(500)) {
                            Log.d("Network", "$address machine is turned on and can be pinged")
                            synchronized(ipList) { ipList.add(address.toString().drop(1)) }
                        } else if (!address.hostAddress?.equals(address.hostName)!!) {
                            Log.d("Network", "$address machine is known in a DNS lookup")
                        } else {
                        }
                    })
                }
                jobs.awaitAll()
            } catch (e: UnknownHostException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return ipList
    }

    private fun intToIp(i: Int): String {
        return (i and 0xFF).toString() + "." +
                (i shr 8 and 0xFF) + "." +
                (i shr 16 and 0xFF) + "." +
                (i shr 24 and 0xFF)
    }
}