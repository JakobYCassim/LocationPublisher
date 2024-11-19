package com.example.locationpublisher


import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import org.json.JSONObject


class MainActivity : AppCompatActivity()  {


    private val locationRequestCode = 1392
    private var isPublishing: Boolean = false
    private var client: Mqtt5BlockingClient? = null
    private var isConnected = false
    private lateinit var locationTextView: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var studentIdText: EditText


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        locationTextView = findViewById(R.id.LocationStatus)
        studentIdText = findViewById(R.id.etStudentId)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                handleLocationResult(locationResult)
            }
        }



    }





    @Suppress("UNUSED_PARAMETER")
    fun startPublishing(view: View) {
        if (!isPublishing) {
            if (studentIdText.text.isNullOrEmpty()) {
                Toast.makeText(this, "Please enter a student Id", Toast.LENGTH_SHORT).show()
                return
            }
            studentIdText.isEnabled = false
            setupMqttClient()
            if (isConnected) {
                isPublishing = true
                requestLocationPermissions()
            } else {
                Toast.makeText(this, "Could not connect to broker", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Already Publishing", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMqttClient() {
        client = Mqtt5Client.builder()
            .identifier(studentIdText.text.toString())
            .serverHost("broker-816041437.sundaebytestt.com")
            .serverPort(1883)
            .build()
            .toBlocking()

        try {
            client?.connect()
            Log.d("CLIENT", "Connected to broker")
            isConnected = true
        } catch (e: Exception) {
            Log.e("CLIENT", "MQTT Connection Failed")
            isConnected = false
        }

    }

    fun handleLocationResult(locationResult: LocationResult) {
        locationResult.locations.forEach { location ->
            publishLocation(location)
        }
    }

    private fun publishLocation(location: Location) {
        if (isConnected) {
            val topic = "assignment/location"
            val messageContent = createMessage(location)

            locationTextView.text = messageContent
            try {
                client?.publishWith()?.topic(topic)?.payload(messageContent.toByteArray(Charsets.UTF_8))?.send()
                Log.d("CLIENT", "Published Location to broker")
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "An error occurred while sending a message to the broker",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun createMessage(location: Location): String {
        val jsonMessage = JSONObject()
        jsonMessage.put("student_id", studentIdText.text.toString())
        jsonMessage.put("latitude", location.latitude)
        jsonMessage.put("longitude", location.longitude)
        jsonMessage.put("timestamp", System.currentTimeMillis())

        return jsonMessage.toString()
    }

    @Suppress("UNUSED_PARAMETER")
    @SuppressLint("SetTextI18n")
    fun stopPublishing(view: View) {
        if (isPublishing) {
            isPublishing = false
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Toast.makeText(this, "Stopped Publishing Location", Toast.LENGTH_SHORT).show()
            disconnectMqtt()
            locationTextView.text = "Not Publishing"
            studentIdText.isEnabled = true
        }
    }

    private fun disconnectMqtt() {
        if (isConnected) {
            try {
                client?.disconnect()
                Log.d("CLIENT", "Disconnected from broker")
                isConnected = false
            } catch (e: Exception) {
                Log.e("CLIENT", "Error while attempting to disconnect")
            }
        } else {
            Log.e("CLIENT", "Client was not connected")
        }
    }


    private fun requestLocationPermissions() {
        // Check for foreground location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationRequestCode
            )
        } else {
            startLocationUpdates()
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start location updates
                startLocationUpdates()
            } else {
                // Permission denied
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }


    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        Toast.makeText(this, "Started Publishing Location", Toast.LENGTH_SHORT).show()
        val locationRequest = LocationRequest.Builder(5000)
            .setMinUpdateDistanceMeters(10f)
            .setMinUpdateIntervalMillis(2000)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    override fun onPause(){
        super.onPause()
        if (isPublishing) fusedLocationClient.removeLocationUpdates(locationCallback)
        isPublishing = false
        disconnectMqtt()
        locationTextView.text="Not Publishing"
        studentIdText.isEnabled = true
    }

    override fun onResume() {
        super.onResume()
        Toast.makeText(this,"You were disconnected to save power, please reconnect",
            Toast.LENGTH_SHORT).show()
    }
    override fun onDestroy() {
        super.onDestroy()
        if (isPublishing) fusedLocationClient.removeLocationUpdates(locationCallback)
        isPublishing = false
        disconnectMqtt()

    }
}


