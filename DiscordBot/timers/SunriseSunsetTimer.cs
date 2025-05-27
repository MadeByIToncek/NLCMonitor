#define SUPRESS_DEBUG
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
        #if DEBUG && !SUPRESS_DEBUG
        registry.Schedule(() => Execute().Wait()).ToRunNow();
        #else
        registry.Schedule(() => new Func<Task>(async () => { await Execute(); }).Invoke()).ToRunEvery(1).Days().At(16, 00);
        #endif
    }

    public static async Task Execute() {
        ulong[] channels = [
            1349038242229387386L
        ];

        List<IChannel> c = channels
            .Select(x => Program.DiscordClient.GetChannelAsync(x))
            .Select(x => x.Result)
            .Where(x => x != null)
            .ToList();
        
        foreach (IChannel channel in c) {
            if (channel is IMessageChannel mch) {
                await mch.TriggerTypingAsync();
            }
        }
        
        Embed[] e = await GenerateSunriseSunsetEmbed();
        
        foreach (IChannel channel in c) {
            if (channel is IMessageChannel mch) {
                await mch.SendMessageAsync(embeds: e);
            }
        }
    }

    public static async Task<Embed[]> GenerateSunriseSunsetEmbed(double lat = 50.5973189d, double lon = 15.1501458d) {
        DateTime today = DateTime.UtcNow.Date.AddHours(12);
        DateTime tomorrow = today.AddDays(1);
        
        try {
        }
        catch (Exception exception) {
            Console.WriteLine(exception);
            throw;
        }

        HorizonsResponse? data = await LunarRiseSetApproximator.GetHorizonsData(today,tomorrow, lat, lon);

        String loc = "Liberecko";
        if (Math.Abs(lat - 50.5973189d) > .0001 || Math.Abs(lon - 15.1501458d) > .0001) {
            loc = $"{lat}N, {lon}E";
        }

        HorizonsResponse r = data.Value;

        bool moonsetApproximate = false;
        bool moonriseApproximate = false;
        Embed e = new EmbedBuilder()
            .WithAuthor(new EmbedAuthorBuilder()
                .WithName(Program.DiscordClient.CurrentUser.Username)
                .WithUrl("https://itoncek.space/")
                .WithIconUrl(Program.DiscordClient.CurrentUser.GetAvatarUrl(ImageFormat.Png, 256)))
            .WithTitle($"Východy a západy pro den <t:{ToEpoch(DateTime.Now)}:D>")
            .WithDescription($"Lokalita: {loc}")
            .WithTimestamp(DateTimeOffset.Now)
            .AddField(new EmbedFieldBuilder()
                .WithName("Východ a západ Slunce")
                .WithValue($"""
                            Západ Slunce: <t:{ToEpoch(r.Sunset)}:t> [<t:{ToEpoch(r.Sunset)}:R>]
                            Východ Slunce: <t:{ToEpoch(r.Sunrise)}:t> [<t:{ToEpoch(r.Sunrise)}:R>]

                            """))
             .AddField(new EmbedFieldBuilder()
                 .WithName("Východ a západ Měsíce")
                 .WithValue((r.Moonset - r.Moonrise).TotalSeconds < 0
                     ? $"""
                        Západ Měsíce: <t:{ToEpoch(r.Moonset)}:t> [<t:{ToEpoch(r.Moonset)}:R>] {(moonsetApproximate ? "Mimo okno dat, aproximováno!":"")}
                        Východ Měsíce: <t:{ToEpoch(r.Moonrise)}:t> [<t:{ToEpoch(r.Moonrise)}:R>] {(moonriseApproximate ? "Mimo okno dat, aproximováno!":"")}

                        """
                     : $"""
                         Východ Měsíce: <t:{ToEpoch(r.Moonrise)}:t> [<t:{ToEpoch(r.Moonrise)}:R>] {(moonriseApproximate ? "Mimo okno dat, aproximováno!":"")}
                         Kulminace Měsíce: <t:{ToEpoch(r.MaxElevTime)}:t> [<t:{ToEpoch(r.MaxElevTime)}:R>]
                         Západ Měsíce: <t:{ToEpoch(r.Moonset)}:t> [<t:{ToEpoch(r.Moonset)}:R>] {(moonsetApproximate ? "Mimo okno dat, aproximováno!":"")}

                         """))
            .WithFooter(b => {
                b.WithText("Vygenerováno z JPL Horizons dat.");
            })
            .WithColor(Discord.Color.Blue)
            .Build();

        
        
        Embed e2 = new EmbedBuilder()
            .WithAuthor(new EmbedAuthorBuilder()
                .WithName(Program.DiscordClient.CurrentUser.Username)
                .WithUrl("https://itoncek.space/")
                .WithIconUrl(Program.DiscordClient.CurrentUser.GetAvatarUrl(ImageFormat.Png, 256)))
            .WithTitle($"Pozorovací časy pro den <t:{ToEpoch(DateTime.Now)}:D>")
            .WithDescription($"""
                              Astronomický soumrak: <t:{ToEpoch(r.NauticalStart)}:t> [<t:{ToEpoch(r.NauticalStart)}:R>]
                              Začátek astro. noci: <t:{ToEpoch(r.Astrostart)}:t> [<t:{ToEpoch(r.Astrostart)}:R>]
                              Konec astro. noci: <t:{ToEpoch(r.AstroEnd)}:t> [<t:{ToEpoch(r.AstroEnd)}:R>]
                              Astronomické svítání: <t:{ToEpoch(r.NauticalEnd)}:t> [<t:{ToEpoch(r.NauticalEnd)}:R>]
                              
                              """)
            .WithFooter(b => {
                b.WithText("Vygenerováno z JPL Horizons dat.");
            })
            .WithTimestamp(DateTimeOffset.Now)
            .WithColor(Discord.Color.Blue)
            .Build();
        return [e,e2];
    }

    private static long ToEpoch(DateTime dateTime) {
        return (long)Math.Round((dateTime.ToUniversalTime() - DateTime.UnixEpoch).TotalSeconds);
    }
}