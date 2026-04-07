package com.liyaqa.report.builder

enum class MetricCatalogue(
    val code: String,
    val label: String,
    val labelAr: String,
    val unit: String,
    val scope: String,
    val sqlFragment: String,
    val sourceTable: String,
) {
    REVENUE(
        code = "revenue",
        label = "Revenue",
        labelAr = "الإيرادات",
        unit = "sar",
        scope = "revenue",
        sqlFragment = "COALESCE(SUM(p.amount_halalas), 0)",
        sourceTable = "payments p",
    ),
    REFUNDS(
        code = "refunds",
        label = "Refunds",
        labelAr = "المبالغ المستردة",
        unit = "sar",
        scope = "revenue",
        sqlFragment = "COALESCE(SUM(CASE WHEN p.payment_status = 'refunded' THEN p.amount_halalas ELSE 0 END), 0)",
        sourceTable = "payments p",
    ),
    NET_REVENUE(
        code = "net_revenue",
        label = "Net Revenue",
        labelAr = "صافي الإيرادات",
        unit = "sar",
        scope = "revenue",
        sqlFragment =
            "COALESCE(SUM(CASE WHEN p.payment_status = 'collected' THEN p.amount_halalas ELSE 0 END), 0) " +
                "- COALESCE(SUM(CASE WHEN p.payment_status = 'refunded' THEN p.amount_halalas ELSE 0 END), 0)",
        sourceTable = "payments p",
    ),
    NEW_MEMBERS(
        code = "new_members",
        label = "New Members",
        labelAr = "أعضاء جدد",
        unit = "count",
        scope = "members",
        sqlFragment = "COUNT(m.id)",
        sourceTable = "members m",
    ),
    ACTIVE_MEMBERSHIPS(
        code = "active_memberships",
        label = "Active Memberships",
        labelAr = "العضويات النشطة",
        unit = "count",
        scope = "members",
        sqlFragment = "COUNT(ms.id)",
        sourceTable = "memberships ms",
    ),
    EXPIRED_MEMBERSHIPS(
        code = "expired_memberships",
        label = "Expired Memberships",
        labelAr = "العضويات المنتهية",
        unit = "count",
        scope = "members",
        sqlFragment = "COUNT(ms.id)",
        sourceTable = "memberships ms",
    ),
    FROZEN_MEMBERSHIPS(
        code = "frozen_memberships",
        label = "Frozen Memberships",
        labelAr = "العضويات المجمدة",
        unit = "count",
        scope = "members",
        sqlFragment = "COUNT(ms.id)",
        sourceTable = "memberships ms",
    ),
    GX_BOOKINGS(
        code = "gx_bookings",
        label = "GX Bookings",
        labelAr = "حجوزات التمارين الجماعية",
        unit = "count",
        scope = "gx",
        sqlFragment = "COUNT(gb.id)",
        sourceTable = "gx_bookings gb",
    ),
    GX_ATTENDANCE(
        code = "gx_attendance",
        label = "GX Attendance",
        labelAr = "حضور التمارين الجماعية",
        unit = "count",
        scope = "gx",
        sqlFragment = "COUNT(CASE WHEN gb.attended = true THEN 1 END)",
        sourceTable = "gx_bookings gb",
    ),
    PT_SESSIONS(
        code = "pt_sessions",
        label = "PT Sessions",
        labelAr = "جلسات التدريب الشخصي",
        unit = "count",
        scope = "pt",
        sqlFragment = "COUNT(ps.id)",
        sourceTable = "pt_sessions ps",
    ),
    PT_ATTENDANCE(
        code = "pt_attendance",
        label = "PT Attendance",
        labelAr = "حضور التدريب الشخصي",
        unit = "count",
        scope = "pt",
        sqlFragment = "COUNT(CASE WHEN ps.session_status = 'completed' THEN 1 END)",
        sourceTable = "pt_sessions ps",
    ),
    LEADS_CREATED(
        code = "leads_created",
        label = "Leads Created",
        labelAr = "العملاء المحتملون الجدد",
        unit = "count",
        scope = "leads",
        sqlFragment = "COUNT(l.id)",
        sourceTable = "leads l",
    ),
    LEADS_CONVERTED(
        code = "leads_converted",
        label = "Leads Converted",
        labelAr = "العملاء المحولون",
        unit = "count",
        scope = "leads",
        sqlFragment = "COUNT(CASE WHEN l.stage = 'converted' THEN 1 END)",
        sourceTable = "leads l",
    ),
    LEAD_CONVERSION_RATE(
        code = "lead_conversion_rate",
        label = "Lead Conversion Rate",
        labelAr = "معدل تحويل العملاء",
        unit = "percent",
        scope = "leads",
        sqlFragment =
            "CASE WHEN COUNT(l.id) > 0 " +
                "THEN ROUND(COUNT(CASE WHEN l.stage = 'converted' THEN 1 END) * 100.0 / COUNT(l.id), 2) ELSE 0 END",
        sourceTable = "leads l",
    ),
    CASH_IN(
        code = "cash_in",
        label = "Cash In",
        labelAr = "النقد الوارد",
        unit = "sar",
        scope = "cash",
        sqlFragment = "COALESCE(SUM(CASE WHEN cde.entry_type = 'cash_in' THEN cde.amount_halalas ELSE 0 END), 0)",
        sourceTable = "cash_drawer_entries cde",
    ),
    CASH_OUT(
        code = "cash_out",
        label = "Cash Out",
        labelAr = "النقد الصادر",
        unit = "sar",
        scope = "cash",
        sqlFragment = "COALESCE(SUM(CASE WHEN cde.entry_type = 'cash_out' THEN cde.amount_halalas ELSE 0 END), 0)",
        sourceTable = "cash_drawer_entries cde",
    ),
    ;

    companion object {
        private val BY_CODE = entries.associateBy { it.code }

        fun fromCode(code: String): MetricCatalogue? = BY_CODE[code]

        fun all(): List<MetricCatalogue> = entries
    }
}
