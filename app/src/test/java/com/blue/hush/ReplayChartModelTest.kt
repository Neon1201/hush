package com.blue.hush

import com.blue.hush.session.StateSample
import com.blue.hush.ui.ReplayMetric
import com.blue.hush.ui.ReplayViewport
import com.blue.hush.ui.smoothedCurvePoints
import com.blue.hush.ui.metricCoverage
import com.blue.hush.ui.MetricCoverage
import com.blue.hush.ui.CoverageStatus
import org.junit.Assert.*
import org.junit.Test

class ReplayChartModelTest {
    @Test fun zoomKeepsAnchorAndClampsPanAndScale() {
        val zoomed = ReplayViewport().transform(2f, 0.25f, 0f, 600)
        assertEquals(0.5f, zoomed.span, 0.0001f)
        assertEquals(150f, zoomed.secondAt(0.25f, 600), 0.0001f)
        assertEquals(0f, zoomed.transform(1f, 0.5f, 10f, 600).start, 0f)
        val end = zoomed.transform(1f, 0.5f, -10f, 600)
        assertEquals(1f, end.end, 0f)
        assertEquals(0.1f, zoomed.transform(100f, 0.5f, 0f, 600).span, 0f)
        assertEquals(ReplayViewport(), zoomed.transform(0.001f, 0.5f, 0f, 600))
        assertEquals(zoomed, zoomed.transform(Float.NaN, 0.5f, 0f, 600))
        assertEquals(ReplayViewport(), ReplayViewport().transform(10f, 0.5f, 0f, 1))
        assertEquals(0f, zoomed.reveal(0f, 600).start, 0f)
        assertEquals(1f, zoomed.reveal(600f, 600).end, 0f)
    }

    @Test fun fixedMappingAndDisplayKeepActualBpm() {
        assertEquals(0.0, ReplayMetric.HEART_RATE.level(40.0), 0.0)
        assertEquals(1.0, ReplayMetric.HEART_RATE.level(180.0), 0.0)
        assertEquals(0.5, ReplayMetric.HEART_RATE.level(110.0), 0.0)
        val sample = StateSample(1, alpha = 0.3214, valid = true, eegBandsAvailable = true,
            calmness = 0.765, heartRateBpm = 110.0)
        assertEquals("Heart Rate 110 BPM", ReplayMetric.HEART_RATE.label(sample))
        assertEquals("Alpha 32.1", ReplayMetric.ALPHA.label(sample))
        assertEquals("Calmness 77", ReplayMetric.CALMNESS.label(sample))
    }

    @Test fun missingEegDoesNotHideIndependentMeasurements() {
        val sample = StateSample(2, alpha = 0.4, stillness = 0.9, valid = true,
            heartRateBpm = 80.0, algorithmVersion = 5)
        assertNull(ReplayMetric.ALPHA.value(sample))
        assertNull(ReplayMetric.CALMNESS.value(sample))
        assertEquals(0.9, ReplayMetric.STABILITY.value(sample)!!, 0.0)
        assertEquals(80.0, ReplayMetric.HEART_RATE.value(sample)!!, 0.0)
        ReplayMetric.entries.forEach { assertNull(it.value(sample.copy(valid = false))) }
    }

    @Test fun invalidMeasurementsStayMissing() {
        val sample = StateSample(1, alpha = Double.NaN, theta = -0.1, beta = 1.1,
            stillness = Double.POSITIVE_INFINITY, calmness = -1.0, heartRateBpm = 181.0,
            valid = true, eegBandsAvailable = true)
        ReplayMetric.entries.forEach {
            assertNull(it.value(sample))
            assertEquals("${it.title} —", it.label(sample))
        }
    }

    @Test fun threeSecondAverageSharesCurveValuesAndPreservesGaps() {
        val samples = listOf(
            StateSample(0, calmness = 0.0, valid = true),
            StateSample(1, calmness = 0.9, valid = true),
            StateSample(2, calmness = 0.0, valid = true),
            StateSample(3, valid = true),
            StateSample(4, calmness = 1.0, valid = true),
            StateSample(7, calmness = 0.2, valid = true),
        )
        val points = smoothedCurvePoints(samples, ReplayMetric.CALMNESS::value).toMap()
        assertEquals(0.45, points.getValue(0), 0.00001)
        assertEquals(0.3, points.getValue(1), 0.00001)
        assertEquals(0.45, points.getValue(2), 0.00001)
        assertFalse(points.containsKey(3))
        assertEquals(1.0, points.getValue(4), 0.0)
        assertEquals(0.2, points.getValue(7), 0.0)
    }
    @Test fun coverageCountsIndependentMeasuredSecondsWithoutFillingGaps() {
        val samples = listOf(
            StateSample(1, valid = true, algorithmVersion = 5, calmness = 0.5, stillness = 0.8),
            StateSample(2, valid = true, algorithmVersion = 5, heartRateBpm = 80.0),
            StateSample(3, valid = false, algorithmVersion = 5, calmness = 0.5),
            StateSample(4, valid = true, algorithmVersion = 0, calmness = 0.5),
        )
        assertEquals(25, metricCoverage(samples, 4, ReplayMetric.CALMNESS).percent)
        assertEquals(1, metricCoverage(samples, 4, ReplayMetric.HEART_RATE).measuredSeconds)
        assertEquals(0, metricCoverage(samples, 0, ReplayMetric.CALMNESS).percent)
    }

    @Test fun coverageThresholdsUseExactRatio() {
        assertEquals(CoverageStatus.LOW, MetricCoverage(49, 100).status)
        assertEquals(CoverageStatus.LIMITED, MetricCoverage(50, 100).status)
        assertEquals(CoverageStatus.LIMITED, MetricCoverage(699, 1000).status)
        assertEquals(CoverageStatus.AVAILABLE, MetricCoverage(70, 100).status)
        assertEquals(CoverageStatus.LOW, MetricCoverage(0, 0).status)
    }

}
