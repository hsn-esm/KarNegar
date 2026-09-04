package ir.karnegar.app.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * ساخت گزارش و ذخیره‌ی آن در پوشه‌ی «دانلود» حافظه‌ی گوشی.
 *
 * ترتیب کار همان چیزی است که کاربر انتظار دارد: اول فایل روی حافظه‌ی گوشی و در
 * Download ذخیره می‌شود، بعد اگر خواست، همان فایل باز یا اشتراک‌گذاری می‌شود.
 *
 * فایل ابتدا در کش اپ ساخته می‌شود چون PdfDocument و XlsxWriter روی File
 * می‌نویسند، و سپس محتوایش به Download منتقل می‌شود.
 *
 * روی اندروید ۱۰ و بالاتر از MediaStore استفاده می‌شود که هیچ مجوزی نمی‌خواهد؛
 * روی اندروید ۷ تا ۹ مسیر عمومی Download با مجوز WRITE_EXTERNAL_STORAGE
 * (که در مانیفست تا API 28 محدود شده) نوشته می‌شود.
 */
object ReportExporter {

    enum class Format(val ext: String, val mime: String, val label: String) {
        PDF("pdf", "application/pdf", "PDF"),
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "اکسل")
    }

    /**
     * فایل ذخیره‌شده در Download.
     *
     * @param uri نشانی قابل باز کردن؛ در MediaStore نشانی محتوا و در مسیر قدیمی
     *            نشانی FileProvider است
     * @param displayName نامی که در پوشه‌ی دانلود دیده می‌شود
     */
    data class Saved(
        val uri: Uri,
        val displayName: String,
        val format: Format
    )

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "reports").apply { mkdirs() }

    /**
     * گزارش را می‌سازد و در پوشه‌ی دانلود ذخیره می‌کند. شبکه یا رابط کاربری را
     * مسدود می‌کند، پس باید از Dispatchers.IO صدا زده شود.
     */
    fun export(context: Context, data: ReportData, format: Format): Saved {
        val temp = File(cacheDir(context), "${data.fileBaseName()}.${format.ext}")
        when (format) {
            Format.PDF -> PdfReportWriter.write(data, temp)
            Format.XLSX -> writeXlsx(data, temp)
        }
        return try {
            saveToDownloads(context, temp, format)
        } finally {
            // نسخه‌ی کش لازم نیست بماند؛ نسخه‌ی اصلی در Download است
            temp.delete()
        }
    }

    // ---------- ذخیره در پوشه‌ی دانلود ----------

    private fun saveToDownloads(context: Context, source: File, format: Format): Saved =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, source, format)
        } else {
            saveViaLegacyPath(context, source, format)
        }

    private fun saveViaMediaStore(context: Context, source: File, format: Format): Saved {
        val resolver = context.contentResolver
        val name = uniqueMediaStoreName(context, source.name)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, format.mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            // تا نوشتن تمام نشود فایل برای اپ‌های دیگر پیدا نمی‌شود
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("ساخت فایل در پوشه‌ی دانلود ممکن نشد")
        try {
            resolver.openOutputStream(uri).use { out ->
                requireNotNull(out) { "نوشتن در پوشه‌ی دانلود ممکن نشد" }
                source.inputStream().use { it.copyTo(out) }
            }
        } catch (e: Throwable) {
            // رکورد نیمه‌کاره نباید در پوشه‌ی دانلود بماند
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null
        )
        return Saved(uri, name, format)
    }

    /**
     * MediaStore نام تکراری را خودش به «(1)» تغییر می‌دهد ولی نامِ نهایی را
     * برنمی‌گرداند، پس نام آزاد را خودمان پیدا می‌کنیم تا آنچه به کاربر نشان
     * می‌دهیم همان چیزی باشد که در پوشه‌ی دانلود است.
     */
    private fun uniqueMediaStoreName(context: Context, base: String): String {
        val dot = base.lastIndexOf('.')
        val stem = if (dot > 0) base.substring(0, dot) else base
        val ext = if (dot > 0) base.substring(dot) else ""
        for (i in 0..99) {
            val candidate = if (i == 0) base else "$stem-${i + 1}$ext"
            val exists = context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND " +
                        "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                arrayOf(candidate, "%${Environment.DIRECTORY_DOWNLOADS}%"),
                null
            )?.use { it.count > 0 } ?: false
            if (!exists) return candidate
        }
        return "$stem-${System.currentTimeMillis()}$ext"
    }

    private fun saveViaLegacyPath(context: Context, source: File, format: Format): Saved {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val target = uniqueLegacyFile(dir, source.name)
        source.inputStream().use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target
        )
        return Saved(uri, target.name, format)
    }

    private fun uniqueLegacyFile(dir: File, base: String): File {
        val dot = base.lastIndexOf('.')
        val stem = if (dot > 0) base.substring(0, dot) else base
        val ext = if (dot > 0) base.substring(dot) else ""
        var file = File(dir, base)
        var i = 2
        while (file.exists() && i <= 100) {
            file = File(dir, "$stem-$i$ext")
            i++
        }
        return file
    }

    // ---------- باز کردن و اشتراک‌گذاری ----------

    fun share(context: Context, saved: Saved) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = saved.format.mime
            putExtra(Intent.EXTRA_STREAM, saved.uri)
            putExtra(Intent.EXTRA_SUBJECT, saved.displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // اگر هیچ اپی برای اشتراک‌گذاری نبود، اپ نباید بسته شود
        runCatching {
            context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری گزارش کارکرد"))
        }.onFailure {
            Toast.makeText(
                context,
                "برنامه‌ای برای اشتراک‌گذاری پیدا نشد. فایل در پوشه‌ی دانلود ذخیره شده است: " +
                        saved.displayName,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun open(context: Context, saved: Saved) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(saved.uri, saved.format.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                Toast.makeText(
                    context,
                    "برنامه‌ای برای باز کردن ${saved.format.label} نصب نیست. " +
                            "فایل در پوشه‌ی دانلود است: ${saved.displayName}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ---------- ساخت اکسل ----------

    private fun writeXlsx(data: ReportData, file: File) {
        val w = XlsxWriter(sheetName = "کارکرد ${data.summary.year}-${data.summary.month}")
        // یازده ستون، هم‌تراز با detailHeader — ستون تاریخ قمری حذف شده است
        w.setColumnWidths(12.0, 10.0, 11.0, 9.0, 9.0, 10.0, 11.0, 11.0, 11.0, 11.0, 22.0)

        val S = XlsxWriter.Style

        // سربرگ سه‌بخشی: نام، ماه، تاریخ خروجی
        w.addRow(
            listOf(
                XlsxWriter.Cell.Text("کارمند: ${data.fullName}", S.BOLD),
                XlsxWriter.Cell.Text("گزارش ساعات کارکرد — ${data.monthTitle}", S.TITLE),
                XlsxWriter.Cell.Text("تاریخ خروجی: ${data.generatedLabel}", S.BOLD)
            )
        )
        w.addEmptyRow()

        // جمع‌بندی
        w.addRow(listOf(XlsxWriter.Cell.Text("خلاصه کارکرد", S.TITLE)))
        for ((label, value) in data.summaryLines()) {
            w.addRow(
                listOf(
                    XlsxWriter.Cell.Text(label, S.BOLD),
                    XlsxWriter.Cell.Text(value, S.NORMAL)
                )
            )
        }
        w.addEmptyRow()

        // جدول ریز کارکرد
        w.addRow(listOf(XlsxWriter.Cell.Text("ریز کارکرد روزانه", S.TITLE)))
        w.addRow(data.detailHeader.map { XlsxWriter.Cell.Text(it, S.HEADER) })
        for (row in data.detailRows()) {
            w.addRow(row.map { XlsxWriter.Cell.Text(it, S.NORMAL) })
        }

        w.writeTo(file)
    }
}
