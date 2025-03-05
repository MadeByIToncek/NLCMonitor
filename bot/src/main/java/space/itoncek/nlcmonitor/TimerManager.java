package space.itoncek.nlcmonitor;

import java.awt.Color;
import java.io.IOException;
import static java.lang.Thread.sleep;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimerManager implements DiscordManager<DiscordTimedExecutor>{
	private static final Logger log = LoggerFactory.getLogger(TimerManager.class);
	private final JDA jda;
	private final BlueSkyRuntime bsky;
	ArrayList<DiscordTimedExecutor> execs = new ArrayList<>();
	ScheduledExecutorService stpex;

	public TimerManager(JDA jda, BlueSkyRuntime bsky) {
		this.jda = jda;
		this.bsky = bsky;
	}

	@Override
	public void register(DiscordTimedExecutor impl) {
		execs.add(impl);
	}

	@Override
	public List<CommandData> getCommands() {
		return execs.stream().filter(x->x.autostartWithoutDev() || DiscordBot.dev).map(DiscordTimedExecutor::getCommand).toList();
	}

	@Override
	public void start() {
		stpex = Executors.newScheduledThreadPool(execs.size());

		LocalDate today = LocalDate.now();
		for (DiscordTimedExecutor exec : execs) {
			if(exec.autostartWithoutDev() || DiscordBot.dev) {
				LocalTime execTime = exec.execTime();
				LocalDateTime execDateTime;
				if (LocalDateTime.now().isAfter(LocalDateTime.of(today, execTime))) {
					execDateTime = LocalDateTime.of(today.plusDays(1), execTime);
				} else {
					execDateTime = LocalDateTime.of(today, execTime);
				}
				long secondsUntilExecTime = LocalDateTime.now().until(execDateTime, ChronoUnit.SECONDS);
				stpex.scheduleAtFixedRate(() -> {
					try {
						exec.execute(jda,bsky, null);
					} catch (IOException e) {
						log.error("exec error!", e);
					}
				}, secondsUntilExecTime, 86400, TimeUnit.SECONDS);

				jda.addEventListener(new ListenerAdapter() {
					@Override
					public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
						if (event.getFullCommandName().startsWith(exec.getCommand().getName())) {
							if (!exec.isEnabled()) {
								event.replyEmbeds(new EmbedBuilder()
										.setAuthor(jda.getSelfUser().getName(), jda.getSelfUser().getAvatarUrl(), jda.getSelfUser().getAvatarUrl())
										.setTimestamp(ZonedDateTime.now())
										.setTitle("Module disabled!")
										.setDescription("This module was disabled, for further information, contact IToncek")
										.setColor(Color.RED)
										.build()).queue();
							} else {
								event.reply("Request sent!").queue(m -> {
									try {
										sleep(5000);
									} catch (InterruptedException e) {
										log.error("await error", e);
									} finally {
										m.deleteOriginal().queue();
									}
								});
								try {
									exec.execute(jda,null, event.getChannel());
								} catch (IOException e) {
									log.error("exec error", e);
								}
							}
						}
						super.onSlashCommandInteraction(event);
					}
				});
				exec.setEnabled(true);
			} else {
				exec.setEnabled(false);
			}
		}
	}


	@Override
	public void awaitTermination() throws InterruptedException {
		stpex.shutdown();
	}

	@Override
	public List<String> getMemberNames() {
		return execs.stream().map(DiscordTimedExecutor::id).toList();
	}
}
