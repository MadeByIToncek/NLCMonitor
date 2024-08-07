package space.itoncek.nlcmonitor;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import space.itoncek.nlcmonitor.modules.EventImpactHook;
import space.itoncek.nlcmonitor.modules.NlcHook;
import space.itoncek.nlcmonitor.modules.SolarFlareHook;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DiscordBot extends ListenerAdapter {
	public static final boolean dev = System.getenv("dev").equals("true");
	private static JDA jda;
	private static final HashMap<String, DiscordHook> hooks = new HashMap<>();

	public static void main(String[] args) throws InterruptedException, IOException {
		// Note: It is important to register your ReadyListener before building
		Logger.getLogger("DEBUG").info("dev=" + dev);
		Activity activity = Activity.watching("clouds go by");
		if (dev)
			activity = Activity.streaming("my developement", "https://www.youtube.com/watch?v=dQw4w9WgXcQ");

		jda = JDABuilder.createDefault(FileUtils.readFileToString(new File("discord.token"), Charset.defaultCharset()))
				.setActivity(Activity.watching("everything being prepared"))
				.setStatus(OnlineStatus.DO_NOT_DISTURB)
				.addEventListeners(new DiscordBot())
				.build();

		// optionally block until JDA is ready
		jda.awaitReady();

		jda.getPresence().setPresence(OnlineStatus.IDLE, Activity.listening("to all modules getting ready"));

		List<DiscordHook> hookList = List.of(new EventImpactHook(), new NlcHook(), new SolarFlareHook());
		for (DiscordHook discordHook : hookList) {
			hooks.put(discordHook.id(), discordHook);
			if (discordHook.autostartWithoutDev()) {
				discordHook.setEnabled(true);
			} else {
				discordHook.setEnabled(false);
			}
		}

		updateCommands();

		jda.getPresence().setPresence(OnlineStatus.ONLINE, activity);

//		MonitorManager mm = new MonitorManager(jda);
//		mm.register(new SolarFlareMonitor());
//
//		mm.start();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			hooks.forEach((id, hook) -> {
				hook.close(jda);
			});
//			mm.awaitShutdown();
			try {
				jda.awaitShutdown();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}));
	}

	private static void updateCommands() {
		ArrayList<CommandData> commands = new ArrayList<>(hooks.size());
		hooks.values().stream().filter(DiscordHook::isEnabled).forEach(hook -> {
			hook.setup(jda);
			commands.add(hook.getCommand());
		});

		commands.add(Commands.slash("modify-modules", "Disable specified module, Author only!")
				.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ALL_PERMISSIONS))
				.addOption(OptionType.STRING, "module", "Module to modify", true, true)
				.addOption(OptionType.STRING, "enable", "Should this module be enabled or disabled?", true, true)
		);

		jda.updateCommands().queue();
		jda.getGuilds().stream()
				.map(g -> g.updateCommands().addCommands(commands))
				.forEach(RestAction::queue);
	}


	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (event.getName().equals("modify-modules"))
			if (event.getInteraction().getUser().getId().equals("580098459802271744")) {
				switch (event.getFocusedOption().getName()) {
					case "module" -> {
						List<Command.Choice> options = hooks.keySet().stream()
								.filter(word -> word.startsWith(event.getFocusedOption().getValue())) // only display words that start with the user's current input
								.map(word -> new Command.Choice(word, word)) // map the words to choices
								.collect(Collectors.toList());
						event.replyChoices(options).queue();
					}
					case "enable" -> {
						event.replyChoices(new Command.Choice("enable", "enable"), new Command.Choice("disable", "disable")).queue();
					}
				}
			} else {
				event.replyChoice("you-are-not-the-author", "This incident has been reported!").queue();
			}
	}

	@Override
	public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent e) {
		if (e.getInteraction().getName().equals("modify-modules")) {
			if (e.getInteraction().getUser().getId().equals("580098459802271744")) {
				String module = null;
				Boolean enabled = null;
				if (e.getInteraction().getOption("module").getType().equals(OptionType.STRING)) {
					module = e.getInteraction().getOption("module").getAsString();
				}
				if (e.getInteraction().getOption("enable").getType().equals(OptionType.STRING)) {
					enabled = e.getInteraction().getOption("enable").getAsString().equals("enable");
				}

				if (module == null || enabled == null || hooks.get(module) == null) {
					e.getInteraction().replyEmbeds(new EmbedBuilder()
							.setAuthor(jda.getSelfUser().getName(), jda.getSelfUser().getAvatarUrl(), jda.getSelfUser().getAvatarUrl())
							.setTimestamp(ZonedDateTime.now())
							.setTitle("Internal faulire!")
							.setDescription("Unable to decipher your request, try again.")
							.setColor(Color.RED)
							.build()).setEphemeral(true).queue();
				} else {
					hooks.get(module).setEnabled(enabled);
					e.getInteraction().replyEmbeds(new EmbedBuilder()
									.setAuthor(jda.getSelfUser().getName(), jda.getSelfUser().getAvatarUrl(), jda.getSelfUser().getAvatarUrl())
									.setTimestamp(ZonedDateTime.now())
									.setTitle("Module state modified!")
									.setDescription("Module " + module + " was " + (enabled ? "enabled" : "disabled"))
									.setColor(Color.GREEN)
									.build())
							.setEphemeral(true)
							.queue();
				}
			} else {
				e.getInteraction().reply("<@580098459802271744>").addEmbeds(new EmbedBuilder()
						.setAuthor(jda.getSelfUser().getName(), jda.getSelfUser().getAvatarUrl(), jda.getSelfUser().getAvatarUrl())
						.setTimestamp(ZonedDateTime.now())
						.setTitle("Intruder alert!")
						.setDescription("You are not IToncek, this incident has been reported")
						.setColor(Color.RED)
						.build()).queue();
			}
		}
	}
}