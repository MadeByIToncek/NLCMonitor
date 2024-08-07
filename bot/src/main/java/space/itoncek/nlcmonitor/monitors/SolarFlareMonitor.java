package space.itoncek.nlcmonitor.monitors;

import static java.util.Collections.max;
import net.dv8tion.jda.api.JDA;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.itoncek.nlcmonitor.DiscordMonitor;
import space.itoncek.utils.signal_processing.SignalDetector;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;

public class SolarFlareMonitor implements DiscordMonitor {
	private static final Logger log = LoggerFactory.getLogger(SolarFlareMonitor.class);
	JDA jda;
	ArrayList<File> delete = new ArrayList<>();

	@Override
	public void setup(JDA jda) {
		this.jda = jda;
	}

	@Override
	public void close(JDA jda) {
		delete.forEach(File::deleteOnExit);
	}

	@Override
	public boolean autostartWithoutDev() {
		return false;
	}

	@Override
	public String id() {
		return "flare-monitor";
	}

	@Override
	public void executeCheck() {
		try {
			String uri = "https://services.swpc.noaa.gov/json/goes/primary/xrays-6-hour.json";

			JSONArray dat = new JSONArray(IOUtils.toString(URI.create(uri), Charset.defaultCharset()));
			ArrayList<Double> values = new ArrayList<>();
			ArrayList<LocalDateTime> times = new ArrayList<>();
			for (int i = 0; i < dat.length(); i++) {
				JSONObject o = dat.getJSONObject(i);
				if (o.getString("energy").equals("0.1-0.8nm")) {
					values.add(o.getDouble("flux"));
					times.add(LocalDateTime.parse(o.getString("time_tag")));
				}
			}

			ArrayList<Double> hackedValues = new ArrayList<>(values);
			double tempval = 0;
			boolean replacing = false;
			for (int i = 0; i < values.size(); i++) {
				double v = values.get(i);
				if (v == 0 && !replacing) {
					replacing = true;
					tempval = values.get(i - 1);
					v = tempval;
				} else if (v == 0) {
					v = tempval;
				} else if (replacing) {
					replacing = false;
				}
				hackedValues.set(i, v);
			}

			SignalDetector.SignalDetectorResult result = SignalDetector.analyzeDataForSignals(hackedValues.stream().map(x -> Double.valueOf(x + "")).toList(), 5, 6., .05);

			int previous = 0;
			boolean running = false;
			HashMap<Double, Integer> vals = new HashMap<>();

			for (int i = 0; i < result.signals().size(); i++) {
				Integer val = result.signals().get(i);
				if (running) {
					vals.put(values.get(i), i);
				}
				if (val == 1 && previous == 0) {
					running = true;
				} else if (((val == 0) && (previous == 1)) || ((val == 1) && (i == (result.signals().size() - 1)))) {
					running = false;
					double max = max(vals.keySet());
					double logmax = Math.log10(max);
					String flareClass;

					double floor;
					if (logmax >= -4) {
						flareClass = "X";
						floor = -4;
					} else if (logmax >= -5) {
						flareClass = "M";
						floor = -5;
					} else if (logmax >= -6) {
						flareClass = "C";
						floor = -6;
					} else if (logmax >= -7) {
						flareClass = "B";
						floor = -7;
					} else {
						flareClass = "A";
						floor = -8;
					}

					if (logmax >= -4.5) {
						flareClass = flareClass + Double.toString(max * Math.pow(10, floor)).substring(0, "0.00".length());
						LocalDateTime start = times.get(0);
						LocalDateTime top = times.get(vals.get(max));
						LocalDateTime end = times.get(i);
						SolarFlareDatabaseManager.SolarFlare flare = new SolarFlareDatabaseManager.SolarFlare(start, top, end, flareClass);

//						drawFlareLine(vals.get(max), flareClass, g2, img, BORDER_GAP, xScale);
						vals.clear();
					}
				}
				previous = val;
			}
		} catch (IOException e) {
			log.error("Cannot execute check!", e);
		}
	}

}
