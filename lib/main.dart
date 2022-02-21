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

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'settings.dart';
import 'storage.dart';

// Entry point
void main() async {
  // Wait until flutter initialized
  WidgetsFlutterBinding.ensureInitialized();

  // Wait until storage initialized
  await Storage.init();

  // Run app
  runApp(const App());
}

// App implementation
class App extends StatelessWidget {
  const App({Key? key}) : super(key: key);

  // Build App
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Night Shield',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.light,
      ),
      darkTheme: ThemeData(
        brightness: Brightness.dark,
      ),
      // ThemeMode.system to follow system theme,
      // ThemeMode.light for light theme,
      // ThemeMode.dark for dark theme
      themeMode: ThemeMode.dark,
      home: const HomePage(title: 'Night Shield'),
    );
  }
}

// Home page
class HomePage extends StatefulWidget {
  const HomePage({Key? key, required this.title}) : super(key: key);

  final String title;

  @override
  State<HomePage> createState() => _HomePageState();
}

// Home page implementation
class _HomePageState extends State<HomePage> {
  // Current audio level
  double _audioLevel = 0.0;

  // Timer for audio level update
  Timer? _timer;

  // As the shield started
  bool _started = false;

  // In alert
  bool _alert = false;

  // Is the audio level greater than the decibel limit
  bool _alertZone = false;

  // If true, will not start a new alert
  bool _disableAlert = false;

  // Native code method channel
  static const platform = MethodChannel('net.dorianb.nightshield');

  // Constructor
  @override
  void initState() {
    super.initState();
    Settings().init();
  }

  // Destructor
  @override
  void dispose() {
    super.dispose();
    _stopShield();
    Settings().save();
  }

  // Notification (snack bar)
  void _notify(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  // Send notification (real notification)
  void _sendNotification(String title, String message) {
    platform
        .invokeMethod('sendNotification', {"title": title, "message": message});
  }

  // Update the audio level (called by the timer)
  Future<void> _updateAudioLevel() async {
    // Check if started
    if (!_started) {
      return;
    }

    // Invoke the native function
    final double result = await platform.invokeMethod('getAudioLevel');

    // Is in alert zone
    bool alertZone;

    // Test if greater than the decibel limit
    if (result > Settings().getDouble("limit")) {
      alertZone = true;
      _startAlert();
    } else {
      alertZone = false;
    }

    // Update the current state
    setState(() {
      _audioLevel = result;
      _alertZone = alertZone;
    });
  }

  // Start the alert system
  void _startAlert() {
    if (_alert || _disableAlert) {
      return;
    }

    _sendNotification("Alert", "Alert triggered.");

    // Enable flash light
    if (Settings().getBool("useFlashLight")) {
      platform.invokeMethod('enableFlashLight');
    }

    // Enable alarm (sound)
    if (Settings().getBool("useAlarm")) {
      platform.invokeMethod('playAlarm');
    }

    // Update state
    setState(() {
      _alert = true;
    });
  }

  // Stop the alert system
  void _stopAlert() {
    if (!_alert) {
      return;
    }

    _notify('End of alert.');

    // Invoke native method to disable flash light
    platform.invokeMethod('disableFlashLight');

    // Invoke native method to disable alarm (sound)
    platform.invokeMethod('stopAlarm');

    // Disable alert for a given time
    _disableAlert = true;
    Future.delayed(Duration(seconds: Settings().getInt("timeBetweenAlert")), () {
      _disableAlert = false;
    });

    // Update state
    setState(() {
      _alert = false;
    });
  }

  // Start the update loop and audio level check
  void _startShield() {
    if (_started) {
      return;
    }

    // Invoke native method to start listening
    platform.invokeMethod(
        'startListening', {"recordTime": Settings().getInt("interval")});

    // Prepare update timer
    _timer = Timer.periodic(
        Duration(milliseconds: Settings().getInt("interval")),
        (Timer t) => _updateAudioLevel());

    _notify('Started shield.');

    // Update state
    setState(() {
      _started = true;
    });
  }

  // Stop the update loop and audio level check
  void _stopShield() {
    if (!_started) {
      return;
    }

    // Invoke native method to stop listening
    platform.invokeMethod('endListening');

    // Invoke native method to disable flash light
    platform.invokeMethod('disableFlashLight');

    // Invoke native method to disable alarm (sound)
    platform.invokeMethod('stopAlarm');

    // Cancel timer
    _timer?.cancel();

    _stopAlert();
    _notify('Stopped shield.');

    // Update state
    setState(() {
      _started = false;
    });
  }

  // Toggle the shield protection
  void _toggleShield() {
    if (_started) {
      _stopShield();
    } else {
      _startShield();
    }
  }

  // Build Home
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
        actions: <Widget>[
          IconButton(
            icon: const Icon(Icons.settings),
            tooltip: 'Settings',
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => const SettingsPage()),
              );
              Settings().save();
            },
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            const Text(
              'Current audio level (in decibels):',
            ),
            const SizedBox(height: 15),
            LinearProgressIndicator(
              value: _audioLevel / 100.0,
              semanticsLabel: 'Linear progress indicator',
              valueColor: _alertZone
                  ? const AlwaysStoppedAnimation<Color>(Colors.red)
                  : const AlwaysStoppedAnimation<Color>(Colors.green),
              minHeight: 10,
            ),
            const SizedBox(height: 5),
            Text(
              _started
                  ? _audioLevel.toStringAsFixed(1) + ' dB'
                  : 'Shield is not active',
              style: Theme.of(context).textTheme.headline6,
            ),
            const SizedBox(height: 100),
            ElevatedButton(
              style: ElevatedButton.styleFrom(
                minimumSize: const Size(300, 300),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(1000.0),
                ),
              ),
              onPressed: !_alert
                  ? null
                  : () {
                      _stopAlert();
                    },
              child: const Text('STOP'),
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _toggleShield,
        backgroundColor: _started ? Colors.green : Colors.red,
        tooltip: 'Start Shield',
        child: const Icon(Icons.shield),
      ),
    );
  }
}
