package com.home.tvhome;

import android.app.admin.DeviceAdminReceiver;

/** Lets TV Home be a device administrator, which only stops it being uninstalled by accident. It asks for no policies. */
public class AdminReceiver extends DeviceAdminReceiver { }
