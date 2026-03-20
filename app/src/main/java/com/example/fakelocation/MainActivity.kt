package com.example.fakelocation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.fakelocation.databinding.ActivityMainBinding
import java.util.Timer
import java.util.TimerTask
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationManager: LocationManager
    private val providerName = LocationManager.GPS_PROVIDER
    private var updateTimer: Timer? = null

    private var currentLon: Double = 103.986258
    private var currentLat: Double = 30.580374
    private var currentName: String = "未开始"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        checkLocationPermission()
        initMockProvider()

        //
        binding.btnField1.setOnClickListener { startMock(103.986258, 30.580374, "第一田径场") }
        binding.btnField2.setOnClickListener { startMock(103.987241, 30.583477, "第二田径场") }
        binding.btnGym.setOnClickListener { startMock(103.988838, 30.583366, "体育馆") }
        binding.btnBasketball1.setOnClickListener { startMock(103.985773, 30.581551, "第一篮球场") }
        binding.btnBasketball2.setOnClickListener { startMock(103.985779, 30.582454, "第二篮球场") }
        binding.btnBadminton.setOnClickListener { startMock(103.985687, 30.583347, "羽毛球场") }

        binding.btnApplyCustom.setOnClickListener {
            val lon = binding.etLon.text.toString().toDoubleOrNull()
            val lat = binding.etLat.text.toString().toDoubleOrNull()
            if (lon != null && lat != null) {
                startMock(lon, lat, "自定义位置")
            } else {
                Toast.makeText(this, "请输入正确的经纬度", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initMockProvider() {
        try {
            locationManager.removeTestProvider(providerName)
        } catch (e: Exception) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val properties = ProviderProperties.Builder()
                    .setHasSpeedSupport(true)
                    .setHasSpeedSupport(true)
                    .setHasBearingSupport(true)
                    .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                    .setAccuracy(ProviderProperties.ACCURACY_FINE)
                    .build()
                locationManager.addTestProvider(providerName, properties)
            } else {
                locationManager.addTestProvider(providerName, false, false, false, false, true, true, true, 1, 1)
            }
            locationManager.setTestProviderEnabled(providerName, true)
        } catch (e: Exception) {
            binding.tvStatus.text = "初始化失败：请检查开发者选项设置"
        }
    }

    private fun startMock(lon: Double, lat: Double, name: String) {
        // 核心步骤：将高德坐标转为 GPS 坐标，消除几百米的偏差
        val wgsCoord = gcj02ToWgs84(lon, lat)
        currentLon = wgsCoord[0]
        currentLat = wgsCoord[1]
        currentName = name

        updateTimer?.cancel()
        updateTimer = Timer()

        updateTimer?.schedule(object : TimerTask() {
            override fun run() {
                // 精度增强：模拟极微小的抖动（0.000001度约为0.1米），增加真实感
                val jitterLat = currentLat + (Random.nextDouble() - 0.5) * 0.000002
                val jitterLon = currentLon + (Random.nextDouble() - 0.5) * 0.000002

                val mockLocation = Location(providerName).apply {
                    latitude = jitterLat
                    longitude = jitterLon
                    altitude = 35.0
                    accuracy = 1.0f // 1米精度
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        verticalAccuracyMeters = 0.5f
                        speedAccuracyMetersPerSecond = 0.1f
                        bearingAccuracyDegrees = 0.1f
                    }
                }

                try {
                    locationManager.setTestProviderLocation(providerName, mockLocation)
                    runOnUiThread {
                        binding.tvStatus.text = "运行中: $currentName\n(已自动校准火星坐标偏差)"
                    }
                } catch (e: Exception) {}
            }
        }, 0, 1000)

        Toast.makeText(this, "正在模拟 $name", Toast.LENGTH_SHORT).show()
    }

    // --- 坐标转换工具函数：GCJ-02 To WGS-84 ---
    private fun gcj02ToWgs84(lng: Double, lat: Double): DoubleArray {
        val dlat = transformLat(lng - 105.0, lat - 35.0)
        val dlng = transformLng(lng - 105.0, lat - 35.0)
        val radlat = lat / 180.0 * PI
        var magic = sin(radlat)
        magic = 1 - 0.00669342162296594323 * magic * magic
        val sqrtmagic = sqrt(magic)
        val mLat = dlat * 180.0 / (6335552.717000426 / (magic * sqrtmagic) * PI)
        val mLng = dlng * 180.0 / (6378137.0 / sqrtmagic * cos(radlat) * PI)
        return doubleArrayOf(lng - mLng, lat - mLat)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }

    private fun checkLocationPermission() {
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (ActivityCompat.checkSelfPermission(this, permissions[0]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, 1001)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateTimer?.cancel()
    }
}
// 辅助随机类
object Random {
    fun nextDouble() = java.util.Random().nextDouble()
}