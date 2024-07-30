package space.itoncek.utils.signal_processing;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SignalDetector {
	public static SignalDetectorResult analyzeDataForSignals(List<Double> data, int lag, Double threshold, Double influence) {
		// init stats instance
		SummaryStatistics stats = new SummaryStatistics();

		// the results (peaks, 1 or -1) of our algorithm
		List<Integer> signals = new ArrayList<>(Collections.nCopies(data.size(), 0));

		// filter out the signals (peaks) from our original list (using influence arg)
		List<Double> filteredData = new ArrayList<>(data);

		// the current average of the rolling window
		List<Double> avgFilter = new ArrayList<>(Collections.nCopies(data.size(), 0.0d));

		// the current standard deviation of the rolling window
		List<Double> stdFilter = new ArrayList<>(Collections.nCopies(data.size(), 0.0d));

		// init avgFilter and stdFilter
		for (int i = 0; i < lag; i++) {
			stats.addValue(data.get(i));
		}
		avgFilter.set(lag - 1, stats.getMean());
		stdFilter.set(lag - 1, Math.sqrt(stats.getPopulationVariance())); // getStandardDeviation() uses sample variance
		stats.clear();

		// loop input starting at end of rolling window
		for (int i = lag; i < data.size(); i++) {

			// if the distance between the current value and average is enough standard deviations (threshold) away
			if (Math.abs((data.get(i) - avgFilter.get(i - 1))) > threshold * stdFilter.get(i - 1)) {

				// this is a signal (i.e. peak), determine if it is a positive or negative signal
				if (data.get(i) > avgFilter.get(i - 1)) {
					signals.set(i, 1);
				} else {
					signals.set(i, -1);
				}

				// filter this signal out using influence
				filteredData.set(i, (influence * data.get(i)) + ((1 - influence) * filteredData.get(i - 1)));
			} else {
				// ensure this signal remains a zero
				signals.set(i, 0);
				// ensure this value is not filtered
				filteredData.set(i, data.get(i));
			}

			// update rolling average and deviation
			for (int j = i - lag; j < i; j++) {
				stats.addValue(filteredData.get(j));
			}
			avgFilter.set(i, stats.getMean());
			stdFilter.set(i, Math.sqrt(stats.getPopulationVariance()));
			stats.clear();
		}

		return new SignalDetectorResult(signals,filteredData,avgFilter,stdFilter);

	} // end

	public record SignalDetectorResult(List<Integer> signals, List<Double> filteredData, List<Double> avgFilter, List<Double> stdFilter) {}
}
