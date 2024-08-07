package space.itoncek.nlcmonitor;

import net.dv8tion.jda.api.JDA;

import java.util.ArrayList;

public interface DiscordMonitor {
	void setup(JDA jda);

	void close(JDA jda);

	boolean autostartWithoutDev();

	String id();

	void executeCheck();
}
