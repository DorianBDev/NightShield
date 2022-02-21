/// MIT License
///
/// Copyright (c) 2022 Dorian Bachelot
///
/// Permission is hereby granted, free of charge, to any person obtaining
/// a copy of this software and associated documentation files (the
/// "Software"), to deal in the Software without restriction, including
/// without limitation the rights to use, copy, modify, merge, publish,
/// distribute, sublicense, and/or sell copies of the Software, and to
/// permit persons to whom the Software is furnished to do so, subject to
/// the following conditions:
///
/// The above copyright notice and this permission notice shall be
/// included in all copies or substantial portions of the Software.
///
/// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
/// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
/// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
/// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
/// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
/// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
/// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package net.dorianb.nightshield.nightshield

import androidx.annotation.NonNull
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import android.Manifest
import android.annotation.SuppressLint
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import android.content.Intent
import android.provider.Settings
import android.app.NotificationManager
import android.app.Notification
import android.app.NotificationChannel
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlin.math.abs
import kotlin.math.log10

import net.dorianb.nightshield.nightshield.AudioService

class MainActivity : FlutterActivity() {

    // Method channel for Flutter
    private val CHANNEL = "net.dorianb.nightshield"

    // Saved record time (when permission popup)
    private var savedRecordTime = 0.0

    // Ringtone
    private var ringtone: Ringtone? = null

    // Wake lock
    private var wakeLock: PowerManager.WakeLock? = null

    // Server
    private lateinit var service: AudioService

    // Is the service bound
    private var serviceBound = false

    // Bind Audio Service
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AudioService.AudioBinder
            this@MainActivity.service = binder.getService()
            serviceBound = true

            // Set parameter
            this@MainActivity.service.recordTime = savedRecordTime
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            serviceBound = false
        }
    }

    // Configure method channels
    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        createNotificationChannel()

        // Configure method channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->

            // Wrappers
            when (call.method) {
                "startListening" -> {
                    startListening(call.argument<Double>("recordTime")!!)
                    result.success(true)
                }
                "endListening" -> {
                    endListening()
                    result.success(true)
                }
                "getAudioLevel" -> {
                    val audioLevel = getAudioLevel()
                    result.success(audioLevel)
                }
                "enableFlashLight" -> {
                    enableFlashLight()
                    result.success(true)
                }
                "disableFlashLight" -> {
                    disableFlashLight()
                    result.success(true)
                }
                "playAlarm" -> {
                    playAlarm()
                    result.success(true)
                }
                "stopAlarm" -> {
                    stopAlarm()
                    result.success(true)
                }
                "sendNotification" -> {
                    sendNotification(call.argument<String>("title")!!, call.argument<String>("message")!!)
                    result.success(true)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    // Start listenning
    private fun startListening(recordTime: Double) {
        if (serviceBound)
            return

        // Acquire wake lock
        // Reference: https://developer.android.com/training/scheduling/wakelock#cpu
        if (wakeLock == null) {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NightShield::WakelockTag").apply {
                    acquire(10 * 60 * 1000L /* 10 minutes */)
                }
            }
        }

        savedRecordTime = recordTime

        // Check permissions for record audio
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 5001)
            return
        }

        // Check permissions for camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 5002)
            return
        }

        // Check permissions for foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.FOREGROUND_SERVICE), 5003)
                return
            }
        }

        // Check permissions for wake lock
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WAKE_LOCK) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WAKE_LOCK), 5004)
            return
        }

        // Check permissions for request ignore battery optimizations
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS), 5005)
            return
        }

        checkBatteryOptimizations()
        startAudioService()
    }

    // Stop listening
    private fun endListening() {

        // Release wave lock
        if (wakeLock != null) {
            wakeLock?.release()
            wakeLock = null
        }

        stopAudioService()
    }

    // Get latest computed audio level (in decibels)
    private fun getAudioLevel(): Double {
        if (!serviceBound)
            return 0.0

        return service.value
    }

    // Enable flash light
    private fun enableFlashLight() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Check if the device has a camera
        if (cameraManager.cameraIdList.isEmpty()) {
            Log.d("TAG", "no camera on device")
            return
        }

        // Check if the device has a flash light
        val hasFlash = cameraManager.getCameraCharacteristics("0").get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
        if (hasFlash != true) {
            Log.d("TAG", "no flash light on device")
            return
        }

        // Enable flash light
        val cameraListId = cameraManager.cameraIdList[0]
        cameraManager.setTorchMode(cameraListId, true)
    }

    // Disable flash light
    private fun disableFlashLight() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Check if the device has a camera
        if (cameraManager.cameraIdList.isEmpty()) {
            Log.d("TAG", "no camera on device")
            return
        }

        // Check if the device has a flash light
        val hasFlash = cameraManager.getCameraCharacteristics("0").get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
        if (hasFlash != true) {
            Log.d("TAG", "no flash light on device")
            return
        }

        // Disable flash light
        val cameraListId = cameraManager.cameraIdList[0]
        cameraManager.setTorchMode(cameraListId, false)
    }

    // Play alarm
    private fun playAlarm() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(context, uri)

        // Set volumne
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone!!.volume = 1.0f
        }

        // Set looping
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone!!.isLooping = true
        }

        // Start playing
        ringtone!!.play()
    }

    // Stop alarm
    private fun stopAlarm() {
        if (ringtone != null) {
            ringtone!!.stop()
            ringtone = null
        }
    }

    // Check if there is any battery optimizations for this app
    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.applicationContext.packageName)
    }

    // Check if there is any battery optimizations for this app, if yes will ask user to disable it
    private fun checkBatteryOptimizations() {
        if (!isIgnoringBatteryOptimizations()) {
            val name = applicationInfo.loadLabel(packageManager).toString()

            // Show toast
            Toast.makeText(applicationContext, "Battery optimization -> All apps -> $name -> Don't optimize", Toast.LENGTH_LONG).show()

            // Show notification
            sendNotification("Battery optimization", "Battery optimization -> All apps -> $name -> Don't optimize")

            // Go to settings
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        }
    }

    // Create notification channel
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, "NightShield", NotificationManager.IMPORTANCE_HIGH)

            // Register the channel with the system
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Send a notification
    private fun sendNotification(title: String, message: String) {

        // TODO: real app logo
        //val fd = registrar.context().getAssets().openFd(registrar.lookupKeyForAsset("assets/logo.png"))

        // Prepare notification
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(R.mipmap.ic_launcher) // TODO: real app logo
        } else {
            Notification.Builder(this)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(R.mipmap.ic_launcher) // TODO: real app logo
        }

        // Send notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(6001, builder.build())
    }

    // Create and bind Audio Service
    private fun startAudioService() {
        if (serviceBound)
            return

        Intent(this, AudioService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    // Unbind and stop Audio Service
    private fun stopAudioService() {
        if (!serviceBound)
            return

        service.listening = false
        unbindService(connection)
        serviceBound = false
    }

    // Permission results processing
    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            5001 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startListening(savedRecordTime)
                } else {
                    Log.d("TAG", "audio permission denied by user")
                }
                return
            }
            5002 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startListening(savedRecordTime)
                } else {
                    Log.d("TAG", "camera permission denied by user")
                }
                return
            }
            5003 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startListening(savedRecordTime)
                } else {
                    Log.d("TAG", "foreground service permission denied by user")
                }
                return
            }
            5004 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startListening(savedRecordTime)
                } else {
                    Log.d("TAG", "wake lock permission denied by user")
                }
                return
            }
            5005 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startListening(savedRecordTime)
                } else {
                    Log.d("TAG", "request ignore battery optimizations permission denied by user")
                }
                return
            }
        }
    }
}
