package ir.karnegar.app.export

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * ساخت فایل خروجی در حافظه‌ی کش اپ و باز کردن پنجره‌ی اشتراک‌گذاری.
 * چون فایل در FileProvider منتشر می‌شود، به هیچ مجوز ذخیره‌سازی نیازی نیست.
 */
object ReportExporter {

    enum class Format(val ext: String, val mime: String, val label: String) {
        PDF("pdf", "application/pdf", "PDF"),
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "اکسل")
    }

    private fun outputDir(context: Context): File =
        File(context.cacheDir, "reports").apply { mkdirs() }

    fun export(context: Context, data: ReportData, format: Format): File {
        val file = File(outputDir(context), "${data.fileBaseName()}.${format.ext}")
        when (format) {
            Format.PDF -> PdfReportWriter.write(data, file)
            Format.XLSX -> writeXlsx(data, file)
        }
        return file
    }

    fun share(context: Context, file: File, format: Format) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // اگر هیچ اپی برای اشتراک‌گذاری نبود، اپ نباید بسته شود
        runCatching {
            context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری گزارش کارکرد"))
        }.onFailure {
            Toast.makeText(
                context,
                "برنامه‌ای برای اشتراک‌گذاری این فایل پیدا نشد. فایل در ${file.name} ساخته شد.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun open(context: Context, file: File, format: Format) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, format.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { share(context, file, format) }
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
