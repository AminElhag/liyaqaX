package com.liyaqa.report.builder

object CompatibilityMatrix {
    private val METRIC_COMPATIBLE_DIMENSIONS: Map<MetricCatalogue, Set<DimensionCatalogue>> =
        MetricCatalogue.entries.associateWith { metric ->
            DimensionCatalogue.entries.filter { dimension ->
                metric.scope in dimension.compatibleScopes
            }.toSet()
        }

    fun isCompatible(
        metric: MetricCatalogue,
        dimension: DimensionCatalogue,
    ): Boolean = dimension in (METRIC_COMPATIBLE_DIMENSIONS[metric] ?: emptySet())

    fun compatibleDimensions(metric: MetricCatalogue): Set<DimensionCatalogue> = METRIC_COMPATIBLE_DIMENSIONS[metric] ?: emptySet()

    fun validateAll(
        metrics: List<MetricCatalogue>,
        dimensions: List<DimensionCatalogue>,
    ): String? {
        for (metric in metrics) {
            for (dimension in dimensions) {
                if (!isCompatible(metric, dimension)) {
                    return "Metric '${metric.code}' is not compatible with dimension '${dimension.code}'."
                }
            }
        }
        return null
    }
}
