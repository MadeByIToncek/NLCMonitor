using SixLabors.Fonts;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.Drawing.Processing;
using SixLabors.ImageSharp.Memory;
using SixLabors.ImageSharp.PixelFormats;
using SixLabors.ImageSharp.Processing;

namespace DiscordBot.module;

public static class GraphUtils {
    public const int borderGap = 100;

    public static void DrawGraphAxis(Image img) {
        PointF[][] lines = [
            [
                new PointF(borderGap, img.Height - borderGap),
                new PointF(borderGap, borderGap)
            ], [
                new PointF(borderGap, img.Height - borderGap),
                new PointF(img.Width - borderGap, img.Height - borderGap)
            ]
        ];
        
        img.Mutate(x => {
            foreach (PointF[] line in lines) {
                x.DrawLine(new Color(new Argb32(255, 255, 255, 255)), 8, line);
            }
        });
    }

    public static void DrawGraph(Image img, Color color, (double x,double y)[] data /* need to be in range 0-1*/) {
        List<PointF> points = [];
        foreach ((double x, double y) in data) {
            float ny = (float)Scale(y, 0, 1, img.Height - borderGap, borderGap);
            float nx = (float)Scale(x, 0, 1, borderGap, img.Width - borderGap);
            points.Add(new PointF(nx, ny));
        }

        img.Mutate(x => {
            x.DrawLine(color, 8f, points.ToArray());
        });
    }

    public static double Scale(double value, double min, double max, double minScale, double maxScale)
    {
        double scaled = minScale + (value - min)/(max-min) * (maxScale - minScale);
        return scaled;
    }
}