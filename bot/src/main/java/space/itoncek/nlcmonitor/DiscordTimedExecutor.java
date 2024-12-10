package space.itoncek.nlcmonitor;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.io.IOException;
import java.time.LocalTime;

public interface DiscordTimedExecutor {
	boolean autostartWithoutDev();

	String id();

	LocalTime execTime();

	void execute(JDA jda, BlueSkyRuntime bsky, TextChannel c) throws IOException;

	CommandData getCommand();

	boolean isEnabled();

	void setEnabled(boolean enabled);
}