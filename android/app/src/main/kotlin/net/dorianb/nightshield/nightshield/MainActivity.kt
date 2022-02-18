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
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.Manifest
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import android.content.Context
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    // Method channel for Flutter
    private val CHANNEL = "net.dorianb.nightshield"

    // Listening state
    private var listening = false

    // Audio value (decibels)
    private var value = 0.0

    // Saved record time (when permission popup)
    private var savedRecordTime = 0.0

    // Ringtone
    private var ringtone: Ringtone? = null

    // Configure method channels
    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Configure method channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->

            // Wrappers
            if (call.method == "startListening") {
                startListening(call.argument<Double>("recordTime")!!)
                result.success(true)
            } else if (call.method == "endListening") {
                endListening()
                result.success(true)
            } else if (call.method == "getAudioLevel") {
                val audioLevel = getAudioLevel()
                result.success(audioLevel)
            } else if (call.method == "enableFlashLight") {
                enableFlashLight()
                result.success(true)
            } else if (call.method == "disableFlashLight") {
                disableFlashLight()
                result.success(true)
            } else if (call.method == "playAlarm") {
                playAlarm()
                result.success(true)
            } else if (call.method == "stopAlarm") {
                stopAlarm()
                result.success(true)
            } else {
                result.notImplemented()
            }
        }
    }

    // Start listenning
    private fun startListening(recordTime: Double) {
        if (listening)
            return

        // Save record time
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

        // Start listening
        listening = true

        // Run thread
        Thread(Runnable {

            // Create buffer
            var buffer = ArrayList<Double>()

            // Setup
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val audioBuffer = ShortArray(6400)

            // Prepare record
            val record = AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,
                    44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    12800)

            // Start record
            record.startRecording()
            var startTime = System.currentTimeMillis().toDouble()

            // Record loop
            while (listening) {

                // Check time difference
                val currentTime = System.currentTimeMillis().toDouble()
                if (currentTime - startTime > recordTime) {
                    // Convert to sum
                    var res = 0.0
                    for (x in buffer) {
                        res = res + x
                    }

                    // Compute mean
                    var mean = res / buffer.size.toDouble();

                    // Compute DB value
                    value = 10 * Math.log10(mean)

                    // Clear buffer
                    buffer.clear()

                    // Reset start time
                    startTime = System.currentTimeMillis().toDouble()
                }

                // Read buffer
                record.read(audioBuffer, 0, audioBuffer.size)

                // Browse audio buffer
                for (x in audioBuffer) {
                    if (x.toInt() == 0)
                        continue

                    // Add to buffer
                    buffer.add(Math.abs(x.toDouble()))
                }
            }

            // Stop record
            record.stop()
            record.release()
        }).start()
    }

    // Stop listening
    private fun endListening() {

        // End/Stop listening
        listening = false
    }

    // Get latest computed audio level (in decibels)
    private fun getAudioLevel(): Double {
        return value
    }

    // Enable flash light
    private fun enableFlashLight() {
        var cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

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
        var cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

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
        var uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(context, uri)

        // Set volumne
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone!!.setVolume(1.0f)
        }

        // Set looping
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone!!.setLooping(true)
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

    // Permission results processing
    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            5001 -> {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startListening(savedRecordTime)
                } else {
                    Log.d("TAG", "audio permission denied by user")
                }
                return
            }
            5002 -> {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startListening(savedRecordTime)
                } else {
                    Log.d("TAG", "camera permission denied by user")
                }
                return
            }
        }
    }
}
