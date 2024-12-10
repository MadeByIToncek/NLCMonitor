package space.itoncek.nlcmonitor;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import space.itoncek.nlcmonitor.modules.EventImpactHook;
//import space.itoncek.nlcmonitor.modules.NlcHook;
import space.itoncek.nlcmonitor.modules.SolarFlareHook;
import space.itoncek.nlcmonitor.timers.LunarXVTimer;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DiscordBot extends ListenerAdapter {
	public static final boolean dev = System.getenv("dev").equals("true");
	private static final org.slf4j.Logger log = LoggerFactory.getLogger(DiscordBot.class);
	private static JDA jda;
	private static BlueSkyRuntime bsky;
	private static final ArrayList<DiscordManager<?>> managers = new ArrayList<>();

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

		bsky = new BlueSkyRuntime();

		//Hooks
		HookManager hm = new HookManager(jda);
		//hm.register(new NlcHook());
		hm.register(new SolarFlareHook());
		hm.register(new EventImpactHook());

		managers.add(hm);
//		MonitorManager mm = new MonitorManager(jda);
//		mm.register(new SolarFlareMonitor());
//
//		managers.add(mm);

		TimerManager tm = new TimerManager(jda,bsky);
		tm.register(new LunarXVTimer());

		managers.add(tm);

		ArrayList<CommandData> commands = new ArrayList<>();
		managers.forEach(x-> {
			commands.addAll(x.getCommands());
		});
		updateCommands(commands);

		managers.forEach(DiscordManager::start);
		jda.getPresence().setPresence(OnlineStatus.ONLINE, activity);

		log.info("Ready");

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				ExecutorService es = Executors.newFixedThreadPool(managers.size());
				for (DiscordManager<?> manager : managers) {
					es.submit(()-> {
						try {
							manager.awaitTermination();
						} catch (InterruptedException e) {
							log.error("Shutdown",e);
						}
					});
				}
				es.shutdown();
				bsky.close();
				jda.awaitShutdown();
			} catch (InterruptedException | IOException e) {
				log.error("Shutdown",e);
			}
		}));
	}

	private static void updateCommands(ArrayList<CommandData> commands) {
		ArrayList<CommandData> finalCommands = commands;
		commands = new ArrayList<>(commands.stream()
				.filter(y -> Collections.frequency(finalCommands.stream().map(CommandData::getName).toList(), y.getName()) == 1)
				.toList());

		commands.add(Commands.slash("modify-modules", "Disable specified module, Author only!")
				.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
				.addOption(OptionType.STRING, "module", "Module to modify", true, true)
				.addOption(OptionType.STRING, "enable", "Should this module be enabled or disabled?", true, true)
		);
		for (Guild guild : jda.getGuilds()) {
			guild.updateCommands().queue();
		}

		jda.updateCommands().addCommands(commands).queue();
	}


	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (event.getName().equals("modify-modules"))
			if (event.getInteraction().getUser().getId().equals("580098459802271744")) {
				switch (event.getFocusedOption().getName()) {
					case "module" -> {
						List<Command.Choice> options = managers.stream()
								.map(DiscordManager::getMemberNames)
								.mapMulti((x,y)->{
									for (String entry : x) {
										y.accept(entry);
									}
								})
								.map(x->(String)x)
								.filter(w->w.startsWith(event.getFocusedOption().getValue()))
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

//				if (module == null || enabled == null || hooks.get(module) == null) {
//					e.getInteraction().replyEmbeds(new EmbedBuilder()
//							.setAuthor(jda.getSelfUser().getName(), jda.getSelfUser().getAvatarUrl(), jda.getSelfUser().getAvatarUrl())
//							.setTimestamp(ZonedDateTime.now())
//							.setTitle("Internal faulire!")
//							.setDescription("Unable to decipher your request, try again.")
//							.setColor(Color.RED)
//							.build()).setEphemeral(true).queue();
//				} else {
//					hooks.get(module).setEnabled(enabled);
//					e.getInteraction().replyEmbeds(new EmbedBuilder()
//									.setAuthor(jda.getSelfUser().getName(), jda.getSelfUser().getAvatarUrl(), jda.getSelfUser().getAvatarUrl())
//									.setTimestamp(ZonedDateTime.now())
//									.setTitle("Module state modified!")
//									.setDescription("Module " + module + " was " + (enabled ? "enabled" : "disabled"))
//									.setColor(Color.GREEN)
//									.build())
//							.setEphemeral(true)
//							.queue();
//				}
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