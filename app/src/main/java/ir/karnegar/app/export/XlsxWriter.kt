package ir.karnegar.app.export

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * نویسنده‌ی ساده‌ی فایل xlsx بدون هیچ کتابخانه‌ی بیرونی.
 *
 * فایل xlsx در واقع یک zip از چند فایل XML است. اینجا حداقلِ لازم را می‌سازیم:
 * یک sheet با سلول‌های متنی و عددی، به‌همراه استایل برای سرستون‌ها.
 * جهت صفحه راست‌به‌چپ تنظیم می‌شود تا در Excel فارسی درست دیده شود.
 */
class XlsxWriter(private val sheetName: String = "کارکرد") {

    sealed interface Cell {
        data class Text(val value: String, val style: Int = 0) : Cell
        data class Number(val value: Double, val style: Int = 0) : Cell
        data object Empty : Cell
    }

    /** شماره‌ی استایل‌هایی که در styles.xml تعریف شده‌اند */
    object Style {
        const val NORMAL = 0
        const val HEADER = 1
        const val TITLE = 2
        const val BOLD = 3
    }

    private val rows = mutableListOf<List<Cell>>()
    private val columnWidths = mutableListOf<Double>()

    fun setColumnWidths(vararg widths: Double) {
        columnWidths.clear()
        columnWidths.addAll(widths.toList())
    }

    fun addRow(cells: List<Cell>) { rows.add(cells) }

    fun addTextRow(vararg values: String, style: Int = Style.NORMAL) {
        rows.add(values.map { Cell.Text(it, style) })
    }

    fun addEmptyRow() { rows.add(emptyList()) }

    fun writeTo(file: File) {
        file.outputStream().use { writeTo(it) }
    }

    fun writeTo(out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.putEntry("[Content_Types].xml", CONTENT_TYPES)
            zip.putEntry("_rels/.rels", ROOT_RELS)
            zip.putEntry("xl/workbook.xml", workbookXml())
            zip.putEntry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.putEntry("xl/styles.xml", STYLES)
            zip.putEntry("xl/worksheets/sheet1.xml", sheetXml())
        }
    }

    private fun ZipOutputStream.putEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun workbookXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="${escape(sheetName)}" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private fun sheetXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews>""")
        if (columnWidths.isNotEmpty()) {
            append("<cols>")
            columnWidths.forEachIndexed { i, w ->
                append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        rows.forEachIndexed { rowIndex, cells ->
            val r = rowIndex + 1
            append("""<row r="$r">""")
            cells.forEachIndexed { colIndex, cell ->
                val ref = "${columnName(colIndex)}$r"
                when (cell) {
                    is Cell.Text -> if (cell.value.isNotEmpty()) {
                        append("""<c r="$ref" s="${cell.style}" t="inlineStr"><is><t xml:space="preserve">${escape(cell.value)}</t></is></c>""")
                    }
                    is Cell.Number ->
                        append("""<c r="$ref" s="${cell.style}"><v>${trimNumber(cell.value)}</v></c>""")
                    Cell.Empty -> Unit
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun trimNumber(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    private fun columnName(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + (i % 26)))
            i = i / 26 - 1
            if (i < 0) break
        }
        return sb.toString()
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private companion object {
        const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

        const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

        /** ۰=عادی، ۱=سرستون، ۲=عنوان بزرگ، ۳=توپر */
        const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="4">
<font><sz val="11"/><name val="Calibri"/></font>
<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
<font><b/><sz val="14"/><name val="Calibri"/></font>
<font><b/><sz val="11"/><name val="Calibri"/></font>
</fonts>
<fills count="3">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF0F766E"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="2">
<border><left/><right/><top/><bottom/><diagonal/></border>
<border><left style="thin"><color rgb="FFBFCAC6"/></left><right style="thin"><color rgb="FFBFCAC6"/></right><top style="thin"><color rgb="FFBFCAC6"/></top><bottom style="thin"><color rgb="FFBFCAC6"/></bottom><diagonal/></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="4">
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="3" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
</cellXfs>
</styleSheet>"""
    }
}
