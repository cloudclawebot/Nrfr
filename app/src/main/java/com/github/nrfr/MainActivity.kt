package com.github.nrfr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.nrfr.ui.screens.AboutScreen
import com.github.nrfr.ui.screens.MainScreen
import com.github.nrfr.ui.screens.ShizukuNotReadyScreen
import com.github.nrfr.ui.theme.NrfrTheme
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private var isShizukuReady by mutableStateOf(false)
    private var showAbout by mutableStateOf(false)

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        isShizukuReady = grantResult == PackageManager.PERMISSION_GRANTED
        if (!isShizukuReady) {
            Toast.makeText(this, "需要 Shizuku 权限才能运行", Toast.LENGTH_LONG).show()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkShizukuStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")

        ensurePhoneStatePermission()
        checkShizukuStatus()

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)

        setContent {
            NrfrTheme {
                if (showAbout) {
                    AboutScreen(onBack = { showAbout = false })
                } else if (isShizukuReady) {
                    MainScreen(onShowAbout = { showAbout = true })
                } else {
                    ShizukuNotReadyScreen()
                }
            }
        }
    }

    private fun ensurePhoneStatePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_STATE), 1001)
        }
    }

    private fun checkShizukuStatus() {
        isShizukuReady = if (Shizuku.getBinder() == null) {
            Toast.makeText(this, "请先安装并启用 Shizuku", Toast.LENGTH_LONG).show()
            false
        } else {
            val hasPermission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Shizuku.requestPermission(0)
            }
            hasPermission
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
    }
}
