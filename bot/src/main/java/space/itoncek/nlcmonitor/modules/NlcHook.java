package space.itoncek.nlcmonitor.modules;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.FileUpload;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.jetbrains.annotations.Nullable;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import space.itoncek.nlcmonitor.DiscordHook;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;

public class NlcHook extends ListenerAdapter implements DiscordHook {
	ArrayList<File> delete = new ArrayList<>();
	private JDA jda;
	private boolean enabled;

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
		return "nlcHook";
	}

	@Override
	public boolean autostartWithoutDev() {
		return true;
	}

	@Override
	public CommandData getCommand() {
		return Commands.slash("nlc", "Checks for NLC on the OSWIN Radar").addOption(OptionType.ATTACHMENT, "image", "Add OSWIN image from another date than now", false);
	}

	@Override
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
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
			} else event.deferReply().queue(m -> {
				try {
					m.editOriginal("Downloading").queue();
					long start = System.currentTimeMillis();
					File f = File.createTempFile("nlcmonitor_", ".png");
					BufferedImage bi;
					boolean old = false;
					try {
						Message.Attachment attachment = event.getInteraction().getOption("image").getAsAttachment();
						if (attachment.isImage()) {
							bi = ImageIO.read(URI.create(attachment.getUrl()).toURL());
							old = true;
						} else {
							throw new Exception("Not an image");
						}
					} catch (Exception ex) {
						bi = ImageIO.read(URI.create("https://www.iap-kborn.de/fileadmin/user_upload/MAIN-abteilung/radar/Radars/OswinVHF/Plots/OSWIN_Mesosphere_4hour.png").toURL());
					}

					if(old) {
						Graphics2D g2 = bi.createGraphics();

						g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
						g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

						g2.setColor(Color.RED);
						if(!new File("./vcr.ttf").exists()) Files.copy(URI.create("https://cdn.itoncek.space/fonts/VCR_OSD_MONO-Regular.ttf").toURL().openStream(),new File("./vcr.ttf").toPath(), StandardCopyOption.REPLACE_EXISTING);
						g2.setFont(Font.createFont(Font.TRUETYPE_FONT, new File("./vcr.ttf")).deriveFont(163F));
						g2.drawString("OLD DATA!", 165, 1294);

						g2.dispose();
					}


					ImageIO.write(bi, "png", f);
					FileUpload fu = FileUpload.fromData(f);
					m.editOriginalAttachments(fu).queue();

					boolean found = false;
					boolean foundblue = false;
					int endx = 0;
					int idx = 0;
					while (!found) {
						Color c = new Color(bi.getRGB(idx, 166));
						if (isAlmostWhite(c)) {
							if (foundblue) {
								found = true;
								endx = idx - 1;
							}
						} else {
							if (!foundblue) {
								foundblue = true;
							}
						}
						idx++;
					}

					m.editOriginal("Parsing").queue();
					float[][] image = new float[1][2116];
					for (int x = 0; x < 46; x++) {
						for (int y = 0; y < 46; y++) {
							Color c = new Color(bi.getRGB((endx - 46) + x, (y * 17) + 119));
							float[] hsv = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
							image[0][(y * 46) + x] = 1 - hsv[0];
						}
					}
					double error, noNLC, NLC;

					File cache = hasResultCached(new File("./cache/"), image);
					if (cache != null) {
						m.editOriginal("Loading result from cache").queue();
						double[] results = readCache(cache);
						error = results[0];
						noNLC = results[1];
						NLC = results[2];
					} else {
						m.editOriginal("Loading ML model").queue();

						MultiLayerNetwork mln = MultiLayerNetwork.load(new File("model.ai"), false);

						m.editOriginal("Analysing").queue();

						INDArray predict = mln.output(Nd4j.create(image));

						error = predict.getDouble(0);
						noNLC = predict.getDouble(1);
						NLC = predict.getDouble(2);

						predict.close();
						mln.close();
						cacheResult(image, new File("./cache/"), new double[]{error, noNLC, NLC});
					}
					double supermax = Math.max(error, Math.max(noNLC, NLC));

					String prediction = old?"[OLD DATA] " :"";
					Color color;

					if (noNLC == supermax) {
						prediction += "I don't see any NLC";
						color = Color.ORANGE;
					} else if (NLC == supermax) {
						prediction += "GO OUT! THERE MIGHT BE NLCs VISIBLE!";
						color = Color.GREEN;
					} else {
						prediction += "There was an error with the input data!";
						color = Color.RED;
					}

					MessageEmbed e = new EmbedBuilder()
							.setAuthor(jda.getSelfUser().getName(), jda.getSelfUser().getAvatarUrl(), jda.getSelfUser().getAvatarUrl())
							.setTimestamp(ZonedDateTime.now())
							.setTitle(prediction)
							.setDescription("(took me " + (System.currentTimeMillis() - start) + "ms to figure this out)")
							.setFooter("Generated using NLCMonitor")
							.setColor(color)
							.build();

					//reset
					m.editOriginal("Finished predicting!").queue();
					m.editOriginalEmbeds(e).queue();

					fu.close();
					delete.add(f);
					bi.flush();
				} catch (Exception e) {
					m.editOriginal("Something weird had happened :shrug:").queue(mes -> {
						try {
							File temp = File.createTempFile("solarbot", ".log");
							PrintStream ps = new PrintStream(temp, Charset.defaultCharset());
							e.printStackTrace(ps);
							e.printStackTrace();
							delete.add(temp);
							m.editOriginalAttachments(FileUpload.fromData(temp)).queue();
						} catch (IOException ex) {
							throw new RuntimeException(ex);
						}
					});
				}
			});
		}
		super.onSlashCommandInteraction(event);
	}

	private double[] readCache(File cache) throws IOException {
		FileInputStream fis = new FileInputStream(cache);
		ObjectInputStream ois = new ObjectInputStream(fis);
		return new double[]{ois.readDouble(), ois.readDouble(), ois.readDouble()};
	}

	private @Nullable File hasResultCached(File file, float[][] img) {
		if (!file.exists()) file.mkdirs();

		int i = Arrays.deepHashCode(img);

		File target = new File(file, i + ".predict");
		if (target.exists()) return target;
		else return null;
	}

	private void cacheResult(float[][] img, File file, double[] results) throws IOException {
		if (!file.exists()) file.mkdirs();

		int i = Arrays.deepHashCode(img);

		File target = new File(file, i + ".predict");
		FileOutputStream fos = new FileOutputStream(target);
		ObjectOutputStream oos = new ObjectOutputStream(fos);
		oos.writeDouble(results[0]);
		oos.writeDouble(results[1]);
		oos.writeDouble(results[2]);
		oos.close();
		fos.close();
	}

	private boolean isAlmostWhite(Color c) {
		return (c.getRed() > 250) && c.getGreen() > 250 && c.getBlue() > 250;
	}
}
