package com.roadwatch.fl.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.roadwatch.fl.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TableViewerDialog(
    context: Context,
    private val filePath: String,
    private val fileName: String
) : Dialog(context, R.style.Theme_RoadWatchFL) {

    private val pageSize = 50
    private var currentPage = 0
    private var totalPages = 1

    private var headers: List<String> = emptyList()
    private var dataRows: List<List<String>> = emptyList()

    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvPageIndicator: TextView
    private lateinit var btnClose: MaterialButton
    private lateinit var btnPrev: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var tableLayout: TableLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_table_viewer)

        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        tvTitle = findViewById(R.id.tv_dialog_title)
        tvSubtitle = findViewById(R.id.tv_dialog_subtitle)
        tvPageIndicator = findViewById(R.id.tv_page_indicator)
        btnClose = findViewById(R.id.btn_dialog_close)
        btnPrev = findViewById(R.id.btn_page_prev)
        btnNext = findViewById(R.id.btn_page_next)
        tableLayout = findViewById(R.id.table_layout)

        tvTitle.text = fileName
        btnClose.setOnClickListener { dismiss() }

        val btnShare = findViewById<MaterialButton>(R.id.btn_dialog_share)
        btnShare?.setOnClickListener {
            com.roadwatch.fl.util.FileShareUtils.shareRecordingFile(context, File(filePath), fileName)
        }

        btnPrev.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                renderTable()
            }
        }

        btnNext.setOnClickListener {
            if (currentPage < totalPages - 1) {
                currentPage++
                renderTable()
            }
        }

        loadCsvData()
        renderTable()
    }

    private fun loadCsvData() {
        val file = File(filePath)
        if (!file.exists()) {
            tvSubtitle.text = "File not found"
            return
        }

        try {
            val lines = file.readLines()
            if (lines.isEmpty()) {
                tvSubtitle.text = "Empty file"
                return
            }

            headers = lines[0].split(",").map { it.trim() }
            dataRows = lines.drop(1).filter { it.isNotBlank() }.map { line ->
                line.split(",").map { it.trim() }
            }

            totalPages = if (dataRows.isEmpty()) 1 else ((dataRows.size + pageSize - 1) / pageSize)
            tvSubtitle.text = "${dataRows.size} rows · ${headers.size} columns"
        } catch (e: Exception) {
            tvSubtitle.text = "Error reading file: ${e.message}"
        }
    }

    private fun renderTable() {
        tableLayout.removeAllViews()

        if (headers.isEmpty()) {
            val emptyRow = TableRow(context)
            val tv = TextView(context).apply {
                text = "No data available in this file"
                setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            }
            emptyRow.addView(tv)
            tableLayout.addView(emptyRow)
            updatePaginationControls()
            return
        }

        // Render Header Row
        val headerRow = TableRow(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
        }

        // Row Index Header
        headerRow.addView(createHeaderCell("#", isIndex = true))

        headers.forEach { headerName ->
            headerRow.addView(createHeaderCell(headerName))
        }
        tableLayout.addView(headerRow)

        // Render Data Rows for current page
        val startIndex = currentPage * pageSize
        val endIndex = minOf(startIndex + pageSize, dataRows.size)

        val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

        for (i in startIndex until endIndex) {
            val rowData = dataRows[i]
            val tableRow = TableRow(context).apply {
                // Alternating row background
                val bgColor = if ((i - startIndex) % 2 == 0) {
                    ContextCompat.getColor(context, R.color.surface)
                } else {
                    ContextCompat.getColor(context, R.color.table_row_alt)
                }
                setBackgroundColor(bgColor)
            }

            // Row index cell
            tableRow.addView(createDataCell((i + 1).toString(), isIndex = true))

            for (colIdx in headers.indices) {
                val value = if (colIdx < rowData.size) rowData[colIdx] else ""
                val displayValue = if (headers[colIdx].equals("timestamp", ignoreCase = true)) {
                    val ts = value.toLongOrNull()
                    if (ts != null) {
                        "${timeFormat.format(Date(ts))}\n($ts)"
                    } else {
                        value.ifEmpty { "-" }
                    }
                } else {
                    value.ifEmpty { "-" }
                }
                tableRow.addView(createDataCell(displayValue))
            }

            tableLayout.addView(tableRow)
        }

        updatePaginationControls()
    }

    private fun createHeaderCell(text: String, isIndex: Boolean = false): TextView {
        return TextView(context).apply {
            this.text = text
            val customTypeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.inter)
            typeface = Typeface.create(customTypeface ?: Typeface.DEFAULT, Typeface.BOLD)
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.primary))
            gravity = if (isIndex) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_table_header_cell)
            val hPad = dpToPx(12)
            val vPad = dpToPx(10)
            setPadding(hPad, vPad, hPad, vPad)
            minWidth = if (isIndex) dpToPx(50) else dpToPx(110)
        }
    }

    private fun createDataCell(text: String, isIndex: Boolean = false): TextView {
        return TextView(context).apply {
            this.text = text
            val monoTypeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.jetbrains_mono)
            typeface = monoTypeface ?: Typeface.MONOSPACE
            textSize = 11f
            setTextColor(
                if (isIndex) {
                    ContextCompat.getColor(context, R.color.on_surface_variant)
                } else {
                    ContextCompat.getColor(context, R.color.on_surface)
                }
            )
            gravity = if (isIndex) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_table_data_cell)
            val hPad = dpToPx(12)
            val vPad = dpToPx(8)
            setPadding(hPad, vPad, hPad, vPad)
            minWidth = if (isIndex) dpToPx(50) else dpToPx(110)
        }
    }

    private fun updatePaginationControls() {
        if (dataRows.isEmpty()) {
            tvPageIndicator.text = "0 / 0"
            btnPrev.isEnabled = false
            btnNext.isEnabled = false
            return
        }

        val startRow = currentPage * pageSize + 1
        val endRow = minOf((currentPage + 1) * pageSize, dataRows.size)
        tvPageIndicator.text = "Page ${currentPage + 1} of $totalPages\n(Rows $startRow - $endRow of ${dataRows.size})"
        btnPrev.isEnabled = currentPage > 0
        btnNext.isEnabled = currentPage < totalPages - 1
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
