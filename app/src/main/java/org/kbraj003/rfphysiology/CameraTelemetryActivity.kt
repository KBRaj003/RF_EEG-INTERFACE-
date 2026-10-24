package org.kbraj003.rfphysiology

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class CameraTelemetryActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var overlay: TextView
    private var provider: ProcessCameraProvider? = null
    private val handler = Handler(Looper.getMainLooper())

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val allGranted = requiredPermissions.all { permission ->
                results[permission] == true ||
                    ContextCompat.checkSelfPermission(
                        this, permission
                    ) == PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) {
                startCamera()
                refreshTelemetry()
            } else {
                overlay.text =
                    "Permissions denied. Grant camera, phone state and " +
                    "location permissions to use this screen."
            }
        }

    private val poller = object : Runnable {
        override fun run() {
            refreshTelemetry()
            handler.postDelayed(this, 3000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Camera + LTE Telemetry"

        val root = FrameLayout(this)
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(
            previewView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        overlay = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(18, 18, 18, 18)
            setBackgroundColor(0xB8000000.toInt())
            text = "Requesting permissions..."
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        )
        params.setMargins(12, 12, 12, 12)
        root.addView(overlay, params)
        setContentView(root)

        if (hasPermissions()) {
            startCamera()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun hasPermissions(): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
                )
            } catch (e: Exception) {
                overlay.text = "Camera unavailable: ${e.javaClass.simpleName}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun valueOrUnavailable(value: Int): String {
        return if (value == Int.MAX_VALUE || value == Int.MIN_VALUE) {
            "Unavailable"
        } else {
            value.toString()
        }
    }

    private fun refreshTelemetry() {
        if (!hasPermissions()) {
            overlay.text = "Camera/LTE permissions are not granted."
            return
        }

        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val cells = tm.allCellInfo
            if (cells.isNullOrEmpty()) {
                overlay.text =
                    "LIVE CAMERA + MODEM TELEMETRY\n" +
                    "Android has not exposed cell records yet.\n" +
                    "EEG: NOT MEASURED"
                return
            }

            val lines = mutableListOf(
                "LIVE CAMERA + CELLULAR TELEMETRY",
                "Real Android modem-reported data",
                "--------------------------------"
            )

            for (cell in cells.take(8)) {
                val role = if (cell.isRegistered) "SERVING" else "OTHER CELL"
                when (cell) {
                    is CellInfoLte -> {
                        val s = cell.cellSignalStrength
                        val id = valueOrUnavailable(cell.cellIdentity.ci)
                        val channel = valueOrUnavailable(cell.cellIdentity.earfcn)
                        val rsrp = valueOrUnavailable(s.rsrp)
                        val rsrq = valueOrUnavailable(s.rsrq)
                        val dbm = valueOrUnavailable(s.dbm)
                        val sinr = valueOrUnavailable(s.rssnr)
                        lines +=
                            "LTE [$role] ID=$id CH=$channel dBm=$dbm " +
                            "RSRP=$rsrp RSRQ=$rsrq SINR=$sinr"
                    }
                    is CellInfoGsm -> {
                        lines +=
                            "GSM [$role] ID=" +
                            valueOrUnavailable(cell.cellIdentity.cid) +
                            " dBm=" +
                            valueOrUnavailable(cell.cellSignalStrength.dbm)
                    }
                    is CellInfoWcdma -> {
                        lines +=
                            "WCDMA [$role] ID=" +
                            valueOrUnavailable(cell.cellIdentity.cid) +
                            " dBm=" +
                            valueOrUnavailable(cell.cellSignalStrength.dbm)
                    }
                    else -> {
                        lines += "${cell.javaClass.simpleName} [$role]"
                    }
                }
            }

            lines += ""
            lines += "Camera preview: visible light only"
            lines += "Raw cellular RF/IQ: not exposed by TelephonyManager"
            lines += "EEG alpha/beta/theta/gamma: NOT MEASURED"
            lines += "Overlay is a visualization, not RF-to-light conversion."

            overlay.text = lines.joinToString("\n")

            // This changes only the overlay's opacity using a real reported metric.
            val validRsrp = cells.filterIsInstance<CellInfoLte>()
                .map { it.cellSignalStrength.rsrp }
                .filter { it != Int.MAX_VALUE && it != Int.MIN_VALUE && it < 0 }
                .minOrNull()

            val alpha = if (validRsrp == null) 184
                else (120 + ((-validRsrp - 60).coerceIn(0, 60))).coerceIn(120, 200)
            overlay.setBackgroundColor((alpha shl 24) or 0x00101820)
        } catch (e: SecurityException) {
            overlay.text = "Android denied access to cellular telemetry."
        } catch (e: Exception) {
            overlay.text =
                "Telemetry temporarily unavailable: ${e.javaClass.simpleName}"
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(poller)
        handler.post(poller)
    }

    override fun onPause() {
        handler.removeCallbacks(poller)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(poller)
        provider?.unbindAll()
        super.onDestroy()
    }
}
