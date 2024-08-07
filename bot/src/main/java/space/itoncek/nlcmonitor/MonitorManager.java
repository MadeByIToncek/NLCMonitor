package space.itoncek.nlcmonitor;

import net.dv8tion.jda.api.JDA;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MonitorManager {
	private final JDA jda;
	private final ArrayList<DiscordMonitor> monitors = new ArrayList<>();
	private DiscordMonitor[] activeMonitors;
	Timer timer = new Timer("MonitorRuntime");

	public MonitorManager(JDA jda) {
		this.jda = jda;
	}

	public void register(DiscordMonitor monitor) {
		monitors.add(monitor);
	}

	public void start() {
		for (DiscordMonitor monitor : monitors) {
			if (DiscordBot.dev || monitor.autostartWithoutDev()) {
				monitor.setup(jda);
			} else {
				monitors.remove(monitor);
			}
		}

		activeMonitors = new DiscordMonitor[monitors.size()];
		for (int i = 0; i < monitors.size(); i++) {
			activeMonitors[i] = monitors.get(i);
		}

		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				if (activeMonitors == null) return;
				else {
					for (DiscordMonitor monitor : activeMonitors) {
						monitor.executeCheck();
					}
				}
			}
		}, 0, 3 * 60 * 1000);
	}

	public void awaitShutdown() {
		timer.cancel();
		timer.purge();
		for (DiscordMonitor m : activeMonitors) {
			m.close(jda);
		}
	}
}
