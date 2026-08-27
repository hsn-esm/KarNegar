package ir.karnegar.app.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File

/**
 * تولید PDF با موتور گرافیکی خود اندروید.
 *
 * نکته‌ی مهم: Canvas.drawText در اندروید متن فارسی را با HarfBuzz شکل می‌دهد،
 * پس حروف چسبیده و ترتیب راست‌به‌چپ درست رندر می‌شود. برای همین نیازی به
 * کتابخانه‌ی جانبی PDF نداریم و حجم اپ کم می‌ماند.
 *
 * صفحه A4 افقی (۸۴۲×۵۹۵ در واحد ۷۲dpi) چون جدول ۱۲ ستون دارد.
 */
object PdfReportWriter {

    private const val PAGE_W = 842
    private const val PAGE_H = 595
    private const val MARGIN = 28f

    private val TEAL = Color.parseColor("#0F766E")
    private val TEAL_LIGHT = Color.parseColor("#E3F1EE")
    private val GRID = Color.parseColor("#C9D4D0")
    private val TEXT = Color.parseColor("#14201C")
    private val MUTED = Color.parseColor("#5A6864")

    /** پهنای نسبی ستون‌های جدول جز به جز، جمعشان باید ۱ باشد */
    private val COL_WEIGHTS = floatArrayOf(
        0.090f, 0.080f, 0.085f,          // تاریخ، روز هفته، نوع شیفت
        0.068f, 0.068f, 0.076f,          // ورود، خروج، کارکرد
        0.080f, 0.080f, 0.080f, 0.080f,  // تعجیل/تاخیر
        0.213f                            // توضیح
    )

    fun write(data: ReportData, file: File) {
        val doc = PdfDocument()
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }
        val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val headerText = Paint(bold).apply { color = Color.WHITE; textSize = 8.5f }
        val line = Paint().apply { color = GRID; strokeWidth = 0.6f; style = Paint.Style.STROKE }
        val fillTeal = Paint().apply { color = TEAL; style = Paint.Style.FILL }
        val fillLight = Paint().apply { color = TEAL_LIGHT; style = Paint.Style.FILL }

        val rows = data.detailRows()
        val tableWidth = PAGE_W - 2 * MARGIN
        val colWidths = COL_WEIGHTS.map { it * tableWidth }
        val rowHeight = 18f

        var pageNumber = 1
        var index = 0

        while (true) {
            val page = doc.startPage(
                PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create()
            )
            val canvas = page.canvas
            var y = MARGIN

            // ---------- سربرگ ----------
            if (pageNumber == 1) {
                y = drawHeader(canvas, data, y, bold, body)
                y += 8f
                y = drawSummaryBox(canvas, data, y, bold, body, fillLight, line)
                y += 14f
                canvas.drawTextRtl("ریز کارکرد روزانه", PAGE_W - MARGIN, y, bold.alignRight(10f, TEAL))
                y += 12f
            } else {
                canvas.drawTextRtl(
                    "${data.fullName} — ${data.monthTitle} (ادامه)",
                    PAGE_W - MARGIN, y + 10f, bold.alignRight(10f, TEAL)
                )
                y += 24f
            }

            // ---------- سرستون جدول ----------
            drawRow(canvas, data.detailHeader, MARGIN, y, colWidths, rowHeight, headerText, fillTeal, line, true)
            y += rowHeight

            // ---------- سطرها ----------
            val indexAtPageStart = index
            while (index < rows.size && y + rowHeight < PAGE_H - MARGIN - 22f) {
                val zebra = if (index % 2 == 1) fillLight else null
                drawRow(canvas, rows[index], MARGIN, y, colWidths, rowHeight, body, zebra, line, false)
                y += rowHeight
                index++
            }
            // اگر در این صفحه هیچ سطری جا نشد، برای جلوگیری از حلقه‌ی بی‌پایان
            // یک سطر را به اجبار همین‌جا می‌کشیم
            if (index == indexAtPageStart && index < rows.size) {
                drawRow(canvas, rows[index], MARGIN, y, colWidths, rowHeight, body, null, line, false)
                index++
            }

            if (rows.isEmpty()) {
                canvas.drawText(
                    "برای این ماه شیفتی ثبت نشده است",
                    PAGE_W / 2f, y + 24f,
                    Paint(body).apply { color = MUTED; textSize = 10f }
                )
            }

            // ---------- پاصفحه ----------
            val footer = Paint(body).apply { color = MUTED; textSize = 8f }
            canvas.drawText(
                "کارنگار — صفحه ${pageNumber.toString().toFa()}",
                PAGE_W / 2f, PAGE_H - MARGIN + 6f, footer
            )

            doc.finishPage(page)

            if (index >= rows.size) break
            pageNumber++
        }

        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    // ---------- بخش‌های سربرگ ----------

    private fun drawHeader(
        canvas: Canvas,
        data: ReportData,
        top: Float,
        bold: Paint,
        body: Paint
    ): Float {
        val right = PAGE_W - MARGIN
        val left = MARGIN
        val center = PAGE_W / 2f

        // راست: نام و فامیل — وسط: ماه — چپ: تاریخ خروجی
        // در هر سه بخش، برچسبِ ریز بالا می‌نشیند و مقدارِ درشت زیرش؛ چشم اول
        // می‌فهمد دارد چه چیزی را می‌خواند و بعد خودِ مقدار را می‌بیند
        canvas.drawTextRtl("کارمند", right, top + 13f, body.alignRight(8.5f, MUTED))
        canvas.drawTextRtl(data.fullName, right, top + 29f, bold.alignRight(13f, TEXT))

        canvas.drawText("گزارش ساعات کارکرد", center, top + 14f, Paint(body).apply {
            textSize = 8.5f; color = MUTED; textAlign = Paint.Align.CENTER
        })
        canvas.drawText("کارکرد ${data.monthTitle}", center, top + 31f, Paint(bold).apply {
            textSize = 15f; color = TEAL; textAlign = Paint.Align.CENTER
        })

        canvas.drawText("تاریخ خروجی", left, top + 13f, body.alignLeft(8.5f, MUTED))
        canvas.drawText(data.generatedLabel, left, top + 29f, bold.alignLeft(10f, TEXT))

        val lineY = top + 40f
        canvas.drawLine(left, lineY, right, lineY, Paint().apply {
            color = TEAL; strokeWidth = 1.2f
        })
        return lineY
    }

    private fun drawSummaryBox(
        canvas: Canvas,
        data: ReportData,
        top: Float,
        bold: Paint,
        body: Paint,
        fillLight: Paint,
        line: Paint
    ): Float {
        val lines = data.summaryLines()
        val colCount = 3
        val rowsCount = (lines.size + colCount - 1) / colCount
        val boxW = (PAGE_W - 2 * MARGIN) / colCount
        val cellH = 16f
        val boxH = rowsCount * cellH + 6f

        canvas.drawRoundRect(
            RectF(MARGIN, top, PAGE_W - MARGIN, top + boxH), 6f, 6f, fillLight
        )

        lines.forEachIndexed { i, (label, value) ->
            val col = i % colCount
            val row = i / colCount
            // ستون‌ها از راست به چپ چیده می‌شوند
            val cellRight = PAGE_W - MARGIN - col * boxW - 8f
            val baseY = top + 3f + (row + 1) * cellH - 4f
            canvas.drawTextRtl("$label: ", cellRight, baseY, body.alignRight(9f, MUTED))
            val labelW = body.alignRight(9f, MUTED).measureText("$label: ")
            canvas.drawTextRtl(value, cellRight - labelW, baseY, bold.alignRight(9f, TEXT))
        }
        return top + boxH
    }

    // ---------- جدول ----------

    private fun drawRow(
        canvas: Canvas,
        cells: List<String>,
        left: Float,
        top: Float,
        colWidths: List<Float>,
        height: Float,
        textPaint: Paint,
        fill: Paint?,
        line: Paint,
        isHeader: Boolean
    ) {
        val totalW = colWidths.sum()
        if (fill != null) {
            canvas.drawRect(left, top, left + totalW, top + height, fill)
        }
        canvas.drawRect(left, top, left + totalW, top + height, line)

        // ستون اول در سمت راست قرار می‌گیرد
        var x = left + totalW
        val baseline = top + height / 2f + textPaint.textSize / 2f - 1.5f
        cells.forEachIndexed { i, text ->
            if (i >= colWidths.size) return@forEachIndexed
            val w = colWidths[i]
            x -= w
            if (i > 0) canvas.drawLine(x + w, top, x + w, top + height, line)
            val p = Paint(textPaint).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText(fit(text, w - 4f, p), x + w / 2f, baseline, p)
        }
    }

    /** کوتاه کردن متن اگر در عرض ستون جا نشد */
    private fun fit(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.length > 1 && paint.measureText("$t…") > maxWidth) {
            t = t.dropLast(1)
        }
        return "$t…"
    }

    // ---------- کمکی‌ها ----------

    private fun Paint.alignRight(size: Float, c: Int) = Paint(this).apply {
        textSize = size; color = c; textAlign = Paint.Align.RIGHT
    }

    private fun Paint.alignLeft(size: Float, c: Int) = Paint(this).apply {
        textSize = size; color = c; textAlign = Paint.Align.LEFT
    }

    private fun Canvas.drawTextRtl(text: String, x: Float, y: Float, paint: Paint) {
        drawText(text, x, y, paint)
    }

    private fun String.toFa(): String = map {
        if (it in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[it - '0'] else it
    }.joinToString("")
}
