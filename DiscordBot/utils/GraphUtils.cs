using DiscordBot.utils;
using SixLabors.Fonts;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.Drawing.Processing;
using SixLabors.ImageSharp.Memory;
using SixLabors.ImageSharp.PixelFormats;
using SixLabors.ImageSharp.Processing;

namespace DiscordBot.module;

public class GraphUtils : IDisposable{
    private readonly string _fpath;
    public const int borderGap = 200;
    public static readonly HttpClient Client = new();

    private GraphUtils(string fpath) {
        _fpath = fpath;
    }

    public static async Task<GraphUtils> Init() {
        await using Stream s =
            await Client.GetStreamAsync(
                "https://github.com/MadeByIToncek/cdn.itoncek.cf/raw/refs/heads/main/fonts/Purista-normal.ttf");
        string fpath = Path.GetTempPath() + Path.GetRandomFileName() + ".ttf";
        FileStream fs = File.OpenWrite(fpath);
        await s.CopyToAsync(fs);
        await fs.DisposeAsync();
        return new GraphUtils(fpath);
    }
     
    public void DrawGraphAxis(Image img) {
        PointF[][] lines = [
            [
                new PointF(borderGap, img.Height - borderGap),
                new PointF(borderGap, borderGap)
            ],
            [
                new PointF(borderGap, img.Height - borderGap),
                new PointF(img.Width, img.Height - borderGap)
            ]
        ];

        img.Mutate(x => {
            foreach (PointF[] line in lines) {
                x.DrawLine(new Color(new Argb32(255, 255, 255, 255)), 8, line);
            }
        });
    }

    public void DrawGraph(Image img, Color color, (double x, double y)[] data /* need to be in range 0-1*/) {
        List<PointF> points = [];
        foreach ((double x, double y) in data) {
            float ny = (float)Scale(y, 0, 1, img.Height - borderGap, borderGap);
            float nx = (float)Scale(x, 0, 1, borderGap, img.Width);
            points.Add(new PointF(nx, ny));
        }

        img.Mutate(x => { x.DrawLine(color, 8f, points.ToArray()); });
    }

    public double Scale(double value, double min, double max, double minScale, double maxScale) {
        double scaled = minScale + (value - min) / (max - min) * (maxScale - minScale);
        return scaled;
    }

    public void DrawTemperatureGraphTicks(Image<Rgba32> img, List<(double, int, float)> ticks) {
        FontCollection collection = new();
        collection.Add(_fpath);

        Font font = collection.Get("Purista").CreateFont(90, FontStyle.Italic);
        img.Mutate(x => {
            foreach ((double height, int degrees, float thickness) in ticks) {
                float y = (float)Scale(height, 0, 1, img.Height - borderGap, borderGap);
                x.DrawLine(new Color(new Argb32(255, 255, 255, 100)), thickness, new PointF(borderGap, y),
                    new PointF(img.Width, y));
                (float fx, float fy, float w, float h) =
                    TextMeasurer.MeasureSize($"{degrees:00}°c", new TextOptions(font));
                x.DrawText($"{degrees:00}°c", font, Color.White, new PointF(borderGap - w - 20, y - (2 * h / 3)));
            }
        });
    }

    public async Task DrawRainGraphTicks(Image<Rgba32> img, float max) {
        FontCollection collection = new();
        await using Stream s =
            await Client.GetStreamAsync(
                "https://github.com/MadeByIToncek/cdn.itoncek.cf/raw/refs/heads/main/fonts/Purista-normal.ttf");
        string fpath = Path.GetTempPath() + Path.GetRandomFileName() + ".ttf";
        FileStream fs = File.OpenWrite(fpath);
        await s.CopyToAsync(fs);
        await fs.DisposeAsync();
        collection.Add(fpath);

        Font font = collection.Get("Purista").CreateFont(40, FontStyle.Italic);
        img.Mutate(x => {
            for (int i = 0; i < 5; i++) {
                float height = i * (max / 5f);
                float y = (float)Scale(height, 0, max, img.Height - borderGap, borderGap);
                x.DrawLine(Color.Aqua.WithAlpha(1/3f), 3f, new PointF(borderGap, y),
                    new PointF(img.Width, y));
                (float fx, float fy, float w, float h) =
                    TextMeasurer.MeasureSize($"{height} mm", new TextOptions(font));
                x.DrawText($"{height} mm", font, Color.Aqua, new PointF(borderGap - w - 20, y -  h / 2));
            }

            Font font2 = collection.Get("Purista").CreateFont(50, FontStyle.Italic);
            for (int i = 0; i < 5; i++) {
                float y = (float)Scale(i+.5, 0, 5, img.Height - borderGap, borderGap);
                x.DrawLine(Color.White.WithAlpha(1/3f), 3f, new PointF(borderGap, y),
                    new PointF(img.Width, y));
                (float fx, float fy, float w, float h) =
                    TextMeasurer.MeasureSize($"{(i+.5)*20}%", new TextOptions(font2));
                x.DrawText($"{(i+.5)*20}%", font2, Color.White, new PointF(borderGap - w - 20, y -  h / 2));
            }
        });
        File.Delete(fpath);
    }

    public void Dispose() {
        File.Delete(_fpath);
    }
}