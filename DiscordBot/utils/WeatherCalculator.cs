using DiscordBot.module;
using DiscordBot.timers;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.PixelFormats;

namespace DiscordBot.utils;

public class WeatherCalculator : IDisposable {
    public readonly Image<Rgba32> TemperatureGraph;
    public readonly Image<Rgba32> RainGraph;
    public readonly Image<Rgba32> WindGraph;
    public readonly Image<Rgba32> CloudGraph;

    public WeatherCalculator(List<DataPoint> data, (int w, int h) resolution) {
        TemperatureGraph = new Image<Rgba32>(resolution.w, resolution.h);
        RainGraph = new Image<Rgba32>(resolution.w, resolution.h);
        WindGraph = new Image<Rgba32>(resolution.w, resolution.h);
        CloudGraph = new Image<Rgba32>(resolution.w, resolution.h);

        GenerateTemperatureGraph(data);
    }

    private void GenerateTemperatureGraph(List<DataPoint> data) {
        List<(DateTime Time, float Temperature2M, float DewPoint2M, float ApparentTemperature)> tempData =
            data.Select(x => (x.Time, x.Temperature2M, x.DewPoint2M, x.ApparentTemperature)).ToList();

        float max = tempData.Max(x => float.Max(x.Temperature2M, float.Max(x.DewPoint2M, x.ApparentTemperature)))+1;
        float min = tempData.Min(x => float.Min(x.Temperature2M, float.Min(x.DewPoint2M, x.ApparentTemperature)))-1;

        List<(DateTime Time, double temp2M, double dewPoint2M, double apparentTemperature)> remappedTempData = data
            .Select(x => (
                x.Time,
                GraphUtils.Scale(x.Temperature2M, min, max, 0, 1),
                GraphUtils.Scale(x.DewPoint2M, min, max, 0, 1),
                GraphUtils.Scale(x.ApparentTemperature, min, max, 0, 1)
            )).ToList();

        DateTime minDate = remappedTempData.Min(x => x.Time);
        DateTime maxDate = remappedTempData.Max(x => x.Time);
        (double x, double y)[] dewpoint = remappedTempData.Select(x =>
            (GraphUtils.Scale(ToEpoch(x.Time), ToEpoch(minDate), ToEpoch(maxDate), 0, 1), x.dewPoint2M)).ToArray();
        (double x, double y)[] apparent = remappedTempData.Select(x =>
            (GraphUtils.Scale(ToEpoch(x.Time), ToEpoch(minDate), ToEpoch(maxDate), 0, 1), x.apparentTemperature)).ToArray();
        (double x, double y)[] real = remappedTempData.Select(x =>
            (GraphUtils.Scale(ToEpoch(x.Time), ToEpoch(minDate), ToEpoch(maxDate), 0, 1), x.temp2M)).ToArray();

        GraphUtils.DrawGraphAxis(TemperatureGraph);
        GraphUtils.DrawGraph(TemperatureGraph, Color.Aqua,dewpoint);
        GraphUtils.DrawGraph(TemperatureGraph, Color.Green,apparent);
        GraphUtils.DrawGraph(TemperatureGraph, Color.Red,real);
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