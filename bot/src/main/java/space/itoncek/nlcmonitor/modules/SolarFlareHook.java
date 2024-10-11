package space.itoncek.nlcmonitor.modules;

import static java.util.Collections.max;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.itoncek.nlcmonitor.DiscordBot;
import space.itoncek.nlcmonitor.DiscordHook;
import space.itoncek.utils.signal_processing.SignalDetector;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.List;

public class SolarFlareHook extends ListenerAdapter implements DiscordHook {
	private static final Logger log = LoggerFactory.getLogger(SolarFlareHook.class);
	ArrayList<File> delete = new ArrayList<>();
	int previousX = 0;
	int previousLength = 0;
	int previousOffset = 0;
	private boolean enabled;
	private JDA jda;

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
		return Commands.slash("flare", "Shows flares that happened in the past 6 hours.")
				.addOptions(new OptionData(OptionType.STRING, "history", "Load historical data")
						.addChoice("6 Hours", "6hour")
						.addChoice("1 Day", "1day")
						.addChoice("3 Days", "3day")
						.addChoice("7 Days", "7day")
						.setRequired(false)
						.setAutoComplete(false));
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

	@Override
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (event.getFullCommandName().startsWith(getCommand().getName())) {
			if (!enabled) {
				event.replyEmbeds(new EmbedBuilder()
						.setAuthor(jda.getSelfUser().getName(), jda.getSelfUser().getAvatarUrl(), jda.getSelfUser().getAvatarUrl())
						.setTimestamp(ZonedDateTime.now())
						.setTitle("Module disabled!")
						.setDescription("This module was disabled, for further information, contact IToncek")
						.setColor(Color.RED)
						.build()).queue();
			} else event.getInteraction().deferReply().queue(q -> {
				try {
					File temp = File.createTempFile("solarbot", ".png");
					String primary = "https://services.swpc.noaa.gov/json/goes/primary/xrays-";
					String secondary = "https://services.swpc.noaa.gov/json/goes/secondary/xrays-";

					OptionMapping opt = event.getInteraction().getOption("history");
					double flareThreshold = -5;
					if (opt != null) {
						String attachment = switch (opt.getAsString()) {
							case "1day" -> "1-day";
							case "3day" -> {
								flareThreshold = -4.6;
								yield "3-day";
							}
							case "7day" -> {
								flareThreshold = -4.25;
								yield "7-day";
							}
							default -> "6-hour";
						};
						primary += attachment;
						secondary += attachment;
					} else {
						primary += "6-hour";
						secondary += "6-hour";
					}
					primary += ".json";
					secondary += ".json";

					q.editOriginal("Downloading GOES primary").queue();
					JSONArray primaryData = new JSONArray(IOUtils.toString(URI.create(primary), Charset.defaultCharset()));
					q.editOriginal("Downloading GOES secondary").queue();
					JSONArray secondaryData = new JSONArray(IOUtils.toString(URI.create(secondary), Charset.defaultCharset()));

					q.editOriginal("Correcting for orbital parameters").queue();

					TreeMap<ZonedDateTime, Double> pd = new TreeMap<>();
					for (int i = 0; i < primaryData.length(); i++) {
						JSONObject o = primaryData.getJSONObject(i);
						if (o.getString("energy").equals("0.1-0.8nm")) {
							pd.put(ZonedDateTime.parse(o.getString("time_tag")), o.getDouble("flux"));
						}
					}
					TreeMap<ZonedDateTime, Double> sd = new TreeMap<>();
					for (int i = 0; i < secondaryData.length(); i++) {
						JSONObject o = secondaryData.getJSONObject(i);
						if (o.getString("energy").equals("0.1-0.8nm")) {
							sd.put(ZonedDateTime.parse(o.getString("time_tag")), o.getDouble("flux"));
						}
					}
					ArrayList<Double> values = new ArrayList<>();
					pd.forEach((date, val)-> {
						double pri = val;
						double sec = sd.get(date);

						values.add(Math.max(pri,sec));
					});

					q.editOriginal("Generating plot").queue();
					BufferedImage img = new BufferedImage(3840, 2160, BufferedImage.TYPE_4BYTE_ABGR);
					Graphics2D g2 = img.createGraphics();

					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
					g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

					ArrayList<Double> hackedValues = new ArrayList<>(values);
					double tempval = 0;
					boolean replacing = false;
					for (int i = 0; i < values.size(); i++) {
						double v = values.get(i);
						if (v == 0 && !replacing) {
							replacing = true;
							tempval = values.get(i - 1);
							v = tempval;
						} else if (v == 0) {
							v = tempval;
						} else if (replacing) {
							replacing = false;
						}
						hackedValues.set(i, v);
					}

					SignalDetector.SignalDetectorResult result = SignalDetector.analyzeDataForSignals(hackedValues.stream().map(x -> Double.valueOf(x + "")).toList(), 5, 6., .05);

					final int BORDER_GAP = 30;
					int height = (img.getHeight() - (2 * BORDER_GAP));
					final Stroke GRAPH_STROKE = new BasicStroke(8f);
					final int GRAPH_POINT_WIDTH = 12;
					final int Y_HATCH_CNT = 10;
					double xScale = ((double) img.getWidth() - (2 * BORDER_GAP)) / (values.size() - 1);

					drawClassLine(-6, "C", g2, img, BORDER_GAP, height);
					drawClassLine(-5, "M", g2, img, BORDER_GAP, height);
					drawClassLine(-4, "X", g2, img, BORDER_GAP, height);
					drawClassLine(-3, "X10", g2, img, BORDER_GAP, height);

					g2.setColor(Color.WHITE);
					g2.setStroke(GRAPH_STROKE);
					// create x and y axes
					g2.drawLine(BORDER_GAP, img.getHeight() - BORDER_GAP, BORDER_GAP, BORDER_GAP);
					g2.drawLine(BORDER_GAP, img.getHeight() - BORDER_GAP, img.getWidth() - BORDER_GAP, img.getHeight() - BORDER_GAP);


					q.editOriginal("Graphing points").queue();
					drawGraph(values.stream().map(Math::log10).map(this::remap).toList(), g2, BORDER_GAP, GRAPH_STROKE, height, xScale);

					int previous = 0;
					boolean running = false;
					HashMap<Double, Integer> vals = new HashMap<>();

					q.editOriginal("Graphing flares").queue();
					for (int i = 0; i < result.signals().size(); i++) {
						Integer val = result.signals().get(i);
						if (running) {
							vals.put(values.get(i), i);
						}
						if (val == 1 && previous == 0) {
							running = true;
						} else if ((((val == 0) && (previous == 1)) || ((val == 1) && (i == (result.signals().size() - 1)))) && !vals.isEmpty()) {
							running = false;
							double max = max(vals.keySet());
							double logmax = Math.log10(max);
							String flareClass;

							double floor;
							if (logmax >= -4) {
								flareClass = "X";
								floor = -4;
							} else if (logmax >= -5) {
								flareClass = "M";
								floor = -5;
							} else if (logmax >= -6) {
								flareClass = "C";
								floor = -6;
							} else if (logmax >= -7) {
								flareClass = "B";
								floor = -7;
							} else {
								flareClass = "A";
								floor = -8;
							}

							if (logmax >= flareThreshold) {
								flareClass = flareClass + Double.toString(max * Math.pow(10, floor)).substring(0, "0.00".length());
								drawFlareLine(vals.get(max), flareClass, g2, img, BORDER_GAP, xScale);
								vals.clear();
							}
						}
						previous = val;
					}

					q.editOriginal("Finalising").queue();
					if (DiscordBot.dev) {
						if (!new File("./vcr.ttf").exists())
							Files.copy(URI.create("https://cdn.itoncek.space/fonts/VCR_OSD_MONO-Regular.ttf").toURL().openStream(), new File("./vcr.ttf").toPath(), StandardCopyOption.REPLACE_EXISTING);
						g2.setFont(Font.createFont(Font.TRUETYPE_FONT, new File("./vcr.ttf")).deriveFont(120f));
						g2.setColor(new Color(255, 0, 0, 70));
						g2.drawString("DEV", img.getWidth() - 20 - g2.getFontMetrics().stringWidth("DEV"), g2.getFontMetrics().getHeight() + 20);
					}

					g2.dispose();
					ImageIO.write(img, "png", temp);
					FileUpload fu = FileUpload.fromData(temp);
					q.editOriginalAttachments(fu).queue();
					q.editOriginal("").queue();

					fu.close();
					delete.add(temp);
					previousOffset = 0;
					previousLength = 0;
					previousX = 0;
				} catch (Exception e) {
					q.editOriginal("Something weird had happened :shrug:").queue(m -> {
						try {
							File temp = File.createTempFile("solarbot", ".log");
							PrintStream ps = new PrintStream(temp, Charset.defaultCharset());
							e.printStackTrace(ps);

							q.editOriginalAttachments(FileUpload.fromData(temp)).queue();
							delete.add(temp);
						} catch (IOException ex) {
							throw new RuntimeException(ex);
						}
					});
					e.printStackTrace();
				}
			});
		}
	}

	private void drawFlareLine(int i, String flareClass, Graphics2D g2, BufferedImage img, int BORDER_GAP, double xScale) throws IOException, FontFormatException {
		if (!new File("./vcr.ttf").exists())
			Files.copy(URI.create("https://cdn.itoncek.space/fonts/VCR_OSD_MONO-Regular.ttf").toURL().openStream(), new File("./vcr.ttf").toPath(), StandardCopyOption.REPLACE_EXISTING);
		g2.setFont(Font.createFont(Font.TRUETYPE_FONT, new File("./vcr.ttf")).deriveFont(80F));

		int x = (int) (i * xScale + BORDER_GAP);
		if (previousX + previousLength > x) {
			previousOffset += g2.getFontMetrics().getHeight() + 20;
		} else {
			previousOffset = 0;
		}

		g2.setColor(new Color(255, 255, 255, 80));
		g2.setStroke(new BasicStroke(16f));
		g2.drawLine(x, BORDER_GAP + 8, x, img.getHeight() - BORDER_GAP - 8 - previousOffset);


		g2.drawString(flareClass, x + 20, img.getHeight() - BORDER_GAP - 15 - previousOffset);
		previousX = x;
		previousLength = g2.getFontMetrics().stringWidth(flareClass);
	}

	public Double remap(double x) {
		return (x + 8) / 6;
	}

	private double unremap(Double v) {
		return (v * 6) - 8;
	}


	private void drawGraph(List<Double> datapoints, Graphics2D g2, int BORDER_GAP, Stroke GRAPH_STROKE, int imgheight, double xScale) {
		ArrayList<Point> graphPoints = new ArrayList<>(datapoints.size());
		ArrayList<Color> colorPoints = new ArrayList<>(datapoints.size());

		for (int i = 0; i < datapoints.size(); i++) {
			if (Double.isInfinite(datapoints.get(i))) {
				graphPoints.add(null);
				colorPoints.add(Color.GRAY);
			} else {
				int x1 = (int) (i * xScale + BORDER_GAP);
				int y1 = (int) (imgheight - (imgheight * datapoints.get(i))) + BORDER_GAP;
				graphPoints.add(new Point(x1, y1));
				colorPoints.add(determineColor(datapoints.get(i)));
			}
		}

		g2.setStroke(GRAPH_STROKE);
		for (int i = 0; i < graphPoints.size() - 1; i++) {
			g2.setColor(colorPoints.get(i + 1));
			Point i0 = graphPoints.get(i);
			Point i1 = graphPoints.get(i + 1);
			if (i0 != null && i1 != null) {
				int x1 = i0.x;
				int y1 = i0.y;
				int x2 = i1.x;
				int y2 = i1.y;
				g2.drawLine(x1, y1, x2, y2);
			}
		}
	}

	private Color determineColor(Double v) {
		double x = unremap(v);
		if (x >= -2) return new Color(0, 0, 0);
		else if (x >= -3) return new Color(87, 0, 0);
		else if (x >= -4) return new Color(255, 0, 0);
		else if (v >= -5) return new Color(255, 140, 0);
		else if (v >= -6) return new Color(255, 225, 0);
		else if (v >= -7) return new Color(79, 218, 0);
		else return new Color(26, 62, 0);
	}


	private void drawClassLine(double strenght, String classs, Graphics2D g2, BufferedImage img, int BORDER_GAP, int imgheight) throws IOException, FontFormatException {
		g2.setColor(new Color(255, 255, 255, 80));
		g2.setStroke(new BasicStroke(8f));

		int y = (int) (imgheight - (imgheight * remap(strenght))) + BORDER_GAP;
		g2.drawLine(BORDER_GAP, y, img.getWidth() - BORDER_GAP, y);


		g2.setColor(new Color(255, 255, 255, 255));
		if (!new File("./vcr.ttf").exists())
			Files.copy(URI.create("https://cdn.itoncek.space/fonts/VCR_OSD_MONO-Regular.ttf").toURL().openStream(), new File("./vcr.ttf").toPath(), StandardCopyOption.REPLACE_EXISTING);
		g2.setFont(Font.createFont(Font.TRUETYPE_FONT, new File("./vcr.ttf")).deriveFont(80f));

		int x1 = img.getWidth() - BORDER_GAP - g2.getFontMetrics().stringWidth(classs);
		int y1 = y + g2.getFontMetrics().getHeight() + 10;
		g2.drawString(classs, x1, y1);
	}
}