package space.itoncek.nlcmonitor.timers;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.itoncek.nlcmonitor.BlueSkyRuntime;
import space.itoncek.nlcmonitor.DiscordTimedExecutor;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class LunarXVTimer implements DiscordTimedExecutor {
	private static final Logger log = LoggerFactory.getLogger(LunarXVTimer.class);
	public static final ZoneId UT_ZONE = ZoneId.of("Z");
	static DateTimeFormatter horizonsDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm:ss");
	private boolean enabled = autostartWithoutDev();

	@Override
	public boolean autostartWithoutDev() {
		return true;
	}

	@Override
	public String id() {
		return "xv-monitor";
	}

	@Override
	public LocalTime execTime() {
		return LocalTime.of(12, 0, 0);
	}

	@Override
	public void execute(JDA jda, BlueSkyRuntime bsky, TextChannel c) throws IOException {
		//LocalDateTime execDateTime = LocalDateTime.of(2024,11,8,12,0,0);
		LocalDateTime execDateTime = LocalDateTime.now();
		Line fitline = bestFitLine(parse(filter(getHorizons(execDateTime))));

		boolean rising = fitline.a > 0;

		if(rising) {
			double root = -fitline.b / fitline.a;
			ZonedDateTime risingTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(Math.round(root)), UT_ZONE);
			if(risingTime.isAfter(execDateTime.atZone(UT_ZONE)) && risingTime.isBefore(execDateTime.plusDays(1).atZone(UT_ZONE))) {
				sendAlert(jda,bsky,c,execDateTime,risingTime);
			}
		}
	}

	@Override
	public CommandData getCommand() {
		return Commands.slash("lunarxv", "Checks if there is Lunar X & V visible in the next 24h.");
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	private static void sendAlert(JDA jda, BlueSkyRuntime bsky, TextChannel c, LocalDateTime execDateTime, ZonedDateTime rising) {
		long snowflake = 1120470471993983056L;
		if(c == null) {
			c = jda.getChannelById(TextChannel.class, snowflake);
		}
		if(c == null) {
			log.error("Unable to find channel {}",snowflake);
			return;
		}
		MessageEmbed embed = new EmbedBuilder()
				.setAuthor(jda.getSelfUser().getName(),"https://itoncek.space",jda.getSelfUser().getAvatarUrl())
				.setColor(Color.GREEN)
				.setTitle("Lunar X & V will be visible tonight!")
				.setDescription("At <t:" + rising.toEpochSecond() +":T> (<t:" + rising.toEpochSecond() +":R>), the sun will rise above Ukert crater, Purbach crater and La Caille crater sites. This creates an opportunity for a V (near Ukert) and an X (between Purbach and La Caille) to form.")
				.setFooter("Generated using NLCMonitor")
				.setTimestamp(execDateTime)
				.build();
		c.sendMessageEmbeds(embed).queue();
		if(bsky != null) {
			try {
				bsky.postBluesky("Lunar X & V will be visible tonight!\n\nAt " +rising.withZoneSameInstant(ZoneId.systemDefault()) + ", the sun will rise above Ukert, Purbach and La Caille craters, showing apparent X & V at the terminator!");
			} catch (IOException e) {
				log.error("BlueSky error", e);
			}
		}
	}

	private static Line bestFitLine(TreeSet<HorizonsData> data) {
		int n = 0;
		double[] x = new double[data.size()];
		double[] y = new double[data.size()];

		// first pass: read in data, compute xbar and ybar
		double sumx = 0.0f, sumy = 0.0f;
		for (HorizonsData datapoint : data) {
			x[n] = datapoint.date.toEpochSecond();
			y[n] = datapoint.elev;
			sumx  += x[n];
			sumy  += y[n];
			n++;
		}

		double xbar = sumx / n;
		double ybar = sumy / n;

		// second pass: compute summary statistics
		double xxbar = 0.0f, xybar = 0.0f;
		for (int i = 0; i < n; i++) {
			xxbar += (x[i] - xbar) * (x[i] - xbar);
			xybar += (x[i] - xbar) * (y[i] - ybar);
		}

		double a = xybar / xxbar;
		double b = ybar - a * xbar;

		return new Line(a,b);
	}

	private static TreeSet<HorizonsData> parse(String filteredResponse) {
		Scanner sc = new Scanner(filteredResponse);
		TreeSet<HorizonsData> output = new TreeSet<>();
		while (sc.hasNextLine()) {
			String line = sc.nextLine();
			String[] split = Arrays.stream(line.split("\\r?,")).map(String::trim).toList().toArray(new String[0]);
			if(split.length != 5) {
				log.warn("Following line is invalid ({} parts instead of 5):{}", split.length, line);
				continue;
			}
			ZonedDateTime dateTime = ZonedDateTime.of(LocalDateTime.parse(split[0],horizonsDateTimeFormatter), UT_ZONE);
			output.add(new HorizonsData(dateTime,Double.parseDouble(split[3]),Double.parseDouble(split[4])));
		}
		return output;
	}

	private static String filter(String horizonsResponse) {
		StringJoiner sb = new StringJoiner("\n");
		Scanner sc = new Scanner(horizonsResponse);
		boolean writing = false;
		while (sc.hasNextLine()) {
			String line = sc.nextLine();
			if(!writing) {
				if(line.startsWith("$$SOE")) {
					writing = true;
				}
			} else {
				if(line.startsWith("$$EOE")) {
					break;
				} else {
					sb.add(line);
				}
			}
		}
		return sb.toString();
	}

	private static String getHorizons(LocalDateTime execDateTime) throws IOException {
		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			HttpPost uploadFile = new HttpPost("https://ssd.jpl.nasa.gov/api/horizons_file.api");
			MultipartEntityBuilder builder = MultipartEntityBuilder.create();
			builder.addTextBody("format", "text", ContentType.TEXT_PLAIN);
			builder.addTextBody("input", generateHorizonsRequest(execDateTime));

			HttpEntity multipart = builder.build();
			uploadFile.setEntity(multipart);
			return httpClient.execute(uploadFile, res -> EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8));
		}
	}

	private static String generateHorizonsRequest(LocalDateTime execDateTime) {
		return """
				!$$SOF
				MAKE_EPHEM=YES
				COMMAND=10
				EPHEM_TYPE=OBSERVER
				CENTER='coord@301'
				COORD_TYPE=GEODETIC
				SITE_COORD='1.57801,8.1587,0.83778'
				START_TIME='%s'
				STOP_TIME='%s'
				STEP_SIZE='1 HOURS'
				QUANTITIES='4'
				REF_SYSTEM='ICRF'
				CAL_FORMAT='CAL'
				CAL_TYPE='M'
				TIME_DIGITS='SECONDS'
				ANG_FORMAT='HMS'
				APPARENT='AIRLESS'
				RANGE_UNITS='AU'
				SUPPRESS_RANGE_RATE='NO'
				SKIP_DAYLT='NO'
				SOLAR_ELONG='0,180'
				EXTRA_PREC='NO'
				R_T_S_ONLY='NO'
				CSV_FORMAT='YES'
				OBJ_DATA='NO'""".formatted(execDateTime.format(horizonsDateTimeFormatter),execDateTime.plusDays(1).format(horizonsDateTimeFormatter));
	}

	private record HorizonsData(ZonedDateTime date, double azi, double elev) implements Comparable<HorizonsData> {
		@Override
		public int compareTo(@NotNull LunarXVTimer.HorizonsData o) {
			return this.date.compareTo(o.date);
		}
	}

	private record Line(double a, double b) {
	}
}