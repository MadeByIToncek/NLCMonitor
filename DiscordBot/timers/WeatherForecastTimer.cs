using System.Diagnostics.CodeAnalysis;
using System.Text.Json;
using Discord;
using DiscordBot.utils;
using FluentScheduler;
using SixLabors.ImageSharp.Formats.Png;
using ITimer = DiscordBot.interfaces.ITimer;

namespace DiscordBot.timers;

public class WeatherForecastTimer : ITimer {
    private const string GenericWeatherRequestUri = "https://api.open-meteo.com/v1/forecast?latitude={0}&longitude={1}&hourly=temperature_2m,precipitation_probability,rain,weather_code,cloud_cover,rain,snowfall,showers,dew_point_2m,apparent_temperature,wind_speed_10m,wind_direction_10m,cloud_cover_low,cloud_cover_high,cloud_cover_mid,visibility,wind_gusts_10m,precipitation&timezone=auto&forecast_days=3";

    public string Id() {
        return "weather-forecast";
    }

    public void SetupTimer(Registry registry) {
        //registry.Schedule(() => new Func<Task>(async () => { await Execute(); }).Invoke()).ToRunEvery(1).Days().At(17, 00);
        //registry.Schedule(() => new Func<Task>(async () => { await Execute(); }).Invoke()).ToRunNow();
    }

    private async Task Execute() {
        await using Stream s = File.OpenRead("./weather.config");
        List<ulong>? channels = await JsonSerializer.DeserializeAsync<List<ulong>>(s);

        if (channels == null) {
            throw new NullReferenceException("Unable to parse weather config!");
        }

        List<IChannel> c = channels
            .Select(x => Program.DiscordClient?.GetChannelAsync(x))
            .Select(x=>x?.Result)
            .Where(x => x != null)
            .Select(x=>(IChannel) x /* cannot actually be null, ignore! Compiler overreacting! */)
            .ToList();

        using HttpClient client = new();
        string formattedWeatherRequestUri = string.Format(GenericWeatherRequestUri, /* Prague = */ 50.088,14.4208);
        await using var stream = await client.GetStreamAsync(formattedWeatherRequestUri);
        WeatherForecast? weatherForecast = JsonSerializer.Deserialize<WeatherForecast>(stream);
        if (weatherForecast == null) {
            throw new NullReferenceException("Unable to parse Weather data!");
        }

        List<DataPoint> data = weatherForecast.GenerateDataPointList();
        using WeatherCalculator calculator = new(data, (3840,2160));

        using TemporaryFile temp = new(".png");
        if (temp.FilePath == null) {
            throw new IOException("Unable to write to a temporary file!");
        }

        await using (Stream stream2 = File.OpenWrite(temp.FilePath)) {
            await calculator.TemperatureGraph.SaveAsync(stream2,new PngEncoder());
        }
        
        foreach (IChannel ch in c) {
            if (ch is IMessageChannel mch) {
                await mch.SendFileAsync(temp.FilePath, "Forecast!");
            }
        }
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