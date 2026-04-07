package com.liyaqa.report.builder

enum class FilterCatalogue(
    val code: String,
    val label: String,
    val labelAr: String,
    val sqlWhereFragment: String,
    val parameterName: String,
) {
    BRANCH_ID(
        code = "branch_id",
        label = "Branch",
        labelAr = "الفرع",
        sqlWhereFragment = "{table}.branch_id = :filterBranchId",
        parameterName = "filterBranchId",
    ),
    PLAN_ID(
        code = "plan_id",
        label = "Membership Plan",
        labelAr = "خطة العضوية",
        sqlWhereFragment = "{table}.plan_id = :filterPlanId",
        parameterName = "filterPlanId",
    ),
    CLASS_TYPE_ID(
        code = "class_type_id",
        label = "Class Type",
        labelAr = "نوع الصف",
        sqlWhereFragment = "gci.class_type_id = :filterClassTypeId",
        parameterName = "filterClassTypeId",
    ),
    LEAD_SOURCE_ID(
        code = "lead_source_id",
        label = "Lead Source",
        labelAr = "مصدر العميل",
        sqlWhereFragment = "l.source_id = :filterLeadSourceId",
        parameterName = "filterLeadSourceId",
    ),
    STAFF_MEMBER_ID(
        code = "staff_member_id",
        label = "Staff Member",
        labelAr = "الموظف",
        sqlWhereFragment = "{table}.staff_member_id = :filterStaffMemberId",
        parameterName = "filterStaffMemberId",
    ),
    ;

    companion object {
        private val BY_CODE = entries.associateBy { it.code }

        fun fromCode(code: String): FilterCatalogue? = BY_CODE[code]

        fun all(): List<FilterCatalogue> = entries
    }
}
