package space.itoncek.nlcmonitor;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.List;

public interface DiscordManager<T> {
	void register(T impl);

	List<CommandData> getCommands();

	void start();
	void awaitTermination() throws InterruptedException;

	List<String> getMemberNames();
}
