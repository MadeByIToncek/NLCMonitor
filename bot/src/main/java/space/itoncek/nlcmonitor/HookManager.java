package space.itoncek.nlcmonitor;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.ArrayList;
import java.util.List;

public class HookManager implements DiscordManager<DiscordHook> {
	private final JDA jda;
	ArrayList<DiscordHook> hooks = new ArrayList<>();

	public HookManager(JDA jda) {
		this.jda = jda;
	}

	@Override
	public void register(DiscordHook impl) {
		hooks.add(impl);
	}

	@Override
	public List<CommandData> getCommands() {
		return hooks.stream().filter(x->x.autostartWithoutDev() || DiscordBot.dev).map(DiscordHook::getCommand).toList();
	}

	@Override
	public void start() {
		hooks.forEach(x-> {
			if(x.autostartWithoutDev() || DiscordBot.dev) {
				x.setup(jda);
				x.setEnabled(true);
			} else {
				x.setEnabled(false);
			}
		});
	}

	@Override
	public void awaitTermination() {
		hooks.forEach(x-> {
			x.close(jda);
		});
	}

	@Override
	public List<String> getMemberNames() {
		return hooks.stream().map(DiscordHook::id).toList();
	}
}
