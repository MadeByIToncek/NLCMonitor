package space.itoncek.nlcmonitor.modules;

import static java.util.Collections.max;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import space.itoncek.nlcmonitor.DiscordHook;
import space.itoncek.utils.signal_processing.SignalDetector;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EventImpactHook extends ListenerAdapter implements DiscordHook {
	ArrayList<File> delete = new ArrayList<>();
	private boolean enabled;
	private JDA jda;

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (event.getFullCommandName().startsWith(getCommand().getName())) {
			if(!enabled) {
				event.replyEmbeds(new EmbedBuilder()
						.setAuthor(jda.getSelfUser().getName(), jda.getSelfUser().getAvatarUrl(), jda.getSelfUser().getAvatarUrl())
						.setTimestamp(ZonedDateTime.now())
						.setTitle("Module disabled!")
						.setDescription("This module was disabled, for further information, contact IToncek")
						.setColor(Color.RED)
						.build()).queue();
			} else try {
				File temp = File.createTempFile("solarbot", ".png");
				JSONArray dat = new JSONArray(IOUtils.toString(URI.create("https://services.swpc.noaa.gov/products/solar-wind/plasma-6-hour.json"), Charset.defaultCharset()));
				ArrayList<SWPCDataPoint> datapoints = new ArrayList<>();
				for (int i = 1; i < dat.length(); i++) {
					JSONArray jsonArray = dat.getJSONArray(i);
					int temperature;
					float pressure, speed;
					try {
						pressure = Float.parseFloat(jsonArray.getString(1));
					} catch (Exception e) {
						pressure = -1;
					}
					try {
						speed = Float.parseFloat(jsonArray.getString(2));
					} catch (Exception e) {
						speed = -1;
					}
					try {
						temperature = Integer.parseInt(jsonArray.getString(3));
					} catch (Exception e) {
						temperature = -1;
					}
					datapoints.add(new SWPCDataPoint(LocalDateTime.parse(jsonArray.getString(0), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")), pressure, speed, temperature));
				}

				BufferedImage img = new BufferedImage(1920, 1080, BufferedImage.TYPE_4BYTE_ABGR);
				Graphics2D g2 = img.createGraphics();

				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				ArrayList<Float> values = new ArrayList<>();

				for (SWPCDataPoint datapoint : datapoints) {
					values.add(datapoint.speed);
				}

				SignalDetector.SignalDetectorResult result = SignalDetector.analyzeDataForSignals(values.stream().map(x -> Double.valueOf(x + "")).toList(), 50, 3.5, .5);

				drawGraph(values, img, g2, Color.green);
				drawGraph(result.signals().stream().map(x -> x * 1f).toList(), img, g2, Color.red);

				g2.dispose();
				ImageIO.write(img, "png", temp);
				FileUpload fu = FileUpload.fromData(temp);
				event.getInteraction().replyFiles(fu).queue();
				delete.add(temp);
				fu.close();
			} catch (Exception e) {
				event.getInteraction().reply("Something weird had happened :shrug:").queue(m -> {
					try {
						File temp = File.createTempFile("solarbot", ".log");
						PrintStream ps = new PrintStream(temp, Charset.defaultCharset());
						e.printStackTrace(ps);

						delete.add(temp);
						m.editOriginalAttachments(FileUpload.fromData(temp)).queue();
					} catch (IOException ex) {
						throw new RuntimeException(ex);
					}
				});
			}
			;
		}
	}

	private void drawGraph(List<Float> datapoints, BufferedImage img, Graphics2D g2, Color GRAPH_COLOR) {
		final int BORDER_GAP = 30;
		final double MAX_SCORE = max(datapoints);
		final Stroke GRAPH_STROKE = new BasicStroke(3f);
		final int GRAPH_POINT_WIDTH = 12;
		final int Y_HATCH_CNT = 10;

		double xScale = ((double) img.getWidth() - 2 * BORDER_GAP) / (datapoints.size() - 1);
		double yScale = (img.getHeight() - 2 * BORDER_GAP) / (MAX_SCORE - MAX_SCORE * .1);

		ArrayList<Point> graphPoints = new ArrayList<>();
		for (int i = 0; i < datapoints.size(); i++) {
			if (datapoints.get(i) >= 0) {
				int x1 = (int) (i * xScale + BORDER_GAP);
				int y1 = (int) ((MAX_SCORE - datapoints.get(i)) * yScale + BORDER_GAP);
				graphPoints.add(new Point(x1, y1));
			}
		}
		// create x and y axes
		g2.drawLine(BORDER_GAP, img.getHeight() - BORDER_GAP, BORDER_GAP, BORDER_GAP);
		g2.drawLine(BORDER_GAP, img.getHeight() - BORDER_GAP, img.getWidth() - BORDER_GAP, img.getHeight() - BORDER_GAP);

		// create hatch marks for y axis.
		for (int i = 0; i < Y_HATCH_CNT; i++) {
			int x1 = GRAPH_POINT_WIDTH + BORDER_GAP;
			int y0 = img.getHeight() - (((i + 1) * (img.getHeight() - BORDER_GAP * 2)) / Y_HATCH_CNT + BORDER_GAP);
			g2.drawLine(BORDER_GAP, y0, x1, y0);
		}

		// and for x axis
		for (int i = 0; i < datapoints.size() - 1; i++) {
			int x0 = (i + 1) * (img.getWidth() - BORDER_GAP * 2) / (datapoints.size() - 1) + BORDER_GAP;
			int x1 = x0;
			int y0 = img.getHeight() - BORDER_GAP;
			int y1 = y0 - GRAPH_POINT_WIDTH;
			g2.drawLine(x0, y0, x1, y1);
		}

		g2.setColor(GRAPH_COLOR);
		g2.setStroke(GRAPH_STROKE);
		for (int i = 0; i < graphPoints.size() - 1; i++) {
			int x1 = graphPoints.get(i).x;
			int y1 = graphPoints.get(i).y;
			int x2 = graphPoints.get(i + 1).x;
			int y2 = graphPoints.get(i + 1).y;
			g2.drawLine(x1, y1, x2, y2);
		}
	}

	@Override
	public void setup(JDA jda) {
		this.jda = jda;
		jda.addEventListener(this);
	}

	@Override
	public void close(JDA jda) {
		jda.removeEventListener(this);
		delete.forEach(File::deleteOnExit);
	}

	@Override
	public String id() {
		return "eventImpactHook";
	}

	@Override
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public boolean autostartWithoutDev() {
		return false;
	}

	public record SWPCDataPoint(LocalDateTime date, float density, float speed,
								int temperature) implements Comparable<SWPCDataPoint> {
		@Override
		public int compareTo(@NotNull SWPCDataPoint o) {
			return Math.round(this.speed) - Math.round(o.speed);
		}
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public CommandData getCommand() {
		return Commands.slash("impact", "[DEV] Reacts with current particle speed around DSCOVR spacecraft and estimated arrival time");
	}
}
