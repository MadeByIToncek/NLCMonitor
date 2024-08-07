package space.itoncek.nlcmonitor;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.Map;

public interface DiscordHook {
	void setup(JDA jda);

	void close(JDA jda);

	boolean isEnabled();

	void setEnabled(boolean enabled);

	boolean autostartWithoutDev();

	String id();

	CommandData getCommand();
}
