package space.itoncek.nlcmonitor.monitors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SolarFlareDatabaseManager {
	private static final Logger log = LoggerFactory.getLogger(SolarFlareDatabaseManager.class);
	private static Connection con;

	public SolarFlareDatabaseManager() throws SQLException {
		if (!new File("./sfd.db").exists()) {
			try (Connection conn = DriverManager.getConnection("jdbc:sqlite:sfd.db")) {
				if (conn != null) {
					var meta = conn.getMetaData();
					log.info("The driver name is {}", meta.getDriverName());
					log.info("A new database has been created.");
					migrate(conn);
				}
			} catch (SQLException e) {
				log.error("Unable to access ./sfd.db", e);
			}
		}
		con = DriverManager.getConnection("jdbc:sqlite:sfd.db");
	}

	public void saveFlare(SolarFlare flare) {
		try (Statement stmt = con.createStatement()) {
			ResultSet rs = stmt.executeQuery("SELECT * FROM \"flares\" WHERE \"start\" = '2024-08-05 22:37:03'\n LIMIT 1;");
			if (!rs.next()) {
				stmt.executeUpdate("INSERT INTO \"flares\" (\"start\", \"max\", \"end\", \"flareStrength\") VALUES ('%s', '%s', '%s', '%s');".formatted(fdate(flare.start()), fdate(flare.max()), fdate(flare.end()), flare.flareStrength()));
			} else {
				if (!rs.getString("flareStrength").equals(flare.flareStrength())) {
					stmt.executeUpdate("UPDATE \"flares\" SET \"max\"='%s', \"end\"='%s', \"flareStrength\"='%s' WHERE  \"start\"='%s';".formatted(fdate(flare.max()), fdate(flare.end()), flare.flareStrength(), fdate(flare.start())));
				}
			}
		} catch (SQLException e) {
			log.error("Unable to save flare to the DB!", e);
		}
	}

	public SolarFlare getFlare(LocalDateTime start) {
		try (Statement stmt = con.createStatement()) {
			ResultSet rs = stmt.executeQuery("SELECT * FROM \"flares\" WHERE \"start\" = '2024-08-05 22:37:03' LIMIT 1;");
			if (rs.next()) {
				return new SolarFlare(LocalDateTime.parse(rs.getString("start"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
						LocalDateTime.parse(rs.getString("max"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
						LocalDateTime.parse(rs.getString("end"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
						rs.getString("flareStrength"));
			} else return null;
		} catch (SQLException e) {
			log.error("Unable to load flare from the DB!", e);
			return null;
		}
	}

	public DiscordStateAction getDiscordStateAction(LocalDateTime start) {
		try (Statement stmt = con.createStatement()) {
			try (ResultSet rs = stmt.executeQuery("SELECT * FROM \"discord_state\" WHERE \"start\" = '%s' LIMIT 1;".formatted(fdate(start)))) {
				if (rs.next()) {
					DiscordState ds = new DiscordState(unfdate(rs.getString("start")), rs.getLong("discordSnowflake"), rs.getString("flareClass"));
					return new DiscordStateAction(ds, false, checkMesageUpdate(ds), checkTooOld(unfdate(rs.getString("start"))));
				} else
					return new DiscordStateAction(null, true, false, checkTooOld(unfdate(rs.getString("creationDate"))));
			}
		} catch (SQLException e) {
			log.error("Unable to load flare from the DB!", e);
			return null;
		}
	}

	private boolean checkMesageUpdate(DiscordState ds) {
		SolarFlare flare = getFlare(ds.start_FK());
		return !flare.flareStrength().equals(ds.flareClass());
	}

	private String fdate(LocalDateTime date) {
		return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
	}

	private LocalDateTime unfdate(String start) {
		return LocalDateTime.parse(start, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
	}

	private boolean checkTooOld(LocalDateTime cdate) {
		return cdate.plusDays(3).isAfter(LocalDateTime.now());
	}

	public void updateDiscordState(DiscordState state) {
		try (Statement stmt = con.createStatement()) {
			ResultSet rs = stmt.executeQuery("SELECT * FROM \"discord_state\" WHERE \"start\" = '%s'\n LIMIT 1;".formatted(fdate(state.start_FK)));
			if (!rs.next()) {
				stmt.executeUpdate("INSERT INTO \"discord_state\" (\"start\", \"discordSnowflake\", \"flareClass\") VALUES ('%s', %d, '%s');".formatted(fdate(state.start_FK), state.snowflake, state.flareClass));
			} else {
				if (!rs.getString("flareClass").equals(state.flareClass)) {
					//stmt.executeUpdate("UPDATE \"flares\" SET \"max\"='%s', \"end\"='%s', \"flareStrength\"='%s' WHERE  \"start\"='%s';".formatted(fdate(flare.max()), fdate(flare.end()), flare.flareStrength(), fdate(flare.start())));
				}
			}
		} catch (SQLException e) {
			log.error("Unable to update discord state in the DB!", e);
		}
	}

	private void migrate(Connection conn) throws SQLException {
		Statement stmt = conn.createStatement();

		//CREATE TABLE IF NOT EXISTS "discord_state" ("start" DATETIME NOT NULL,"discordSnowflake" BIGINT NOT NULL,"flareClass" VARCHAR(6) NOT NULL,PRIMARY KEY ("start"),CONSTRAINT "start" FOREIGN KEY ("start") REFERENCES "flares" ("start") ON UPDATE CASCADE ON DELETE CASCADE);
		//CREATE TABLE IF NOT EXISTS "flares" ("start" DATETIME NOT NULL,"max" DATETIME NOT NULL,"end" DATETIME NOT NULL,"flareStrength" VARCHAR(6) NOT NULL,PRIMARY KEY ("start"));
		//CREATE TABLE IF NOT EXISTS "flares_old" ("start" DATETIME NOT NULL,"max" DATETIME NOT NULL,"end" DATETIME NOT NULL,"flareStrength" VARCHAR(6) NOT NULL,PRIMARY KEY ("start"));
		//CREATE TABLE IF NOT EXISTS "discord_state_old" ("start" DATETIME NOT NULL,"discordSnowflake" BIGINT NOT NULL,"flareClass" VARCHAR(6) NOT NULL,PRIMARY KEY ("start"),CONSTRAINT "start" FOREIGN KEY ("start") REFERENCES "flares_old" ("start") ON UPDATE CASCADE ON DELETE CASCADE);
		stmt.executeUpdate("CREATE TABLE IF NOT EXISTS \"flares\" (\"start\" DATETIME NOT NULL,\"max\" DATETIME NOT NULL,\"end\" DATETIME NOT NULL,\"flareStrength\" VARCHAR(6) NOT NULL,PRIMARY KEY (\"start\"));");
		stmt.executeUpdate("CREATE TABLE IF NOT EXISTS \"discord_state\" (\"start\" DATETIME NOT NULL,\"discordSnowflake\" BIGINT NOT NULL,\"flareClass\" VARCHAR(6) NOT NULL,PRIMARY KEY (\"start\"),CONSTRAINT \"start\" FOREIGN KEY (\"start\") REFERENCES \"flares\" (\"start\") ON UPDATE CASCADE ON DELETE CASCADE);");
		stmt.executeUpdate("CREATE TABLE IF NOT EXISTS \"flares_old\" (\"start\" DATETIME NOT NULL,\"max\" DATETIME NOT NULL,\"end\" DATETIME NOT NULL,\"flareStrength\" VARCHAR(6) NOT NULL,PRIMARY KEY (\"start\"));");
		stmt.executeUpdate("CREATE TABLE IF NOT EXISTS \"discord_state_old\" (\"start\" DATETIME NOT NULL,\"discordSnowflake\" BIGINT NOT NULL,\"flareClass\" VARCHAR(6) NOT NULL,PRIMARY KEY (\"start\"),CONSTRAINT \"start\" FOREIGN KEY (\"start\") REFERENCES \"flares_old\" (\"start\") ON UPDATE CASCADE ON DELETE CASCADE);");

		stmt.close();
	}

	private void maintenance() {
		if (false) {
			try (Statement stmt = con.createStatement()) {
				try (ResultSet rs = stmt.executeQuery("SELECT * FROM \"flares\";")) {
					while (rs.next()) {
						if (checkTooOld(unfdate(rs.getString("start")))) {
							stmt.executeUpdate("INSERT INTO \"flares_old\" (\"start\", \"max\", \"end\", \"flareStrength\") VALUES ('%s', '%s', '%s', '%s');".formatted(rs.getString("start"), rs.getString("max"), rs.getString("end"), rs.getString("flareStrength")));
							stmt.executeUpdate("DELETE FROM \"flares\" WHERE \"start\"='%s';".formatted(rs.getString("start")));
						}
					}
				}

				try (ResultSet rs = stmt.executeQuery("SELECT * FROM \"discord_state\";")) {
					while (rs.next()) {
						if (checkTooOld(unfdate(rs.getString("start")))) {
							stmt.executeUpdate("INSERT INTO \"discord_state_old\" (\"start\", \"discordSnowflake\", \"flareClass\") VALUES ('%s', %d, '%s');".formatted(rs.getString("start"), rs.getLong("discordSnowflake"), rs.getString("flareClass")));
							stmt.executeUpdate("DELETE FROM \"discord_state\" WHERE \"start\"='%s';".formatted(rs.getString("start")));
						}
					}
				}
			} catch (SQLException e) {
				log.error("Unable to execute maintenance on the DB!", e);
			}
		}
	}

	public record DiscordState(LocalDateTime start_FK, long snowflake, String flareClass) {
	}

	public record SolarFlare(LocalDateTime start, LocalDateTime max, LocalDateTime end, String flareStrength) {
	}

	public record DiscordStateAction(DiscordState state, boolean requiresMessageCreation, boolean requiresMessageUpdate,
									 boolean tooOld) {
	}
}
