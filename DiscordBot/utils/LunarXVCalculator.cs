using System.Globalization;
using System.Text;
using Discord.Commands;

namespace DiscordBot.utils;

public class LunarXvCalculator {
    public static async Task<string> DownloadData(DateTime time) {
        const string requestTemplate = "!$$SOF\n" +
                               "MAKE_EPHEM=YES\n" +
                               "COMMAND=10\n" +
                               "EPHEM_TYPE=OBSERVER\n" +
                               "CENTER='coord@301'\n" +
                               "COORD_TYPE=GEODETIC\n" +
                               "SITE_COORD='1.57801,8.1587,0.83778'\n" +
                               "START_TIME='{0}'\n" +
                               "STOP_TIME='{1}'\n" +
                               "STEP_SIZE='1 HOURS'\n" +
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
    
    private static (double a, double b) BestFitLine(List<(DateTime date, double elev)> data) {
        int n = 0;
        double[] x = new double[data.Count];
        double[] y = new double[data.Count];

        // first pass: read in data, compute xbar and ybar
        double sumx = 0.0f, sumy = 0.0f;
        foreach ((DateTime date, double elev) in data) {
            x[n] = ToEpochSecond(date);
            y[n] = elev;
            sumx  += x[n];
            sumy  += y[n];
            n++;
        }

        double xbar = sumx / n;
        double ybar = sumy / n;

        // second pass: compute summary statistics
        double xxbar = 0.0f, xybar = 0.0f;
        for (int i = 0; i < n; i++) {
            xxbar += (x[i] - xbar) * (x[i] - xbar);
            xybar += (x[i] - xbar) * (y[i] - ybar);
        }

        double a = xybar / xxbar;
        double b = ybar - a * xbar;

        return (a,b);
    }

    public static async Task<(bool happens, DateTime? xvTime)> Execute(DateTime time) {
        // NODO)) REMOVE FOR PRODUCTION!
        //time = new DateTime(2025,03,6).AddHours(12);
        
        (double a, double b) = BestFitLine(Parse(Filter(await DownloadData(time))));
        bool rising = a > 0;

        if (!rising) return (false, null);
        
        double root = -b / a;
            
        DateTime risingTime = ToDateTime(root);
        
        if(DateTime.Compare(time, risingTime) < 0 && DateTime.Compare(time.AddDays(1), risingTime) > 0) {
            return (true, risingTime);
        }

        return (false, null);
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