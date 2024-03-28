package com.example.docscanner.ui.screen

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.docscanner.MainActivity
import com.example.docscanner.R
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

typealias Pdf = Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current as MainActivity
    var docs by remember { mutableStateOf(getSavedPdfList(context)) }
    val options = remember { getOptions() }

    val insertPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanningResult?.pdf?.let { pdf ->
                savePdf(pdf.uri, context)
                docs = getSavedPdfList(context)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.mediumTopAppBarColors()
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val scanner = GmsDocumentScanning.getClient(options)
                    scanner.getStartScanIntent(context)
                        .addOnSuccessListener { intentSender ->
                            insertPdfLauncher.launch(
                                IntentSenderRequest.Builder(intentSender).build()
                            )
                        }
                        .addOnFailureListener {
                            Log.d("TAG", "HomeScreen: ${it.message}")
                        }
                },
                text = {
                    Text(text = "Scan")
                },
                icon = {
                    Icon(
                        painterResource(id = R.drawable.ic_camera),
                        contentDescription = null,
                    )
                }
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                docs.forEach { (t, u) ->
                    PdfCard(pdf = t, displayName = u, context = context) { map ->
                        docs = map
                    }
                }
            }
        }
    )
}

@Composable
fun PdfCard(
    pdf: Pdf,
    displayName: String,
    context: MainActivity,
    onDelete: (Map<Pdf, String>) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .clickable {
                    openPdf(pdf, context)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                modifier = Modifier.size(80.dp),
                painter = painterResource(id = R.drawable.ic_pdf),
                contentDescription = null,
            )
            Text(text = displayName)

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                painter = painterResource(id = R.drawable.ic_delete),
                contentDescription = "Delete",
                modifier = Modifier.clickable {
                    deletePdf(pdf, context)
                    onDelete(getSavedPdfList(context))
                }
            )
        }
    }
}

private fun savePdf(uri: Uri, context: MainActivity) {
    val contentResolver = context.contentResolver

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val displayName = uri.lastPathSegment ?: "document.pdf"
        val mimeType = "application/pdf"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
        }
        val insertUri = contentResolver.insert(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            contentValues
        )
        insertUri?.let { newUri ->
            contentResolver.openOutputStream(newUri)?.use { outputStream ->
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
    } else {
        val inputStream = context.contentResolver.openInputStream(uri)
        inputStream?.use { input ->
            val fileName = uri.lastPathSegment ?: "document.pdf"
            val directory =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val file = File(directory, fileName)
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
    }
}


private fun deletePdf(pdf: Pdf, context: MainActivity) {
    val contentResolver = context.contentResolver

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val deleteUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val selection = "${MediaStore.MediaColumns._ID} = ?"
        val selectionArgs = arrayOf(pdf.lastPathSegment)
        contentResolver.delete(deleteUri, selection, selectionArgs)
    } else {
        val directory =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val file = File(directory, pdf.lastPathSegment!!)
        if (file.exists()) {
            file.delete()
        }
    }
}


private fun openPdf(pdf: Pdf, context: MainActivity) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = pdf
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    context.startActivity(intent)
}

private fun getSavedPdfList(context: MainActivity): Map<Pdf, String> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentResolver = context.contentResolver
        val queryUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        val selection = "${MediaStore.MediaColumns.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("application/pdf")

        val pdfMap = mutableMapOf<Pdf, String>()

        contentResolver.query(
            queryUri,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val displayNameColumnIndex =
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idColumnIndex)
                val displayName = cursor.getString(displayNameColumnIndex)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    id.toLong()
                )
                pdfMap[contentUri] = displayName
            }
        }
        return pdfMap
    } else {
        val pdfMap = mutableMapOf<Pdf, String>()
        val appDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val pdfFiles = appDir?.listFiles { file ->
            file.extension.lowercase(Locale.ROOT) == "pdf"
        } ?: emptyArray()

        pdfFiles.forEach {
            pdfMap[it.toUri()] = it.name
        }
        return pdfMap
    }
}


fun getOptions(): GmsDocumentScannerOptions {
    return GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build()
}
