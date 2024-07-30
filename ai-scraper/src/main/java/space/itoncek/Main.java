package space.itoncek.nlcmonitor;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;

public class Main {
	public static void main(String[] args) throws IOException {
		File f = new File("./images/" + System.currentTimeMillis() + ".png");
		f.getParentFile().mkdirs();
		f.createNewFile();

		BufferedImage bi = ImageIO.read(URI.create("https://www.iap-kborn.de/fileadmin/user_upload/MAIN-abteilung/radar/Radars/OswinVHF/Plots/OSWIN_Mesosphere_4hour.png").toURL());

		boolean found = false;
		boolean foundblue = false;
		int endx = 0;
		int idx = 0;
		while (!found) {
			Color c = new Color(sampleColorAtX(bi, idx));
			if (isAlmostWhite(c)) {
				if (foundblue) {
					found = true;
					endx = idx - 1;
				}
			} else {
				if (!foundblue) {
					foundblue = true;
				}
			}
			idx++;
		}
		BufferedImage out = new BufferedImage(46, 46, BufferedImage.TYPE_4BYTE_ABGR);
		for (int x = 0; x < out.getWidth(); x++) {
			for (int y = 0; y < out.getHeight(); y++) {
				out.setRGB(x, y, bi.getRGB((endx - out.getWidth()) + x, (y * 17) + 119));
			}
		}

		ImageIO.write(out, "png", f);
	}

	private static int sampleColorAtX(BufferedImage c, int idx) {
		int r = 0, g = 0, b = 0, count = 0;
		Color y153 = new Color(c.getRGB(idx, 153));
		Color y281 = new Color(c.getRGB(idx, 281));
		Color y407 = new Color(c.getRGB(idx, 407));
		Color y657 = new Color(c.getRGB(idx, 657));
		Color y750 = new Color(c.getRGB(idx, 750));
		Color y864 = new Color(c.getRGB(idx, 864));
		r += y153.getRed();
		g += y153.getGreen();
		b += y153.getBlue();
		count++;
		r += y281.getRed();
		g += y281.getGreen();
		b += y281.getBlue();
		count++;
		r += y407.getRed();
		g += y407.getGreen();
		b += y407.getBlue();
		count++;
		r += y657.getRed();
		g += y657.getGreen();
		b += y657.getBlue();
		count++;
		r += y750.getRed();
		g += y750.getGreen();
		b += y750.getBlue();
		count++;
		r += y864.getRed();
		g += y864.getGreen();
		b += y864.getBlue();
		count++;

		return new Color((r / 255f) / count, (g / 255f) / count, (b / 255f) / count).getRGB();
	}

	private static boolean isAlmostWhite(Color c) {
		return (c.getRed() > 250) && (c.getGreen() > 250) && (c.getBlue() > 250);
	}
}