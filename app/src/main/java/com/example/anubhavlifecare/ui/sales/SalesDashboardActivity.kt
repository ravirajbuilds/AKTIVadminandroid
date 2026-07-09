package com.example.anubhavlifecare.ui.sales

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.anubhavlifecare.data.repository.AktivRepository
import com.example.anubhavlifecare.utils.SessionManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToLong

/**
 * Sales dashboard — visible only to roles with can_view_sales (ADMIN / ACCOUNT).
 * Charts (MPAndroidChart): revenue trend (line), by-category & by-doctor (bars),
 * year-over-year comparison (multi-line). All data from the /api/analytics/* endpoints,
 * which the server also gates by role.
 */
class SalesDashboardActivity : AppCompatActivity() {
    private val repo = AktivRepository()
    private var days = 90

    private lateinit var kpiRow: LinearLayout
    private lateinit var line: LineChart
    private lateinit var catChart: HorizontalBarChart
    private lateinit var docChart: BarChart
    private lateinit var yoyChart: LineChart

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        title = "Sales Dashboard"

        // hard gate — server also enforces, this is the UI guard
        if (!SessionManager.canViewSales(this)) {
            Toast.makeText(this, "You don't have permission to view sales.", Toast.LENGTH_LONG).show()
            finish(); return
        }

        val scroll = ScrollView(this).apply { setBackgroundColor(0xFFF3F4F6.toInt()) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(24))
        }
        scroll.addView(root, ViewGroup.LayoutParams(MATCH, WRAP))
        setContentView(scroll)

        // range selector
        val rangeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(30, 90, 365).forEach { d ->
            rangeRow.addView(Button(this).apply {
                text = when (d) { 30 -> "30d"; 90 -> "90d"; else -> "1y" }
                isAllCaps = false
                setOnClickListener { days = d; loadAll() }
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
        }
        root.addView(rangeRow)

        kpiRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(8)) }
        root.addView(kpiRow)

        root.addView(sectionLabel("Revenue trend"))
        line = LineChart(this).apply { layoutParams = chartLp(220) }; root.addView(line)

        root.addView(sectionLabel("Revenue by category"))
        catChart = HorizontalBarChart(this).apply { layoutParams = chartLp(260) }; root.addView(catChart)

        root.addView(sectionLabel("Top referring doctors"))
        docChart = BarChart(this).apply { layoutParams = chartLp(240) }; root.addView(docChart)

        root.addView(sectionLabel("Year-over-year (monthly)"))
        yoyChart = LineChart(this).apply { layoutParams = chartLp(240) }; root.addView(yoyChart)

        loadAll()
    }

    private fun loadAll() {
        val uk = SessionManager.getUserKey(this) ?: return
        val to = LocalDate.now().toString()
        val from = LocalDate.now().minusDays(days.toLong()).toString()
        lifecycleScope.launch {
            repo.salesSummary(uk, from, to).onSuccess { renderKpis(it) }
            repo.salesByDay(uk, from, to).onSuccess { rows ->
                val entries = rows.mapIndexed { i, r -> Entry(i.toFloat(), r.revenue.toFloat()) }
                setLine(line, entries, rows.map { it.day.takeLast(5) }, "Revenue", 0xFF0D9488.toInt())
            }
            repo.salesByCategory(uk, from, to).onSuccess { rows ->
                val top = rows.take(10)
                setBar(catChart, top.mapIndexed { i, r -> BarEntry(i.toFloat(), r.revenue.toFloat()) },
                    top.map { it.category }, "By category", 0xFF2563EB.toInt())
            }
            repo.salesByDoctor(uk, from, to).onSuccess { rows ->
                val top = rows.take(8)
                setBar(docChart, top.mapIndexed { i, r -> BarEntry(i.toFloat(), r.revenue.toFloat()) },
                    top.map { it.doctor.take(14) }, "By doctor", 0xFFF59E0B.toInt())
            }
            repo.salesYoy(uk).onSuccess { renderYoy(it) }
        }
    }

    private fun renderKpis(s: com.example.anubhavlifecare.data.model.SalesSummary) {
        kpiRow.removeAllViews()
        kpiRow.addView(kpi("Revenue", money(s.revenue)))
        kpiRow.addView(kpi("Bills", s.bills.toString()))
        kpiRow.addView(kpi("Avg bill", money(s.avgBill)))
        kpiRow.addView(kpi("Pending", money(s.pending)))
    }

    private fun renderYoy(y: com.example.anubhavlifecare.data.model.YoyResponse) {
        val byYear = y.points.groupBy { it.year }
        val sets = byYear.entries.sortedBy { it.key }.mapIndexed { idx, e ->
            val entries = e.value.sortedBy { it.month }.map { Entry((it.month - 1).toFloat(), it.revenue.toFloat()) }
            LineDataSet(entries, e.key.toString()).apply {
                val palette = intArrayOf(0xFF0D9488.toInt(), 0xFF2563EB.toInt(), 0xFFF59E0B.toInt(), 0xFFDC2626.toInt(), 0xFF7C3AED.toInt())
                color = palette[idx % palette.size]; setDrawCircles(false); lineWidth = 2f; setDrawValues(false)
            }
        }
        yoyChart.data = LineData(sets.toList())
        yoyChart.xAxis.valueFormatter = IndexAxisValueFormatter(
            listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"))
        styleAxes(yoyChart.xAxis)
        yoyChart.axisRight.isEnabled = false
        yoyChart.description.isEnabled = false
        yoyChart.invalidate()
    }

    // ---- chart helpers ----
    private fun setLine(chart: LineChart, entries: List<Entry>, labels: List<String>, label: String, color: Int) {
        val ds = LineDataSet(entries, label).apply {
            this.color = color; setDrawCircles(false); lineWidth = 2f; setDrawValues(false)
            setDrawFilled(true); fillColor = color; fillAlpha = 40
        }
        chart.data = LineData(ds)
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        styleAxes(chart.xAxis)
        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.invalidate()
    }

    // BarChart covers HorizontalBarChart too (it's a subclass), so setFitBars is available.
    private fun setBar(chart: BarChart, entries: List<BarEntry>, labels: List<String>, label: String, color: Int) {
        val ds = BarDataSet(entries, label).apply {
            this.color = color; valueTextSize = 9f
        }
        chart.data = BarData(ds)
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chart.xAxis.labelCount = labels.size
        styleAxes(chart.xAxis)
        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setFitBars(true)
        chart.invalidate()
    }

    private fun styleAxes(x: XAxis) {
        x.position = XAxis.XAxisPosition.BOTTOM
        x.granularity = 1f
        x.setDrawGridLines(false)
        x.textSize = 8f
    }

    private fun kpi(label: String, value: String): TextView = TextView(this).apply {
        text = "$value\n$label"
        gravity = Gravity.CENTER
        setPadding(dp(6), dp(12), dp(6), dp(12))
        setBackgroundColor(Color.WHITE)
        setTextColor(0xFF111111.toInt())
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(6) }
    }

    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t; textSize = 14f; setTextColor(0xFF374151.toInt())
        setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(14), 0, dp(4))
    }

    private fun chartLp(h: Int) = LinearLayout.LayoutParams(MATCH, dp(h))

    private fun money(v: Double): String {
        val n = v.roundToLong()
        return when {
            n >= 10_000_000 -> "₹%.2fCr".format(n / 1e7)
            n >= 100_000 -> "₹%.2fL".format(n / 1e5)
            n >= 1_000 -> "₹%.1fK".format(n / 1e3)
            else -> "₹$n"
        }
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
