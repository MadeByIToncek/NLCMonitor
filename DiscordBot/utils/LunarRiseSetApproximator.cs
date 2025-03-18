using System.Globalization;
using System.Text;
using Discord.Commands;

namespace DiscordBot.utils;

public class LunarRiseSetApproximator {
    public static async Task<string> DownloadData(DateTime time) {
        const string requestTemplate = "!$$SOF\n" +
                               "MAKE_EPHEM=YES\n" +
                               "COMMAND=301\n" +
                               "EPHEM_TYPE=OBSERVER\n" +
                               "CENTER='coord@399'\n" +
                               "COORD_TYPE=GEODETIC\n" +
                               "SITE_COORD='+14.43330,+50.08330,250'\n" +
                               "START_TIME='{0}'\n" +
                               "STOP_TIME='{1}'\n" +
                               "STEP_SIZE='5 MINUTES'\n" +
                               "QUANTITIES='4'\n" +
                               "REF_SYSTEM='ICRF'\n" +
                               "CAL_FORMAT='CAL'\n" +
                               "CAL_TYPE='M'\n" +
                               "TIME_DIGITS='SECONDS'\n" +
                               "ANG_FORMAT='HMS'\n" +
                               "APPARENT='AIRLESS'\n" +
                               "RANGE_UNITS='AU'\n" +
                               "SUPPRESS_RANGE_RATE='NO'\n" +
                               "SKIP_DAYLT='NO'\n" +
                               "SOLAR_ELONG='0,180'\n" +
                               "EXTRA_PREC='NO'\n" +
                               "R_T_S_ONLY='NO'\n" +
                               "CSV_FORMAT='YES'\n" +
                               "OBJ_DATA='NO'\n";

        string requestString = String.Format(requestTemplate, time.ToString("yyyy-MMM-dd HH:mm:ss"), time.AddDays(1).ToString("yyyy-MMM-dd HH:mm:ss"));

        using MultipartFormDataContent content = new();
        content.Add(new StringContent(requestString),"input");
        content.Add(new StringContent("text"),"format");
        using HttpClient client = new();
        using HttpResponseMessage res = await client.PostAsync("https://ssd.jpl.nasa.gov/api/horizons_file.api", content);
        return await res.Content.ReadAsStringAsync();
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
        for (var i = 0; i < data.Count-1; i++) {
            var p1 = data[i];
            var p2 = data[i+1];

            double ax = (p1.elev - p2.elev) / (ToEpochSecond(p1.date) - ToEpochSecond(p2.date));
            double bx = p1.elev - ax*ToEpochSecond(p1.date);
            
            output.Add((ax,bx, ToEpochSecond(p1.date), ToEpochSecond(p2.date)));
        }

        return output.ToArray();
    }

    public static async Task<List<(DateTime time, bool rising)>> Execute(DateTime time) {
        // NODO)) REMOVE FOR PRODUCTION!
        //time = new DateTime(2025,03,6).AddHours(12);

        (double ax, double bx, long startLimit, long endLimit)[] tuple = ClosestPointsToZeroToLines(Parse(Filter(await DownloadData(time))));

        List<(DateTime time, bool rising)> results = [];
        foreach ((double ax, double bx, long startLimit, long endLimit) in tuple) {
            double r = -bx / ax;
            if (r >= startLimit && r <= endLimit) {
                results.Add((ToDateTime(r),ax > 0));
            }
        }

        return results;
    }

    private static DateTime ToDateTime(double epochSecond) {
        return DateTime.UnixEpoch + TimeSpan.FromSeconds(epochSecond);
    }

    private static long ToEpochSecond(DateTime dateTime)
    {
        TimeSpan t = dateTime.ToUniversalTime() - DateTime.UnixEpoch;
        return (long)t.TotalSeconds;
    }
    
}