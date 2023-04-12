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
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.furkan.pekautomotiveapp.databinding.ActivityMainBinding
import com.furkan.pekautomotiveapp.network.NetworkManager
import com.furkan.pekautomotiveapp.util.Constants
import com.furkan.pekautomotiveapp.viewmodel.MainViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var networkManager: NetworkManager

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        networkManager = NetworkManager(this, viewModel)

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
                Snackbar.make(
                    binding.root,
                    getString(R.string.error_enter_message),
                    Snackbar.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            lifecycleScope.launch { sendToServer(input, ipList, refusedList) }
            if (viewModel.isConnected.value!! && !cbManualIP.isChecked) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.error_no_connection),
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

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset Connection").setMessage(getString(R.string.dialog_reset_message))
            .setPositiveButton("Yes") { _, _ -> resetConnection() }
            .setNegativeButton("No", null).show()
    }

    private fun resetConnection() {
        cbManualIP.isChecked = false
        sharedPreferences.edit().remove("server_ip").apply()
        showConnectionDialog(false)
    }

    private fun showConnectionDialog(isFirstRun: Boolean) {
        AlertDialog.Builder(this).setTitle("Connection Warning")
            .setMessage(getString(R.string.dialog_warning_message))
            .setPositiveButton("OK") { _, _ ->
                progressDialog = ProgressDialog(this).apply {
                    setTitle("Establishing Connection")
                    setMessage(
                        if (isFirstRun) getString(R.string.dialog_first_time_message)
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
            withTimeout(Constants.CONNECTION_TIMEOUT) {
                while (currentRetry < maxRetries) {
                    Log.d("Network", "connectToServer: Inside while loop")
                    Log.d("Network", "Connection retry : ${currentRetry + 1}")
                    viewModel.isConnected.postValue(false)
                    if (cbManualIP.isChecked && etManualIP.text.toString().isNotEmpty()) {
                        Log.d("Network", "connectToServer: Manual IP checked")
                        val manualIp = etManualIP.text.toString()
                        if (testConnection(this@MainActivity, manualIp)) {
                            Log.d("Network", "connectToServer: Manual IP connection successful")
                            ipList.clear()
                            ipList.add(manualIp)
                            viewModel.isConnected.postValue(true)
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
                                    viewModel.isConnected.postValue(true)
                                    ipList.clear()
                                    refusedList.clear()
                                    ipList.add(ip)
                                    break
                                }
                                Log.d("Network", "connectToServer: IP connection unsuccessful")
                            }
                        }
                    }
                    if (viewModel.isConnected.value!!) {
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
                if (viewModel.isConnected.value!!) {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.info_connection_successful),
                        Snackbar.LENGTH_LONG
                    ).show()
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Connection Failed")
                        .setMessage(getString(R.string.dialog_failed_message))
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
        var result = false
        try {
            withTimeout(Constants.SOCKET_TIMEOUT) {
                result = networkManager.attemptConnection(ip)
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
                if (networkManager.attemptConnection(manualIp)) {
                    withContext(Dispatchers.Main) { progressDialog.dismiss() }
                    Log.d("Network", "sendToServer: Sending ip to sentToIp for IP $manualIp")
                    sendToIp(manualIp, text)
                } else {
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Snackbar.make(
                            binding.root,
                            getString(R.string.error_ip_not_valid),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
                return@withContext
            }
            for (ip in ipList) {
                if (!refusedList.contains(ip)) {
                    if (networkManager.attemptConnection(ip)) {
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
                val socket = Socket(ip, Constants.PORT)
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
                        getString(R.string.info_message_send_successful),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("Network", "Error sending to IP: $ip")
                runOnUiThread {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.error_send_message),
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