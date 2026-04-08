package com.github.nrfr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ShizukuNotReadyScreen(hasPhoneStatePermission: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (hasPhoneStatePermission) "需要 Shizuku 权限" else "需要电话权限",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (hasPhoneStatePermission) {
                "请安装并启用 Shizuku，然后重启应用"
            } else {
                "请允许读取电话状态权限，否则无法读取 SIM 卡信息"
            },
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
