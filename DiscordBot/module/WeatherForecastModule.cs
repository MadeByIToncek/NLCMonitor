using System.Diagnostics.CodeAnalysis;
using System.Text.Json;
using Discord;
using Discord.WebSocket;
using DiscordBot.interfaces;
using DiscordBot.utils;
using SixLabors.ImageSharp.Formats.Png;

namespace DiscordBot.module;

public class WeatherForecastModule : IModule {
    private const string GenericWeatherRequestUri =
        "https://api.open-meteo.com/v1/forecast?latitude={0}&longitude={1}&hourly=temperature_2m,precipitation_probability,rain,weather_code,cloud_cover,rain,snowfall,showers,dew_point_2m,apparent_temperature,wind_speed_10m,wind_direction_10m,cloud_cover_low,cloud_cover_high,cloud_cover_mid,visibility,wind_gusts_10m,precipitation&timezone=auto&forecast_days=3";

    public string Id() {
        return "weather-forecast";
    }

    public bool InstallGlobally() {
        return false;
    }

    public Task SetupListeners(DiscordSocketClient client) {
        client.SelectMenuExecuted += async c => {
            await c.DeferAsync();
            string value = c.Data.Value;
            Location l = Location.Constants.Find(x => x.Name == value);
            await ProcessDropdown(l, c);
        };
        return Task.CompletedTask;
    }

    public SlashCommandProperties BuildCommand() {
        return new SlashCommandBuilder()
            .WithName(Id())
            .WithDescription("Předpověď počasí pro danou lokalitu")
            .Build();
    }

    public async Task Execute(SocketSlashCommand cmd) {
        SelectMenuBuilder? menuBuilder = new SelectMenuBuilder()
            .WithPlaceholder("Select an option")
            .WithCustomId("menu-1")
            .WithMinValues(1)
            .WithMaxValues(1);

        Location.Constants.ForEach(x => { menuBuilder.AddOption(x.Name, x.Name); });

        ComponentBuilder? builder = new ComponentBuilder()
            .WithSelectMenu(menuBuilder);

        await cmd.RespondAsync("Vyberte lokalitu", components: builder.Build() /*, ephemeral: true*/);
    }

    public async Task ProcessDropdown(Location location, SocketMessageComponent cmp) {
        await cmp.DeleteOriginalResponseAsync();
        using HttpClient client = new();
        string formattedWeatherRequestUri =
            string.Format(GenericWeatherRequestUri, location.Lat, location.Lon);
        await using var stream = await client.GetStreamAsync(formattedWeatherRequestUri);

        WeatherForecast? weatherForecast = JsonSerializer.Deserialize<WeatherForecast>(stream);
        if (weatherForecast == null) {
            throw new NullReferenceException("Unable to parse Weather data!");
        }

        List<DataPoint> data = weatherForecast.GenerateDataPointList();
        using WeatherCalculator calculator = new((3840, 2160));
        await calculator.GenerateGraphs(data);

        using TemporaryFile temp = new(".png");
        using TemporaryFile temp2 = new(".png");
        if (temp.FilePath == null || temp2.FilePath == null) {
            throw new IOException("Unable to write to a temporary file!");
        }

        await using (Stream stream2 = File.OpenWrite(temp.FilePath)) {
            await calculator.TemperatureGraph.SaveAsync(stream2, new PngEncoder());
        }

        await using (Stream stream2 = File.OpenWrite(temp2.FilePath)) {
            await calculator.RainGraph.SaveAsync(stream2, new PngEncoder());
        }

        Embed[] e = [
            new EmbedBuilder()
                .WithAuthor(new EmbedAuthorBuilder()
                    .WithName(Program.DiscordClient.CurrentUser.Username)
                    .WithUrl("https://itoncek.space/")
                    .WithIconUrl(Program.DiscordClient.CurrentUser.GetAvatarUrl(ImageFormat.Png, 256)))
                .WithTitle("Předpověď počasí")
                .WithDescription("Lokalita: Liberec\nVygenerováno z dat [Open-Meteo](https://open-meteo.com/)")
                .WithColor(Color.DarkGrey)
                .Build(),
            new EmbedBuilder()
                .WithAuthor(new EmbedAuthorBuilder()
                    .WithName(Program.DiscordClient.CurrentUser.Username)
                    .WithUrl("https://itoncek.space/")
                    .WithIconUrl(Program.DiscordClient.CurrentUser.GetAvatarUrl(ImageFormat.Png, 256)))
                .WithFooter(b => { b.WithText("Vygenerováno z dat open-meteo.com"); })
                .WithTimestamp(DateTimeOffset.Now)
                .WithColor(Color.Red)
                .WithTitle("Teplota")
                .WithDescription("""
                                 :red_circle: - Teplota 
                                 :green_circle: - Pocitová teplota
                                 :blue_circle: - Rosný bod

                                 """)
                .WithImageUrl($"attachment://{Path.GetFileName(temp.FilePath)}")
                .Build(),
            new EmbedBuilder()
                .WithAuthor(new EmbedAuthorBuilder()
                    .WithName(Program.DiscordClient.CurrentUser.Username)
                    .WithUrl("https://itoncek.space/")
                    .WithIconUrl(Program.DiscordClient.CurrentUser.GetAvatarUrl(ImageFormat.Png, 256)))
                .WithDescription("""
                                 :blue_circle: - Srážky
                                 :white_circle: - Pravděpodobnost

                                 """)
                .WithImageUrl($"attachment://{Path.GetFileName(temp2.FilePath)}")
                .WithFooter(b => { b.WithText("Vygenerováno z dat open-meteo.com"); })
                .WithTimestamp(DateTimeOffset.Now)
                .WithColor(Color.Blue)
                .Build()
        ];
        await cmp.FollowupWithFilesAsync(attachments: [
                new FileAttachment(temp.FilePath),
                new FileAttachment(temp2.FilePath)
            ],
            embeds: e);
    }
}

[SuppressMessage("ReSharper", "ClassNeverInstantiated.Global")]
[SuppressMessage("ReSharper", "InconsistentNaming")]
internal class WeatherForecast {
    public float latitude { get; set; }
    public float longitude { get; set; }
    public double generationtime_ms { get; set; }
    public int utc_offset_seconds { get; set; }
    public string? timezone { get; set; }
    public string? timezone_abbreviation { get; set; }
    public float elevation { get; set; }
    public HourlyForecast hourly { get; set; }

    public List<DataPoint> GenerateDataPointList() {
        List<DataPoint> output = [];

        for (int i = 0; i < hourly.time.Count; i++) {
            output.Add(new DataPoint {
                Time = hourly.time[i],
                WeatherCode = hourly.weather_code[i],
                /* Temperature */
                Temperature2M = hourly.temperature_2m[i],
                DewPoint2M = hourly.dew_point_2m[i],
                ApparentTemperature = hourly.apparent_temperature[i],
                /* Rain */
                PrecipitationProbability = hourly.precipitation_probability[i],
                Precipitation = hourly.precipitation[i],
                Rain = hourly.rain[i],
                Snowfall = hourly.snowfall[i],
                Showers = hourly.showers[i],
                /* Wind */
                WindSpeed10M = hourly.wind_speed_10m[i],
                WindDirection10M = hourly.wind_direction_10m[i],
                WindGusts10M = hourly.wind_gusts_10m[i],
                /* Cloud */
                CloudCover = hourly.cloud_cover[i],
                CloudCoverHigh = hourly.cloud_cover_high[i],
                CloudCoverMid = hourly.cloud_cover_mid[i],
                CloudCoverLow = hourly.cloud_cover_low[i],
                Visibility = hourly.visibility[i]
            });
        }
        
        return output;
    }
}

[SuppressMessage("ReSharper", "ClassNeverInstantiated.Global")]
[SuppressMessage("ReSharper", "InconsistentNaming")]
[SuppressMessage("ReSharper", "CollectionNeverUpdated.Global")]
internal class HourlyForecast {
    public List<DateTime> time { get; set; } = [];
    public List<float> temperature_2m { get; set; } = [];
    public List<int> precipitation_probability { get; set; } = [];
    public List<float> precipitation { get; set; } = [];
    public List<float> rain { get; set; } = [];
    public List<float> showers { get; set; } = [];
    public List<float> snowfall { get; set; } = [];
    public List<float> weather_code { get; set; } = [];
    public List<float> cloud_cover { get; set; } = [];
    public List<float> dew_point_2m { get; set; } = [];
    public List<float> apparent_temperature { get; set; } = [];
    public List<float> wind_speed_10m { get; set; } = [];
    public List<ushort> wind_direction_10m { get; set; } = [];
    public List<byte> cloud_cover_low { get; set; } = [];
    public List<byte> cloud_cover_high { get; set; } = [];
    public List<byte> cloud_cover_mid { get; set; } = [];
    public List<float> visibility { get; set; } = [];
    public List<float> wind_gusts_10m { get; set; } = [];
}

public record struct Location(string Name, double Lat, double Lon) {
    public static readonly List<Location> Constants = [
        new("Hodkovice", 50.6607558, 15.0856189),
        new("Liberec", 50.7610444, 15.0530347),
        new("Alšovice/Skuhrov", 50.6829392, 15.2189356),
        new("Bulovka", 50.9611883, 15.2110381),
        new("Jizerka", 50.8198425, 15.3442644),
        new("Rozdroże", 50.8653161, 15.4485442),
        new("Jítrava", 50.8016283, 14.8535258),
        new("Kumburk", 50.4934542, 15.4456056),
        new("Soběslavice", 50.6048978, 15.0341069),
        new("Vrátenská hora", 50.4787750, 14.6512383),
        new("Malá Skála", 50.6418056, 15.1903611),
        new("Brdo", 50.8225900, 15.1163100)
    ];
}

[SuppressMessage("ReSharper", "UnusedAutoPropertyAccessor.Global")]
public class DataPoint {
    public DateTime Time { get; set; }
    public float Temperature2M { get; set; }
    public int PrecipitationProbability { get; set; }
    public float Precipitation { get; set; }
    public float Rain { get; set; }
    public float Snowfall { get; set; }
    public float Showers { get; set; }
    public float WeatherCode { get; set; }
    public float DewPoint2M { get; set; }
    public float ApparentTemperature { get; set; }
    public float WindSpeed10M { get; set; }
    public ushort WindDirection10M { get; set; }
    public float WindGusts10M { get; set; }
    public float CloudCover { get; set; }
    public byte CloudCoverHigh { get; set; }
    public byte CloudCoverMid { get; set; }
    public byte CloudCoverLow { get; set; }
    public float Visibility { get; set; }
}