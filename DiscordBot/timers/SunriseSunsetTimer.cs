using System.Text.Json;
using Discord;
using DiscordBot.utils;
using FluentScheduler;
using Color = SixLabors.ImageSharp.Color;
using ITimer = DiscordBot.interfaces.ITimer;

namespace DiscordBot.timers;

public class SunriseSunsetTimer : ITimer {
    public string Id() {
        return "sunrise-sunset";
    }

    public void SetupTimer(Registry registry) {
        registry.Schedule(() => new Func<Task>(async () => { await Execute(); }).Invoke()).ToRunEvery(1).Days().At(16, 00);
        //NODO)) Reset before publish!
        //registry.Schedule(() => Execute().Wait()).ToRunNow();
    }

    public async Task Execute() {
        
        await using Stream s = File.OpenRead("./sunrise.config");
        List<ulong>? channels = await JsonSerializer.DeserializeAsync<List<ulong>>(s);

        if (channels == null) {
            throw new NullReferenceException("Unable to parse weather config!");
        }

        List<IChannel> c = channels
            .Select(x => Program.DiscordClient?.GetChannelAsync(x))
            .Select(x => x?.Result)
            .Where(x => x != null)
            .Select(x => (IChannel)x /* cannot actually be null, ignore! Compiler overreacting! */)
            .ToList();


        
        Embed e = await GenerateSunriseSunsetEmbed();
        
        foreach (IChannel channel in c) {
            if (channel is IMessageChannel mch) {
                await mch.SendMessageAsync(embed: e);
            }
        }
    }

    public static async Task<Embed> GenerateSunriseSunsetEmbed(double lat = 50.5973189d, double lon = 15.1501458d) {
        DateTime today = DateTime.UtcNow.Date.AddHours(12);
        DateTime tomorrow = today.AddDays(1);
        
        try {
        }
        catch (Exception exception) {
            Console.WriteLine(exception);
            throw;
        }

        (DateTime sunrise, DateTime sunset, DateTime astrostart, DateTime astroEnd, DateTime moonrise, DateTime moonset)? data = await LunarRiseSetApproximator.GetHorizonsData(today,tomorrow, lat, lon);

        String loc = "Liberecko";
        if (Math.Abs(lat - 50.5973189d) > .0001 || Math.Abs(lon - 15.1501458d) > .0001) {
            loc = $"{lat}N, {lon}E";
        }
        
        if (data == null) return new EmbedBuilder()
            .WithColor(Discord.Color.Red)
            .WithAuthor(new EmbedAuthorBuilder()
                .WithName(Program.DiscordClient.CurrentUser.Username)
                .WithUrl("https://itoncek.space/")
                .WithIconUrl(Program.DiscordClient.CurrentUser.GetAvatarUrl(ImageFormat.Png, 256)))
            .WithTitle($"Časové tabulky pro den <t:{ToEpoch(DateTime.Now)}:D>")
            .WithDescription($"Lokalita: {loc}")
            .AddField(builder => {
                builder.WithName("Error")
                    .WithValue("There has been an error while computing times!");
            })
            .WithTimestamp(DateTimeOffset.Now)
            .Build();

        (DateTime sunrise, DateTime sunset, DateTime astrostart, DateTime astroEnd, DateTime moonrise, DateTime moonset) = data.Value;

        bool moonsetApproximate = false;
        bool moonriseApproximate = false;
        Embed e = new EmbedBuilder()
            .WithAuthor(new EmbedAuthorBuilder()
                .WithName(Program.DiscordClient.CurrentUser.Username)
                .WithUrl("https://itoncek.space/")
                .WithIconUrl(Program.DiscordClient.CurrentUser.GetAvatarUrl(ImageFormat.Png, 256)))
            .WithTitle($"Časové tabulky pro den <t:{ToEpoch(DateTime.Now)}:D>")
            .WithDescription($"Lokalita: {loc}")
            .WithTimestamp(DateTimeOffset.Now)
            .AddField(new EmbedFieldBuilder()
                .WithName("Východ a západ Slunce")
                .WithValue($"""
                            Západ Slunce: <t:{ToEpoch(sunset)}:t> [<t:{ToEpoch(sunset)}:R>]
                            Východ Slunce: <t:{ToEpoch(sunrise)}:t> [<t:{ToEpoch(sunrise)}:R>]

                            """))
             .AddField(new EmbedFieldBuilder()
                 .WithName("Východ a západ Měsíce")
                 .WithValue((moonset - moonrise).TotalSeconds < 0
                     ? $"""
                        Západ Měsíce: <t:{ToEpoch(moonset)}:t> [<t:{ToEpoch(moonset)}:R>] {(moonsetApproximate ? "Mimo okno dat, aproximováno!":"")}
                        Východ Měsíce: <t:{ToEpoch(moonrise)}:t> [<t:{ToEpoch(moonrise)}:R>] {(moonriseApproximate ? "Mimo okno dat, aproximováno!":"")}

                        """
                     : $"""
                         Východ Měsíce: <t:{ToEpoch(moonrise)}:t> [<t:{ToEpoch(moonrise)}:R>] {(moonriseApproximate ? "Mimo okno dat, aproximováno!":"")}
                         Západ Měsíce: <t:{ToEpoch(moonset)}:t> [<t:{ToEpoch(moonset)}:R>] {(moonsetApproximate ? "Mimo okno dat, aproximováno!":"")}

                         """))
             .AddField(new EmbedFieldBuilder()
                 .WithName("Astronomická noc (aktuálně nezahrnuje pozici měsíce)")
                 .WithValue($"""
                             Začátek: <t:{ToEpoch(astrostart)}:t> [<t:{ToEpoch(astrostart)}:R>]
                             Konec: <t:{ToEpoch(astroEnd)}:t> [<t:{ToEpoch(astroEnd)}:R>]

                             """))
            // .AddField(new EmbedFieldBuilder()
            //     .WithName("Astronomická noc")
            //     .WithValue(anAlert))
            .WithFooter(b => {
                b.WithText("Generated from JPL's Horizons data.");
            })
            .WithColor(Discord.Color.Blue)
            .Build();
        return e;
    }

    private static bool IsBetween(DateTime a, DateTime x, DateTime y) {
        return x < a && a < y;
    }

    private static long ToEpoch(DateTime dateTime) {
        return (long)Math.Round((dateTime.ToUniversalTime() - DateTime.UnixEpoch).TotalSeconds);
    }
}