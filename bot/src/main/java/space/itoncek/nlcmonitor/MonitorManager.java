package space.itoncek.nlcmonitor;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MonitorManager implements DiscordManager<DiscordMonitor>{
	private static final Logger log = LoggerFactory.getLogger(MonitorManager.class);
	private final JDA jda;
	private final ArrayList<DiscordMonitor> monitors = new ArrayList<>();
	private DiscordMonitor[] activeMonitors;
	ScheduledExecutorService timer = Executors.newScheduledThreadPool(1);

	public MonitorManager(JDA jda) {
		this.jda = jda;
	}

	@Override
	public void register(DiscordMonitor monitor) {
		monitors.add(monitor);
	}

	@Override
	public List<CommandData> getCommands() {
		return List.of();
	}

	@Override
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

		timer.scheduleAtFixedRate(() -> {
			if (activeMonitors != null) {
				for (DiscordMonitor monitor : activeMonitors) {
					monitor.executeCheck();
				}
			}
		}, 0, 3 * 60, TimeUnit.SECONDS);
	}

	@Override
	public void awaitTermination() throws InterruptedException {
		timer.shutdown();

		for (DiscordMonitor m : activeMonitors) {
			m.close(jda);
		}
	}

	@Override
	public List<String> getMemberNames() {
		return Arrays.stream(activeMonitors).map(DiscordMonitor::id).toList();
	}
}
