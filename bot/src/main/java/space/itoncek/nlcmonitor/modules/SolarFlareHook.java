package space.itoncek.nlcmonitor.modules;

import static com.twelvemonkeys.lang.StringUtil.pad;
import static java.util.Collections.max;
import static java.util.Collections.min;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
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
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class SolarFlareHook extends ListenerAdapter implements DiscordHook {
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
				JSONArray dat = new JSONArray(IOUtils.toString(URI.create("https://services.swpc.noaa.gov/json/goes/primary/xrays-6-hour.json"), Charset.defaultCharset()));

				ArrayList<Double> values = new ArrayList<>();
				for (int i = 0; i < dat.length(); i++) {
					JSONObject o = dat.getJSONObject(i);
					if (o.getString("energy").equals("0.1-0.8nm")) {
						values.add(o.getDouble("flux"));
					}
				}

				BufferedImage img = new BufferedImage(3840, 2160, BufferedImage.TYPE_4BYTE_ABGR);
				Graphics2D g2 = img.createGraphics();

				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				SignalDetector.SignalDetectorResult result = SignalDetector.analyzeDataForSignals(values.stream().map(x -> Double.valueOf(x + "")).toList(), 25, 3.5, .05);

				drawGraph(values, img, g2, Color.green);
				drawGraph(result.signals().stream().map(x -> x * 1d).toList(), img, g2, Color.red);

				g2.dispose();
				ImageIO.write(img, "png", temp);
				FileUpload fu = FileUpload.fromData(temp);
				event.getInteraction().replyFiles(fu).queue(m -> {
					StringJoiner sb = new StringJoiner("\n");
					sb.add("```diff");
					sb.add("  " + "-".repeat(55));
					generateHeader(sb);
					int previous = 0;
					boolean running = false;
					ArrayList<Double> vals = new ArrayList<>();
					String start = "";
					for (int i = 0; i < result.signals().size(); i++) {
						Integer val = result.signals().get(i);
						if (running) {
							vals.add(values.get(i));
						}
						if (val == 1 && previous == 0) {
							running = true;
							start = dat.getJSONObject(i).getString("time_tag");
						} else if (((val == 0) && (previous == 1)) || ((val == 1) && (i == (result.signals().size() - 1)))) {
							running = false;
							double max = max(vals);
							double logmax = Math.log10(max);
							String flareClass;

							if (logmax >= -4) flareClass = "X";
							else if (logmax >= -5) flareClass = "M";
							else if (logmax >= -6) flareClass = "C";
							else if (logmax >= -7) flareClass = "B";
							else flareClass = "A";

							double floor = Math.floor(max);
							double ceil = Math.ceil(max);
							double ratio = (max - floor) / (ceil - floor);
							String tag = " ";
							if(ratio*10 > 8 && flareClass.equals("C") || flareClass.equals("M") || flareClass.equals("X")) {
								tag = "-";
							}

							if((i == (result.signals().size() - 1))) {
								tag = "+";
							}

							flareClass = flareClass + Double.toString(ratio * 10).substring(0, "0.00".length());


							String end = dat.getJSONObject(i).getString("time_tag");
							StringJoiner js = new StringJoiner(" | ");
							js.add(flareClass);
							js.add(start);
							js.add(end);

							sb.add(tag+" | " + js + " |");

							vals.clear();
							start = "";
						}
						previous = val;
					}
					sb.add("  " + "-".repeat(55));
					sb.add("```");
					m.editOriginal(sb.toString()).queue();
				});
				fu.close();
				delete.add(temp);
			} catch (Exception e) {
				event.getInteraction().reply("Something weird had happened :shrug:").queue(m -> {
					try {
						File temp = File.createTempFile("solarbot", ".log");
						PrintStream ps = new PrintStream(temp, Charset.defaultCharset());
						e.printStackTrace(ps);

						m.editOriginalAttachments(FileUpload.fromData(temp)).queue();
						delete.add(temp);
					} catch (IOException ex) {
						throw new RuntimeException(ex);
					}
				});
			}
		}
	}

	private void generateHeader(StringJoiner sb) {
		StringJoiner js = new StringJoiner(" | ");
		js.add("CLASS");
		js.add(pad("START", 20, " ", false));
		js.add(pad("END", 20, " ", false));
		sb.add("  | " + js + " |");
	}

	private void drawGraph(List<Double> datapoints, BufferedImage img, Graphics2D g2, Color GRAPH_COLOR) {
		final int BORDER_GAP = 30;
		final double MAX_SCORE = max(datapoints);
		final double MIN_SCORE = min(datapoints);
		final Stroke GRAPH_STROKE = new BasicStroke(8f);
		final int GRAPH_POINT_WIDTH = 12;
		final int Y_HATCH_CNT = 10;

		double xScale = ((double) img.getWidth() - (2 * BORDER_GAP)) / (datapoints.size() - 1);
		double yScale = ((img.getHeight() - (2 * BORDER_GAP)) - (MIN_SCORE - (MIN_SCORE * .1))) / (MAX_SCORE + (MAX_SCORE * .1));

		ArrayList<Point> graphPoints = new ArrayList<>();
		for (int i = 0; i < datapoints.size(); i++) {
			if (datapoints.get(i) >= 0) {
				int x1 = (int) (i * xScale + BORDER_GAP);
				int y1 = (int) ((MAX_SCORE - datapoints.get(i)) * yScale + BORDER_GAP);
				graphPoints.add(new Point(x1, y1));
			}
		}

		g2.setColor(Color.WHITE);
		// create x and y axes
		g2.drawLine(BORDER_GAP, img.getHeight() - BORDER_GAP, BORDER_GAP, BORDER_GAP);
		g2.drawLine(BORDER_GAP, img.getHeight() - BORDER_GAP, img.getWidth() - BORDER_GAP, img.getHeight() - BORDER_GAP);

		// create hatch marks for y-axis.
		for (int i = 0; i < Y_HATCH_CNT; i++) {
			int x1 = GRAPH_POINT_WIDTH + BORDER_GAP;
			int y0 = img.getHeight() - (((i + 1) * (img.getHeight() - BORDER_GAP * 2)) / Y_HATCH_CNT + BORDER_GAP);
			g2.drawLine(BORDER_GAP, y0, x1, y0);
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
	public CommandData getCommand() {
		return Commands.slash("flare", "Shows flares that happened in the past 6 hours.");
	}

	@Override
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public String id() {
		return "solarFlareHook";
	}

	@Override
	public boolean autostartWithoutDev() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}
}