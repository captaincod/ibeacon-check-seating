package com.example.beaconcheckseating

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.MonitorNotifier

class MainActivity : AppCompatActivity() {
    lateinit var beaconListView: ListView
    lateinit var yourSeat: TextView
    lateinit var isRightSeat: TextView

    lateinit var beaconReferenceApplication: BeaconReferenceApp
    var alertDialog: AlertDialog? = null
    var neverAskAgainPermissions = ArrayList<String>()

    var beaconsList = mutableMapOf<String, Double>()
    var waitingData: Int = 0
    var placeIsDone: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        beaconReferenceApplication = application as BeaconReferenceApp

        checkPermissions()

        val regionViewModel = BeaconManager.getInstanceForApplication(this).getRegionViewModel(beaconReferenceApplication.region)
        regionViewModel.regionState.observe(this, monitoringObserver)
        regionViewModel.rangedBeacons.observe(this, rangingObserver)
        beaconListView = findViewById(R.id.beaconList)
        yourSeat = findViewById(R.id.your_seat)
        isRightSeat = findViewById(R.id.is_right_seat)

        val beaconManager = BeaconManager.getInstanceForApplication(this)
        beaconManager.startRangingBeacons(beaconReferenceApplication.region)
        beaconManager.startMonitoring(beaconReferenceApplication.region)
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.attention_title))
        builder.setMessage(getString(R.string.attention_msg))
        builder.setPositiveButton(getString(R.string.attention_ok), null)
        alertDialog?.dismiss()
        alertDialog = builder.create()
        alertDialog?.show()

        beaconListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayOf("--"))
    }

    override fun onPause() {
        Log.d(TAG, "onPause")
        super.onPause()
    }
    override fun onResume() {
        Log.d(TAG, "onResume")
        super.onResume()
        checkPermissions()
    }

    val monitoringObserver = Observer<Int> { state ->
        var dialogTitle = getString(R.string.beacons_found)
        var dialogMessage = getString(R.string.beacons_found_msg)
        var stateString = "inside"
        if (state == MonitorNotifier.OUTSIDE) {
            dialogTitle = getString(R.string.beacons_not_found)
            dialogMessage = getString(R.string.beacons_not_found_msg)
            stateString = "outside"
            beaconListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayOf("--"))
        }
        Log.d(TAG, "monitoring state changed to : $stateString")
        val builder =
            AlertDialog.Builder(this)
        builder.setTitle(dialogTitle)
        builder.setMessage(dialogMessage)
        builder.setPositiveButton(getString(R.string.attention_ok), null)
        alertDialog?.dismiss()
        alertDialog = builder.create()
        alertDialog?.show()
    }

    val rangingObserver = Observer<Collection<Beacon>> { beacons ->
        Log.d(TAG, "Ranged: ${beacons.count()} beacons")
        if (BeaconManager.getInstanceForApplication(this).rangedRegions.isNotEmpty()) {
            val oldSize = beaconsList.size
            for (beacon in beacons){
                beaconsList[beacon.id3.toString()] = beacon.distance
            }
            if (!placeIsDone){
                if (beaconsList.size == oldSize && waitingData > 10){
                    val minorIndices = beaconsList.keys
                    yourSeat.text = minorIndices.asSequence().shuffled().find { true }
                    placeIsDone = true
                }
            } else {
                if (beaconsList[yourSeat.text]!! <= 0.5) {
                    isRightSeat.text = getString(R.string.right_seat)
                    isRightSeat.setBackgroundColor(getColor(R.color.right))
                } else {
                    isRightSeat.text =
                        getString(R.string.wrong_seat, String.format("%.3f", beaconsList[yourSeat.text]))
                    isRightSeat.setBackgroundColor(getColor(R.color.wrong))
                }
            }
            beaconListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            beaconsList
                .map { "Номер: ${it.key}\nРасстояние до Вас: ${String.format("%.3f", it.value)} метров" })

            waitingData += 1
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for (i in 1 until permissions.size) {
            Log.d(TAG, "onRequestPermissionResult for "+permissions[i]+":" +grantResults[i])
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                if (!shouldShowRequestPermissionRationale(permissions[i])) {
                    neverAskAgainPermissions.add(permissions[i])
                }
            }
        }
    }

    fun checkPermissions() {
        var permissions = arrayOf( Manifest.permission.ACCESS_FINE_LOCATION)
        var permissionRationale = getString(R.string.loc)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = arrayOf( Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN)
            permissionRationale = getString(R.string.loc_n_blue)
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if ((checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
                permissions = arrayOf( Manifest.permission.ACCESS_FINE_LOCATION)
                permissionRationale = getString(R.string.loc)
            }
            else {
                permissions = arrayOf( Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                permissionRationale = getString(R.string.loc)
            }
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions = arrayOf( Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            permissionRationale = getString(R.string.loc)
        }
        var allGranted = true
        for (permission in permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) allGranted = false
        }
        if (!allGranted) {
            if (neverAskAgainPermissions.count() == 0) {
                val builder =
                    AlertDialog.Builder(this)
                builder.setTitle(getString(R.string.ask_permissions))
                builder.setMessage(permissionRationale)
                builder.setPositiveButton(getString(R.string.attention_ok), null)
                builder.setOnDismissListener {
                    requestPermissions(
                        permissions,
                        PERMISSION_REQUEST_FINE_LOCATION
                    )
                }
                builder.show()
            }
            else {
                val builder =
                    AlertDialog.Builder(this)
                builder.setTitle(getString(R.string.limited_func))
                builder.setMessage(getString(R.string.go_settings))
                builder.setPositiveButton(getString(R.string.attention_ok), null)
                builder.setOnDismissListener { }
                builder.show()
            }
        }
        else {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                        val builder =
                            AlertDialog.Builder(this)
                        builder.setTitle(getString(R.string.ask_permissions))
                        builder.setMessage(getString(R.string.loc))
                        builder.setPositiveButton(getString(R.string.attention_ok), null)
                        builder.setOnDismissListener {
                            requestPermissions(
                                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                                PERMISSION_REQUEST_BACKGROUND_LOCATION
                            )
                        }
                        builder.show()
                    } else {
                        val builder =
                            AlertDialog.Builder(this)
                        builder.setTitle(getString(R.string.limited_func))
                        builder.setMessage(getString(R.string.go_settings))
                        builder.setPositiveButton(getString(R.string.attention_ok), null)
                        builder.setOnDismissListener { }
                        builder.show()
                    }
                }
            }
            else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S &&
                (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                        != PackageManager.PERMISSION_GRANTED)) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_SCAN)) {
                    val builder =
                        AlertDialog.Builder(this)
                    builder.setTitle(getString(R.string.ask_permissions))
                    builder.setMessage(getString(R.string.blue))
                    builder.setPositiveButton(getString(R.string.attention_ok), null)
                    builder.setOnDismissListener {
                        requestPermissions(
                            arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                            PERMISSION_REQUEST_BLUETOOTH_SCAN
                        )
                    }
                    builder.show()
                } else {
                    val builder =
                        AlertDialog.Builder(this)
                    builder.setTitle(getString(R.string.limited_func))
                    builder.setMessage(getString(R.string.go_settings))
                    builder.setPositiveButton(getString(R.string.attention_ok), null)
                    builder.setOnDismissListener { }
                    builder.show()
                }
            }
            else {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                            val builder =
                                AlertDialog.Builder(this)
                            builder.setTitle(getString(R.string.ask_permissions))
                            builder.setMessage(getString(R.string.loc))
                            builder.setPositiveButton(getString(R.string.attention_ok), null)
                            builder.setOnDismissListener {
                                requestPermissions(
                                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                                    PERMISSION_REQUEST_BACKGROUND_LOCATION
                                )
                            }
                            builder.show()
                        } else {
                            val builder =
                                AlertDialog.Builder(this)
                            builder.setTitle(getString(R.string.limited_func))
                            builder.setMessage(getString(R.string.go_settings))
                            builder.setPositiveButton(getString(R.string.attention_ok), null)
                            builder.setOnDismissListener { }
                            builder.show()
                        }
                    }
                }
            }
        }

    }

    companion object {
        const val TAG = "MainActivity"
        const val PERMISSION_REQUEST_BACKGROUND_LOCATION = 0
        const val PERMISSION_REQUEST_BLUETOOTH_SCAN = 1
        const val PERMISSION_REQUEST_BLUETOOTH_CONNECT = 2
        const val PERMISSION_REQUEST_FINE_LOCATION = 3
    }

}