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
        registry.Schedule(() => new Func<Task>(async () => { await Execute(); }).Invoke()).ToRunEvery(1).Days().At(18,00);
    }

    private async Task Execute() {
        DateTime today = DateTime.UtcNow.Date;
        DateTime tomorrow = DateTime.UtcNow.Date.AddDays(1);
        Sunriset.SunriseSunset(today.Year, today.Month, today.Day,50,15, out double _, out double set);
        Sunriset.SunriseSunset(tomorrow.Year, tomorrow.Month, tomorrow.Day,50,15, out double rise, out double _);

        DateTime sunset = today.AddHours(set).ToLocalTime();
        DateTime sunrise = tomorrow.AddHours(rise).ToLocalTime();
        
        Sunriset.AstronomicalTwilight(today.Year, today.Month, today.Day,50,15, out double _, out double astroset);
        Sunriset.AstronomicalTwilight(tomorrow.Year, tomorrow.Month, tomorrow.Day,50,15, out double astroise, out double _);

        DateTime astrosunset = today.AddHours(astroset).ToLocalTime();
        DateTime astrosunrise = tomorrow.AddHours(astroise).ToLocalTime();
        
        await using Stream s = File.OpenRead("./sunrise.config");
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

        DateTime moonset = DateTime.Now, moonrise = DateTime.Now;
        foreach ((DateTime time, bool rising) in await LunarRiseSetApproximator.Execute(DateTime.Today.AddHours(12))) {
            if (rising) {
                moonrise = time;
            }
            else {
                moonset = time;
            }
        }
        
        Embed e = new EmbedBuilder()
            .WithAuthor(new EmbedAuthorBuilder()
                .WithName(Program.DiscordClient.CurrentUser.Username)
                .WithUrl("https://itoncek.space/")
                .WithIconUrl(Program.DiscordClient.CurrentUser.GetAvatarUrl(ImageFormat.Png, 256)))
            .WithTitle($"Časové tabulky pro den <t:{ToEpoch(DateTime.Now)}:D>")
            .WithDescription("Lokalita: Liberec")
            .AddField(new EmbedFieldBuilder()
                .WithName("Východ a západ slunce")
                .WithValue($"""
                            Západ Slunce: <t:{ToEpoch(sunset)}:t> [<t:{ToEpoch(sunset)}:R>]
                            Východ Slunce: <t:{ToEpoch(sunrise)}:t> [<t:{ToEpoch(sunrise)}:R>]
                            
                            """))
            .AddField(new EmbedFieldBuilder()
                .WithName("Východ a západ měsíce")
                .WithValue((moonset - moonrise).TotalSeconds < 0? 
                    $"""
                     Západ Měsíce: <t:{ToEpoch(moonset)}:t> [<t:{ToEpoch(moonset)}:R>]
                     Východ Měsíce: <t:{ToEpoch(moonrise)}:t> [<t:{ToEpoch(moonrise)}:R>]
                     
                     """:
                    $"""
                     Východ Měsíce: <t:{ToEpoch(moonrise)}:t> [<t:{ToEpoch(moonrise)}:R>]
                     Západ Měsíce: <t:{ToEpoch(moonset)}:t> [<t:{ToEpoch(moonset)}:R>]
                     
                     """))
            .AddField(new EmbedFieldBuilder()
                .WithName("Astronomická noc")
                .WithValue($"""
                            Začátek: <t:{ToEpoch(astrosunset)}:t> [<t:{ToEpoch(astrosunset)}:R>]
                            Konec: <t:{ToEpoch(astrosunrise)}:t> [<t:{ToEpoch(astrosunrise)}:R>]

                            """))
            .WithColor(Discord.Color.Blue)
            .Build();
        foreach (IChannel channel in c) {
            if (channel is IMessageChannel mch) {
                await mch.SendMessageAsync(embed: e);
            }
        }
    }

    private static long ToEpoch(DateTime dateTime) {
        return (long)Math.Round((dateTime.ToUniversalTime() - DateTime.UnixEpoch).TotalSeconds);
    }
}