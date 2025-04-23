using DiscordBot.module;
using DiscordBot.timers;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;
using Color = SixLabors.ImageSharp.Color;

namespace DiscordBot.utils;

public class WeatherCalculator : IDisposable {
    public readonly Image<Rgba32> TemperatureGraph;
    public readonly Image<Rgba32> RainGraph;
    public readonly Image<Rgba32> WindGraph;
    public readonly Image<Rgba32> CloudGraph;

    public WeatherCalculator((int w, int h) resolution) {
        TemperatureGraph = new Image<Rgba32>(resolution.w, resolution.h);
        RainGraph = new Image<Rgba32>(resolution.w, resolution.h);
        WindGraph = new Image<Rgba32>(resolution.w, resolution.h);
        CloudGraph = new Image<Rgba32>(resolution.w, resolution.h);
    }

    public async Task GenerateGraphs(List<DataPoint> data) {
        await GenerateTemperatureGraph(data);
        await GenerateRainGraph(data);
    }

    private async Task GenerateRainGraph(List<DataPoint> data) {
        using GraphUtils u = await GraphUtils.Init();
        
        List<(DateTime Time, float Precipitation, int Probability)> rainData =
            data.Select(x => (x.Time, x.Precipitation, x.PrecipitationProbability)).ToList();
        
        
        float max = rainData.Max(x => x.Precipitation)+1;
        
        List<(DateTime Time, double Precipitation, double Probability)> remappedRainData = data
            .Select(x => (
                x.Time,
                u.Scale(x.Precipitation, 0, max, 0, 1),
                u.Scale(x.PrecipitationProbability, 0, 100, 0, 1)
            )).ToList();
        
        DateTime minDate = remappedRainData.Min(x => x.Time);
        DateTime maxDate = remappedRainData.Max(x => x.Time);
        
        (double x, double y)[] precipitation = remappedRainData.Select(x =>
                (u.Scale(ToEpoch(x.Time), ToEpoch(minDate), ToEpoch(maxDate), 0, 1), x.Precipitation))
            .ToArray();
        (double x, double y)[] probability = remappedRainData.Select(x =>
            (u.Scale(ToEpoch(x.Time), ToEpoch(minDate), ToEpoch(maxDate), 0, 1), x.Probability)).ToArray();

        u.DrawGraphAxis(RainGraph);
        await u.DrawRainGraphTicks(RainGraph, max);
        u.DrawGraph(RainGraph, Color.Aqua,precipitation);
        u.DrawGraph(RainGraph, Color.White,probability);
    }

    private async Task GenerateTemperatureGraph(List<DataPoint> data) {
        using GraphUtils u = await GraphUtils.Init();
        List<(DateTime Time, float Temperature2M, float DewPoint2M, float ApparentTemperature)> tempData =
            data.Select(x => (x.Time, x.Temperature2M, x.DewPoint2M, x.ApparentTemperature)).ToList();

        float max = tempData.Max(x => float.Max(x.Temperature2M, float.Max(x.DewPoint2M, x.ApparentTemperature)))+1;
        float min = tempData.Min(x => float.Min(x.Temperature2M, float.Min(x.DewPoint2M, x.ApparentTemperature)))-1;

        List<(DateTime Time, double temp2M, double dewPoint2M, double apparentTemperature)> remappedTempData = data
            .Select(x => (
                x.Time,
                u.Scale(x.Temperature2M, min, max, 0, 1),
                u.Scale(x.DewPoint2M, min, max, 0, 1),
                u.Scale(x.ApparentTemperature, min, max, 0, 1)
            )).ToList();


        List<(double, int, float)> ticks = [];
        for (int i = (int) MathF.Ceiling(min); i < MathF.Floor(max); i++) {
            if (i % 2 == 0) {
                ticks.Add((u.Scale(i, min, max, 0, 1), i, i % 10 == 0 ? 8f : 2f));
            }
        }
        
        DateTime minDate = remappedTempData.Min(x => x.Time);
        DateTime maxDate = remappedTempData.Max(x => x.Time);
        (double x, double y)[] dewpoint = remappedTempData.Select(x =>
            (u.Scale(ToEpoch(x.Time), ToEpoch(minDate), ToEpoch(maxDate), 0, 1), x.dewPoint2M)).ToArray();
        (double x, double y)[] apparent = remappedTempData.Select(x =>
            (u.Scale(ToEpoch(x.Time), ToEpoch(minDate), ToEpoch(maxDate), 0, 1), x.apparentTemperature)).ToArray();
        (double x, double y)[] real = remappedTempData.Select(x =>
            (u.Scale(ToEpoch(x.Time), ToEpoch(minDate), ToEpoch(maxDate), 0, 1), x.temp2M)).ToArray();

        u.DrawGraphAxis(TemperatureGraph);
        await u.DrawTemperatureGraphTicks(TemperatureGraph, ticks);
        u.DrawGraph(TemperatureGraph, Color.Aqua,dewpoint);
        u.DrawGraph(TemperatureGraph, Color.Green,apparent);
        u.DrawGraph(TemperatureGraph, Color.Red,real);
    }
    
    private double ToEpoch(DateTime time) {
        return (time - DateTime.UnixEpoch).TotalSeconds;
    }

    public void Dispose() {
        TemperatureGraph.Dispose();
        RainGraph.Dispose();
        WindGraph.Dispose();
        CloudGraph.Dispose();
        GC.Collect(2,GCCollectionMode.Aggressive);
    }
}