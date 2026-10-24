package org.kbraj003.rfphysiology

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telephony.*
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var telephony: TelephonyManager
    private lateinit var status: TextView
    private lateinit var readings: TextView
    private var recording = false
    private val rows = mutableListOf<String>()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    private val poll = object : Runnable { override fun run() { if (recording) { readCells(); handler.postDelayed(this, 10000L) } } }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        telephony = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 20) }
        status = TextView(this).apply { text = "Real cellular telemetry. EEG is not measured."; textSize = 16f }
        readings = TextView(this).apply { textSize = 13f; setTextIsSelectable(true) }
        fun button(label: String, f: () -> Unit) { root.addView(Button(this).apply { text = label; setOnClickListener { f() } }) }
        button("Grant permissions") { requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_FINE_LOCATION), 42) }
        button("Read modem now") { if (permitted()) readCells() else status.text = "Grant phone-state and location permissions first." }
        button("Start recording") { if (!permitted()) { status.text = "Grant permissions first." } else if (!recording) { recording = true; status.text = "Recording actual Android modem telemetry."; handler.post(poll) } }
        button("Stop recording") { recording = false; handler.removeCallbacks(poll); status.text = "Stopped. Saved ${rows.size} records." }
        button("Export CSV") { exportCsv() }
        root.addView(status)
        val scroll = ScrollView(this); scroll.addView(readings); root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }
    private fun permitted() = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, results); status.text = if (permitted()) "Permissions granted." else "Required permissions were not granted." }

    private fun readCells() {
        if (!permitted()) return
        try { telephony.requestCellInfoUpdate(mainExecutor, object : TelephonyManager.CellInfoCallback() {
            override fun onCellInfo(cells: List<CellInfo>) {
                val stamp = fmt.format(Date()); val display = StringBuilder()
                if (cells.isEmpty()) display.append("No cell records returned.\n")
                cells.forEach { cell ->
                    val type: String; val mcc: String; val mnc: String; val id: String; val area: String; val pci: String; val channel: String
                    var dbm = ""; var rsrp = ""; var rsrq = ""; var sinr = ""
                    when (cell) {
                        is CellInfoLte -> { val i=cell.cellIdentity; val s=cell.cellSignalStrength; type="LTE"; mcc=i.mccString?:""; mnc=i.mncString?:""; id=i.ci.toString(); area=i.tac.toString(); pci=i.pci.toString(); channel=i.earfcn.toString(); dbm=s.dbm.toString(); rsrp=s.rsrp.toString(); rsrq=s.rsrq.toString(); sinr=s.rssnr.toString() }
                        is CellInfoNr -> { val i=cell.cellIdentity as CellIdentityNr; val s=cell.cellSignalStrength as CellSignalStrengthNr; type="NR"; mcc=i.mccString?:""; mnc=i.mncString?:""; id=i.nci.toString(); area=i.tac.toString(); pci=i.pci.toString(); channel=i.nrarfcn.toString(); dbm=s.dbm.toString(); rsrp=s.ssRsrp.toString(); rsrq=s.ssRsrq.toString(); sinr=s.ssSinr.toString() }
                        is CellInfoWcdma -> { val i=cell.cellIdentity; val s=cell.cellSignalStrength; type="WCDMA"; mcc=i.mccString?:""; mnc=i.mncString?:""; id=i.cid.toString(); area=i.lac.toString(); pci=i.psc.toString(); channel=i.uarfcn.toString(); dbm=s.dbm.toString() }
                        is CellInfoGsm -> { val i=cell.cellIdentity; val s=cell.cellSignalStrength; type="GSM"; mcc=i.mccString?:""; mnc=i.mncString?:""; id=i.cid.toString(); area=i.lac.toString(); pci=i.bsic.toString(); channel=i.arfcn.toString(); dbm=s.dbm.toString() }
                        else -> { type=cell.javaClass.simpleName; mcc=""; mnc=""; id=""; area=""; pci=""; channel=""; dbm=cell.cellSignalStrength.dbm.toString() }
                    }
                    val fields=listOf(stamp,type,cell.isRegistered.toString(),mcc,mnc,id,area,pci,channel,dbm,rsrp,rsrq,sinr)
                    val csv=fields.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
                    if (recording) synchronized(rows) { rows.add(csv) }
                    display.append("$type ${if(cell.isRegistered) "[SERVING]" else "[OTHER CELL]"} ID=$id channel=$channel dBm=$dbm RSRP=$rsrp RSRQ=$rsrq SINR=$sinr\n")
                }
                readings.text=display.toString(); status.text="Updated $stamp; cells=${cells.size}; saved=${rows.size}. These are modem telemetry, not raw RF samples or EEG."
            }
            override fun onError(errorCode: Int, detail: Throwable?) { status.text="Cell update failed: code=$errorCode ${detail?.message?:""}" }
        }) } catch (e: Exception) { status.text="Telephony API error: ${e.javaClass.simpleName}: ${e.message}" }
    }
    private fun exportCsv() { val intent=android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(android.content.Intent.CATEGORY_OPENABLE); type="text/csv"; putExtra(android.content.Intent.EXTRA_TITLE,"cellular_telemetry.csv") }; @Suppress("DEPRECATION") startActivityForResult(intent, 71) }
    @Deprecated("Activity result API retained for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) { super.onActivityResult(requestCode,resultCode,data); if(requestCode==71 && resultCode==RESULT_OK) { val uri: Uri=data?.data?:return; try { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w -> w.appendLine("timestamp,cell_type,registered,mcc,mnc,cell_id,tac_or_lac,pci,channel,dbm,rsrp,rsrq,sinr"); synchronized(rows) { rows.forEach { w.appendLine(it) } } }; status.text="Export complete: ${rows.size} records." } catch(e:Exception) { status.text="Export failed: ${e.message}" } } }
    override fun onDestroy() { recording=false; handler.removeCallbacksAndMessages(null); super.onDestroy() }
}
