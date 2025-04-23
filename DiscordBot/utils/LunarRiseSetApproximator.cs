#define SUPRESS_DEBUG
using System.Collections;
using System.Diagnostics;
using System.Globalization;
using System.Text;
using Discord.Commands;

namespace DiscordBot.utils;

public static class LunarRiseSetApproximator {
    private static async Task<string> DownloadData(DateTime start, DateTime end, double lat, double lon) {
        string requestString = "!$$SOF\n" +
                               "MAKE_EPHEM=YES\n" +
                               "COMMAND=301\n" +
                               "EPHEM_TYPE=OBSERVER\n" +
                               "CENTER='coord@399'\n" +
                               "COORD_TYPE=GEODETIC\n" +
                               $"SITE_COORD='{(lon > 0 ? "+" : "")}{lon},{(lat > 0 ? "+" : "")}{lat},0.0'\n" +
                               $"START_TIME='{start:yyyy-MMM-dd HH:mm:ss}'\n" +
                               $"STOP_TIME='{end:yyyy-MMM-dd HH:mm:ss}'\n" +
                               "STEP_SIZE='1 MINUTES'\n" +
                               "QUANTITIES='4'\n" +
                               "REF_SYSTEM='ICRF'\n" +
                               "CAL_FORMAT='CAL'\n" +
                               "CAL_TYPE='M'\n" +
                               "TIME_DIGITS='MINUTES'\n" +
                               "ANG_FORMAT='DEG'\n" +
                               "APPARENT='REFRACTED'\n" +
                               "RANGE_UNITS='AU'\n" +
                               "SUPPRESS_RANGE_RATE='NO'\n" +
                               "SKIP_DAYLT='NO'\n" +
                               "SOLAR_ELONG='0,180'\n" +
                               "EXTRA_PREC='NO'\n" +
                               "R_T_S_ONLY='NO'\n" +
                               "CSV_FORMAT='YES'\n" +
                               "OBJ_DATA='YES'\n";

        using MultipartFormDataContent content = new();
        content.Add(new StringContent(requestString), "input");
        content.Add(new StringContent("text"), "format");
        using HttpClient client = new();
        using HttpResponseMessage res = await client.PostAsync("https://ssd.jpl.nasa.gov/api/horizons_file.api", content);
        string resb = await res.Content.ReadAsStringAsync();
        
        #if DEBUG && !SUPRESS_DEBUG
        Console.WriteLine(resb);
        #endif
        
        return resb;
    }

    private static List<string> Filter(string horizonsData) {
        List<string> sb = [];
        bool writing = false;
        foreach (string s in horizonsData.Split("\n")) {
            if (!writing) {
                if (s.StartsWith("$$SOE")) {
                    writing = true;
                }
            }
            else {
                if(s.StartsWith("$$EOE")) {
                    break;
                }
                sb.Add(s.Trim() + "\n");
            }
        }

        return sb;
    }
    
    private static List<(DateTime date, double elev)> Parse(List<string> input) {
        List<(DateTime date, double elev)> o = [];
        
        foreach (string s in input) {
            string[] elements = s.Split(",").Select(x => x.Trim()).ToArray();
            if (elements.Length != 6) {
                Console.WriteLine("Following line is invalid ({0} parts instead of 6):{1}", elements.Length, s);
                continue;
            } 
            o.Add((DateTime.Parse(elements[0]+"Z"), double.Parse(elements[4])));
        }
        
        return o;
    }
    
    private static (double ax, double bx, long startLimit, long endLimit)[] ClosestPointsToZeroToLines(List<(DateTime date, double elev)> data) {
        List<(double ax, double bx, long startLimit, long endLimit)> output = [];
        for (int i = 0; i < data.Count-1; i++) {
            (DateTime date, double elev) p1 = data[i];
            (DateTime date, double elev) p2 = data[i+1];

            double ax = (p1.elev - p2.elev) / (ToEpochSecond(p1.date) - ToEpochSecond(p2.date));
            double bx = p1.elev - ax*ToEpochSecond(p1.date);
            
            output.Add((ax,bx, ToEpochSecond(p1.date), ToEpochSecond(p2.date)));
        }
        
        return output.ToArray();
    }

    private static DateTime ToDateTime(double epochSecond) {
        return DateTime.UnixEpoch + TimeSpan.FromSeconds(epochSecond);
    }

    private static long ToEpochSecond(DateTime dateTime)
    {
        TimeSpan t = dateTime.ToUniversalTime() - DateTime.UnixEpoch;
        return (long)t.TotalSeconds;
    }

    public static async
        Task<HorizonsResponse> GetHorizonsData(DateTime start, DateTime end, double lat, double lon) {
        string data = await DownloadData(start, end, lat, lon);
        List<(DateTime time, SolarFlag sf, LunarFlag lf)> list = Parsse(Filter(data));

        DateTime moonrise = start,
            moonset = start,
            sunrise = start,
            sunset = start,
            astrostart = start,
            astroend = start,
            nautstart = start,
            nautend = start,
            maxElevTime = start;
        bool moonHasRisen = false;
        bool moonHasSet = false;
        bool sunHasRisen = false;
        bool sunHasSet = false;
        bool astroStarted = false;
        bool astroEnded = false;
        bool nautstarted = false;
        bool nautended = false;

        foreach ((DateTime time, SolarFlag sf, LunarFlag lf) in list) {
            switch (lf) {
                case LunarFlag.Rising when !moonHasRisen:
                    moonHasRisen = true;
                    moonrise = time;
                    break;
                case LunarFlag.Setting when !moonHasSet:
                    moonHasSet = true;
                    moonset = time;
                    break;
                case LunarFlag.MaxElev:
                    maxElevTime = time;
                    break;
            }

            switch (sf) {
                case SolarFlag.Civil when !sunHasSet:
                    sunHasSet = true;
                    sunset = time;
                    break;
                case SolarFlag.Daylight when sunHasSet && !sunHasRisen:
                    sunHasRisen = true;
                    sunrise = time;
                    break;
                case SolarFlag.Astronomical when astroStarted:
                    if (!astroEnded && astroStarted) {
                        astroend = time;
                        astroEnded = true;
                    }

                    break;
                case SolarFlag.Astronomical when !astroEnded:
                    astrostart = time;
                    nautstarted = true;
                    break;

                case SolarFlag.Nautical when nautstarted:
                    if (!nautended) {
                        nautend = time;
                        nautended = true;
                    }

                    break;
                case SolarFlag.Nautical when !nautended:
                    nautstart = time;
                    break;

                case SolarFlag.Night:
                    astroStarted = true;
                    break;
            }
            #if DEBUG && !SUPRESS_DEBUG
            Console.WriteLine($"{time} - {sf}|{lf}");
            #endif
        }

        return new HorizonsResponse(sunrise, sunset, astrostart, astroend, nautstart, nautend, moonrise, moonset,
            maxElevTime);
    }

    private static List<(DateTime time, SolarFlag sf, LunarFlag lf)> Parsse(List<string> data) {
        List<(DateTime time, SolarFlag sf, LunarFlag lf)> o = [];
        
        foreach (string s in data) {
            string[] elements = s.Split(",").Select(x => x.Trim()).ToArray();
            if (elements.Length != 6) {
                Console.WriteLine("Following line is invalid ({0} parts instead of 6):{1}", elements.Length, s);
                continue;
            } 
            o.Add((DateTime.Parse(elements[0]+"Z"), DetermineSolar(elements[1]), DetermineLunar(elements[2])));
        }
        
        return o;
    }

    public static SolarFlag DetermineSolar(string s) {
        return s switch {
            "*" => SolarFlag.Daylight,
            "C" => SolarFlag.Civil,
            "N" => SolarFlag.Nautical,
            "A" => SolarFlag.Astronomical,
            _ => SolarFlag.Night
        };
    }
    public static LunarFlag DetermineLunar(string s) {
        return s switch {
            "m" => LunarFlag.Visible,
            "r" => LunarFlag.Rising,
            "e" => LunarFlag.MaxElev,
            "t" => LunarFlag.Transit,
            "s" => LunarFlag.Setting,
            _ => LunarFlag.Invisible
        };
    }
}

public record struct HorizonsResponse(
    DateTime Sunrise,
    DateTime Sunset,
    DateTime Astrostart,
    DateTime AstroEnd,
    DateTime NauticalStart,
    DateTime NauticalEnd,
    DateTime Moonrise,
    DateTime Moonset,
    DateTime MaxElevTime) {
}

public enum SolarFlag {
    Daylight,
    Civil,
    Nautical,
    Astronomical,
    Night
}

public enum LunarFlag {
    Visible,
    Invisible,
    Rising,
    MaxElev,
    Transit,
    Setting
}