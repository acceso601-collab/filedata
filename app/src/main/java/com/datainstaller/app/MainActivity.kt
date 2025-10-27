package com.datainstaller.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnRun: Button
    private lateinit var btnPick: Button

    private var pickedTreeUri: Uri? = null

    private val pickTreeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                pickedTreeUri = uri
                tvStatus.text = "Carpeta Android/data seleccionada."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvStatus = findViewById(R.id.tvStatus)
        btnRun = findViewById(R.id.btnRun)
        btnPick = findViewById(R.id.btnPickFolder)

        btnPick.setOnClickListener {
            pickAndroidDataFolder()
        }

        btnRun.setOnClickListener {
            startProcess()
        }

        // Request storage permissions for legacy devices
        if (Build.VERSION.SDK_INT <= 28) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 101)
        }
    }

    private fun pickAndroidDataFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        pickTreeLauncher.launch(intent)
    }

    private fun startProcess() {
        tvStatus.text = "Buscando zip en Download..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val downloads = java.io.File("/storage/emulated/0/Download")
                val zipFile = java.io.File(downloads, "com.HelloCrime.LovelyCraftPiston.zip")
                if (!zipFile.exists()) {
                    runOnUiThread { tvStatus.text = "No encontré com.HelloCrime.LovelyCraftPiston.zip en Download." }
                    return@launch
                }

                // Extract to Download/com.HelloCrime.LovelyCraftPiston/
                val outDir = java.io.File(downloads, "com.HelloCrime.LovelyCraftPiston")
                if (!outDir.exists()) outDir.mkdirs()
                unzip(zipFile, outDir)

                runOnUiThread { tvStatus.text = "Extraído en Download/com.HelloCrime.LovelyCraftPiston/" }

                // Now copy to Android/data
                if (Build.VERSION.SDK_INT >= 30) {
                    // Use SAF
                    if (pickedTreeUri == null) {
                        runOnUiThread { tvStatus.text = "Para Android 11+ debes seleccionar la carpeta Android/data con el botón 'Seleccionar Android/data'." }
                        return@launch
                    }
                    runOnUiThread { tvStatus.text = "Copiando vía SAF..." }
                    val tree = DocumentFile.fromTreeUri(this@MainActivity, pickedTreeUri!!)
                    if (tree == null) {
                        runOnUiThread { tvStatus.text = "Error accediendo a la carpeta seleccionada." }
                        return@launch
                    }
                    // Delete existing folder if present
                    tree.findFile("com.HelloCrime.LovelyCraftPiston")?.delete()
                    val newFolder = tree.createDirectory("com.HelloCrime.LovelyCraftPiston")
                    if (newFolder == null) {
                        runOnUiThread { tvStatus.text = "No pude crear la carpeta en Android/data (permiso insuficiente)." }
                        return@launch
                    }
                    copyFolderToDocumentFile(outDir, newFolder)
                    runOnUiThread { tvStatus.text = "Copiado en Android/data/com.HelloCrime.LovelyCraftPiston via SAF. Listo." }
                } else {
                    // Direct file copy for older Android
                    runOnUiThread { tvStatus.text = "Copiando en /storage/emulated/0/Android/data/ (modo legacy)..." }
                    val destRoot = java.io.File("/storage/emulated/0/Android/data")
                    if (!destRoot.exists()) destRoot.mkdirs()
                    val dest = java.io.File(destRoot, "com.HelloCrime.LovelyCraftPiston")
                    if (dest.exists()) {
                        dest.deleteRecursively()
                    }
                    copyDirectory(outDir, dest)
                    runOnUiThread { tvStatus.text = "Copiado en Android/data/com.HelloCrime.LovelyCraftPiston. Listo." }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { tvStatus.text = "Error: ${e.message}" }
            }
        }
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var ze: ZipEntry? = zis.nextEntry
            val buffer = ByteArray(4096)
            while (ze != null) {
                val fileName = ze.name
                val newFile = File(targetDir, fileName)
                if (ze.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        var count: Int
                        while (zis.read(buffer).also { count = it } != -1) {
                            fos.write(buffer, 0, count)
                        }
                    }
                }
                zis.closeEntry()
                ze = zis.nextEntry
            }
        }
    }

    private fun copyDirectory(src: File, dst: File) {
        if (src.isDirectory) {
            if (!dst.exists()) dst.mkdirs()
            src.list()?.forEach { child ->
                copyDirectory(File(src, child), File(dst, child))
            }
        } else {
            FileInputStream(src).use { fis ->
                FileOutputStream(dst).use { fos ->
                    fis.copyTo(fos)
                }
            }
        }
    }

    private fun copyFolderToDocumentFile(src: File, dstDoc: DocumentFile) {
        if (src.isDirectory) {
            src.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    val created = dstDoc.createDirectory(child.name ?: "unknown")
                    if (created != null) copyFolderToDocumentFile(child, created)
                } else {
                    val mime = contentResolver.getType(Uri.fromFile(child)) ?: "application/octet-stream"
                    val createdFile = dstDoc.createFile(mime, child.name ?: "file")
                    if (createdFile != null) {
                        contentResolver.openOutputStream(createdFile.uri)?.use { out ->
                            FileInputStream(child).use { inp ->
                                inp.copyTo(out)
                            }
                        }
                    }
                }
            }
        }
    }

}