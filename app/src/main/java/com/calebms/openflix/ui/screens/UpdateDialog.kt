package com.calebms.openflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.calebms.openflix.data.remote.updater.AppUpdateInfo

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit,
    onDownloadConfirm: () -> Unit
) {
    Dialog(onDismissRequest = { if (!updateInfo.forceUpdate) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF262626),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Update Available",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Version ${updateInfo.versionName}", color = Color(0xFF46D369), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = updateInfo.fileSize, color = Color.Gray, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFF333333))
                Spacer(modifier = Modifier.height(16.dp))

                Text("What's New:", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                updateInfo.changelog.forEach { log ->
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("•", color = Color.Gray, modifier = Modifier.padding(end = 8.dp))
                        Text(log, color = Color.LightGray, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        onDownloadConfirm()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Download & Install", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                if (!updateInfo.forceUpdate) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Maybe Later", color = Color.Gray)
                    }
                }
            }
        }
    }
}