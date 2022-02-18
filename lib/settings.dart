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

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'storage.dart';

// Wrapper to access settings of the app
class Settings {
  // Singleton
  static final Settings _settings = Settings._internal();

  Settings._internal();

  factory Settings() {
    return _settings;
  }

  // Settings
  Map<String, Object> storage = {};

  // Constructor
  void init() {
    initDefault();

    // limit (in decibel)
    final double? limit = Storage.local().getDouble('limit');
    if (limit != null) {
      storage["limit"] = limit;
    }

    // interval (in millisecond)
    final int? interval = Storage.local().getInt('interval');
    if (interval != null) {
      storage["interval"] = interval;
    }

    // useFlashLight (in millisecond)
    final bool? useFlashLight = Storage.local().getBool('useFlashLight');
    if (useFlashLight != null) {
      storage["useFlashLight"] = useFlashLight;
    }

    // useAlarm (in millisecond)
    final bool? useAlarm = Storage.local().getBool('useAlarm');
    if (useAlarm != null) {
      storage["useAlarm"] = useAlarm;
    }

    // timeBetweenAlert (in seconds)
    final int? timeBetweenAlert = Storage.local().getInt('timeBetweenAlert');
    if (timeBetweenAlert != null) {
      storage["timeBetweenAlert"] = timeBetweenAlert;
    }
  }

  // Destructor
  void save() {
    Storage.local().setDouble('limit', storage["limit"]! as double);
    Storage.local().setInt('interval', storage["interval"]! as int);
    Storage.local().setBool('useFlashLight', storage["useFlashLight"]! as bool);
    Storage.local().setBool('useAlarm', storage["useAlarm"]! as bool);
    Storage.local().setInt('timeBetweenAlert', storage["timeBetweenAlert"]! as int);
  }

  // Init default values
  void initDefault() {
    storage["limit"] = 50.0; // dB
    storage["interval"] = 1000; // milliseconds
    storage["useFlashLight"] = true;
    storage["useAlarm"] = true;
    storage["timeBetweenAlert"] = 5; // seconds
  }

  // Getters
  double getDouble(String key) {
    return storage[key]! as double;
  }

  int getInt(String key) {
    return storage[key]! as int;
  }

  bool getBool(String key) {
    return storage[key]! as bool;
  }
}

// Settings page
class SettingsPage extends StatefulWidget {
  const SettingsPage({Key? key}) : super(key: key);

  @override
  _SettingsPageState createState() => _SettingsPageState();
}

// Settings page implementation
class _SettingsPageState extends State<SettingsPage> {
  // Constructor
  @override
  void initState() {
    super.initState();
  }

  // Destructor
  @override
  void dispose() {
    super.dispose();
  }

  // Build settings page
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        actions: <Widget>[
          IconButton(
            icon: const Icon(Icons.settings),
            tooltip: 'Settings',
            onPressed: () {},
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(20.0),
        child: ListView(
          children: [
            TextField(
              controller: TextEditingController(
                  text: Settings().storage["limit"].toString()),
              decoration: const InputDecoration(
                  labelText: "Enter noise limit (decibels)"),
              keyboardType: TextInputType.number,
              inputFormatters: <TextInputFormatter>[
                FilteringTextInputFormatter.allow(RegExp(r'^(\d+)?\.?\d{0,2}'))
              ],
              onSubmitted: (String value) async {
                Settings().storage["limit"] = double.parse(value);
              },
            ),
            TextField(
              controller: TextEditingController(
                  text: Settings().storage["interval"].toString()),
              decoration: const InputDecoration(
                  labelText: "Audio level update inerval (milliseconds)"),
              keyboardType: TextInputType.number,
              inputFormatters: <TextInputFormatter>[
                FilteringTextInputFormatter.digitsOnly
              ],
              onSubmitted: (String value) async {
                Settings().storage["interval"] = int.parse(value);
              },
            ),
            TextField(
              controller: TextEditingController(
                  text: Settings().storage["timeBetweenAlert"].toString()),
              decoration: const InputDecoration(
                  labelText: "Minimum time between alerts (seconds)"),
              keyboardType: TextInputType.number,
              inputFormatters: <TextInputFormatter>[
                FilteringTextInputFormatter.digitsOnly
              ],
              onSubmitted: (String value) async {
                Settings().storage["timeBetweenAlert"] = int.parse(value);
              },
            ),
            CheckboxListTile(
              title: const Text("Use flash light when alert mode"),
              value: Settings().getBool("useFlashLight"),
              onChanged: (bool? value) {
                setState(() {
                  Settings().storage["useFlashLight"] = value!;
                });
              },
            ),
            CheckboxListTile(
              title: const Text("Use alarm sound when alert mode"),
              value: Settings().getBool("useAlarm"),
              onChanged: (bool? value) {
                setState(() {
                  Settings().storage["useAlarm"] = value!;
                });
              },
            ),
          ],
        ),
      ),
    );
  }
}
