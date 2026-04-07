package com.liyaqa.report.builder

enum class DimensionCatalogue(
    val code: String,
    val label: String,
    val labelAr: String,
    val sqlSelectFragment: String,
    val sqlGroupByFragment: String,
    val joinFragment: String?,
    val compatibleScopes: Set<String>,
) {
    DAY(
        code = "day",
        label = "Day",
        labelAr = "اليوم",
        sqlSelectFragment = "DATE_TRUNC('day', {date_column})",
        sqlGroupByFragment = "DATE_TRUNC('day', {date_column})",
        joinFragment = null,
        compatibleScopes = setOf("revenue", "members", "leads", "gx", "pt", "cash"),
    ),
    WEEK(
        code = "week",
        label = "Week",
        labelAr = "الأسبوع",
        sqlSelectFragment = "DATE_TRUNC('week', {date_column})",
        sqlGroupByFragment = "DATE_TRUNC('week', {date_column})",
        joinFragment = null,
        compatibleScopes = setOf("revenue", "members", "leads", "gx", "pt", "cash"),
    ),
    MONTH(
        code = "month",
        label = "Month",
        labelAr = "الشهر",
        sqlSelectFragment = "DATE_TRUNC('month', {date_column})",
        sqlGroupByFragment = "DATE_TRUNC('month', {date_column})",
        joinFragment = null,
        compatibleScopes = setOf("revenue", "members", "leads", "gx", "pt", "cash"),
    ),
    BRANCH(
        code = "branch",
        label = "Branch",
        labelAr = "الفرع",
        sqlSelectFragment = "br.name_en",
        sqlGroupByFragment = "br.name_en",
        joinFragment = "JOIN branches br ON br.id = {table}.branch_id",
        compatibleScopes = setOf("revenue", "members", "leads", "gx", "pt", "cash"),
    ),
    MEMBERSHIP_PLAN(
        code = "membership_plan",
        label = "Membership Plan",
        labelAr = "خطة العضوية",
        sqlSelectFragment = "mp.name_en",
        sqlGroupByFragment = "mp.name_en",
        joinFragment = "JOIN membership_plans mp ON mp.id = {table}.plan_id",
        compatibleScopes = setOf("revenue", "members"),
    ),
    CLASS_TYPE(
        code = "class_type",
        label = "Class Type",
        labelAr = "نوع الصف",
        sqlSelectFragment = "gct.name_en",
        sqlGroupByFragment = "gct.name_en",
        joinFragment = "JOIN gx_class_instances gci ON gci.id = gb.class_instance_id JOIN gx_class_types gct ON gct.id = gci.class_type_id",
        compatibleScopes = setOf("gx"),
    ),
    LEAD_SOURCE(
        code = "lead_source",
        label = "Lead Source",
        labelAr = "مصدر العميل",
        sqlSelectFragment = "ls.name_en",
        sqlGroupByFragment = "ls.name_en",
        joinFragment = "JOIN lead_sources ls ON ls.id = l.source_id",
        compatibleScopes = setOf("leads"),
    ),
    STAFF_MEMBER(
        code = "staff_member",
        label = "Staff Member",
        labelAr = "الموظف",
        sqlSelectFragment = "sm.first_name_en || ' ' || sm.last_name_en",
        sqlGroupByFragment = "sm.first_name_en, sm.last_name_en",
        joinFragment = "JOIN staff_members sm ON sm.id = {table}.staff_member_id",
        compatibleScopes = setOf("revenue", "leads"),
    ),
    ;

    companion object {
        private val BY_CODE = entries.associateBy { it.code }

        fun fromCode(code: String): DimensionCatalogue? = BY_CODE[code]

        fun all(): List<DimensionCatalogue> = entries
    }
}
