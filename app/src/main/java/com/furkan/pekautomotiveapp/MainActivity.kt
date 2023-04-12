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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.furkan.pekautomotiveapp.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var etInput: EditText
    private lateinit var btnSend: Button
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var cbManualIP: CheckBox
    private lateinit var etManualIP: EditText
    private lateinit var progressDialog: ProgressDialog
    private lateinit var toolbar: Toolbar
    private lateinit var ipList: HashSet<String>

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
        val refusedList = mutableListOf<String>()

        sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

        lifecycleScope.launch {
            val savedIp = sharedPreferences.getString("server_ip", null)
            if (savedIp != null && testConnection(savedIp)) {
                ipList.add(savedIp)
            } else {
                ipList = getLocalIpAddress()
                Log.d("Network", "IP RETRIEVAL PROCESS FINISHED, AVAILABLE IP ADDRESSES : $ipList")
            }
        }

        cbManualIP.setOnCheckedChangeListener { _, isChecked ->
            etManualIP.isEnabled = isChecked
        }

        showConnectionDialog()

        btnSend.setOnClickListener {
            val input = etInput.text.toString()
            if (input.isEmpty()) return@setOnClickListener
            lifecycleScope.launch {
                sendToServer(input, ipList, refusedList)
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
            .setTitle("Reset Connection")
            .setMessage("Are you sure you want to reset the connection?")
            .setPositiveButton("Yes") { _, _ ->
                resetConnection()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun resetConnection() {
        cbManualIP.isChecked = false
        sharedPreferences.edit().remove("server_ip").apply()
        showConnectionDialog()
    }

    private fun showConnectionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Connection Warning")
            .setMessage("Please make sure the server is running before clicking OK, otherwise the recorded IP will be reset and the app will try to connect again.")
            .setPositiveButton("OK") { _, _ ->
                progressDialog = ProgressDialog(this).apply {
                    setTitle("Establishing Connection")
                    setMessage("Please wait...")
                    setCancelable(false)
                    show()
                }
                lifecycleScope.launch {
                    connectToServer()
                }
            }
            .setCancelable(false)
            .show()
    }

    private suspend fun connectToServer() {
        var isConnected = false

        if (cbManualIP.isChecked && etManualIP.text.toString().isNotEmpty()) {
            val manualIp = etManualIP.text.toString()
            if (testConnection(manualIp)) {
                ipList.clear()
                ipList.add(manualIp)
                isConnected = true
            }
        } else {
            val savedIp = sharedPreferences.getString("server_ip", null)
            if (savedIp != null && testConnection(savedIp)) {
                ipList.clear()
                ipList.add(savedIp)
                isConnected = true
            } else {
                ipList.clear()
                ipList.addAll(getLocalIpAddress())
                for (ip in ipList) {
                    if (testConnection(ip)) {
                        isConnected = true
                        break
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            progressDialog.dismiss()
            if (!isConnected) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Connection Failed")
                    .setMessage("Failed to connect to the server. Please make sure the server is running and try again.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private suspend fun testConnection(ip: String): Boolean {
        if (cbManualIP.isChecked && etManualIP.text.toString().isNotEmpty()) {
            return withContext(Dispatchers.IO) {
                try {
                    val manualIp = etManualIP.text.toString()
                    val socket = Socket(manualIp, 8000)
                    socket.close()
                    runOnUiThread {
                        Snackbar.make(binding.root, "Connection for IP $manualIp is successful", Snackbar.LENGTH_SHORT).show()
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }
        } else {
            return withContext(Dispatchers.IO) {
                try {
                    val socket = Socket(ip, 8000)
                    socket.close()
                    runOnUiThread {
                        Snackbar.make(binding.root, "Connection for IP $ip is successful", Snackbar.LENGTH_LONG).show()
                    }
                    true
                } catch (e: ConnectException) {
                    if (e.toString().contains("Connection refused")) {
                        Log.d("Network", "REFUSED FOR IP : $ip")
                        //refusedList.add(ip)
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Connection for IP $ip refused", Toast.LENGTH_SHORT).show()
                        }
                    }
                    false
                } catch (e: UnknownHostException) {
                    e.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "INVALID IP", Toast.LENGTH_SHORT).show()
                    }
                    false
                } catch (e: IOException) {
                    e.printStackTrace()
                    false
                }
            }
        }
    }

    private suspend fun sendToServer(
        text: String,
        ipList: HashSet<String>,
        refusedList: MutableList<String>
    ) {
        withContext(Dispatchers.IO) {
            val iterator = ipList.iterator()
            while (iterator.hasNext()) {
                val ip = iterator.next()
                if (!refusedList.contains(ip)) {
                    val manualIp = etManualIP.text.toString()
                    val usedIp = if (cbManualIP.isChecked && manualIp.isNotEmpty()) manualIp else ip
                    try {
                        val socket = Socket(usedIp, 8000)
                        val isConnected = testConnection(ip)
                        if (!isConnected) {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Connection for IP $usedIp is not valid", Toast.LENGTH_SHORT).show()
                            }
                            iterator.remove()
                        }
                        with(sharedPreferences.edit()) {
                            putString("server_ip", ip)
                            apply()
                        }
                        val dos = DataOutputStream(socket.getOutputStream())
                        dos.writeUTF(text)
                        dos.flush()
                        dos.close()
                        socket.close()
                    } catch (e: ConnectException) {
                        if (e.toString().contains("Connection refused")) {
                            Log.d("Network", "REFUSED FOR IP : $ip")
                            refusedList.add(ip)
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Connection for IP $usedIp refused", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: UnknownHostException) {
                        e.printStackTrace()
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "INVALID IP", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
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
                for (i in 1..254) {
                    ip[3] = i.toByte()
                    val address: InetAddress = InetAddress.getByAddress(ip)
                    if (address.isReachable(100)) {
                        Log.d("Network", "$address machine is turned on and can be pinged")
                        ipList.add(address.toString().drop(1))
                    } else if (!address.hostAddress?.equals(address.hostName)!!) {
                        Log.d("Network", "$address machine is known in a DNS lookup")
                    }
                }
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