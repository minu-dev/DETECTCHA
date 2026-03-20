package com.example.detectcha

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 알림 접근 권한이 있는지 확인함
        if (!isNotificationPermissionGranted()) {
            // 권한 없으면 토스트 띄움
            Toast.makeText(this, "알림 접근 권한을 허용해주세요.", Toast.LENGTH_LONG).show()

            // 설정의 알림 접근 허용 창으로 이동함
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }
    }

    // 권한 체크
    private fun isNotificationPermissionGranted(): Boolean {
        val packageName = packageName
        val sets = NotificationManagerCompat.getEnabledListenerPackages(this)
        return sets.contains(packageName)
    }
}