import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Renders the Play Store icon and feature graphic from the same geometry as
 * res/drawable/ic_launcher_foreground.xml, so the store and the launcher agree.
 *
 *   java Render.java          (run from this directory; JDK 11+)
 */
public class Render {

    static final Color INK = new Color(0x11, 0x11, 0x11);

    /** The launcher glyph, drawn in its own 108-unit viewport. */
    static void glyph(Graphics2D g, double x, double y, double size) {
        Graphics2D c = (Graphics2D) g.create();
        c.translate(x, y);
        c.scale(size / 108.0, size / 108.0);
        c.setColor(INK);

        Area ring = new Area(new Ellipse2D.Double(30, 30, 48, 48));
        ring.subtract(new Area(new Ellipse2D.Double(38, 38, 32, 32)));
        c.fill(ring);
        c.fill(new Rectangle2D.Double(50, 46, 8, 16));
        c.fill(new Rectangle2D.Double(30, 50, 6, 8));
        c.fill(new Rectangle2D.Double(22, 50, 4, 8));
        c.fill(new Rectangle2D.Double(72, 50, 6, 8));
        c.fill(new Rectangle2D.Double(82, 50, 4, 8));
        c.dispose();
    }

    static Graphics2D canvas(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        return g;
    }

    public static void main(String[] a) throws Exception {
        // Icon: Play applies its own rounded mask, so this is full-bleed white
        // with the glyph enlarged to sit inside the safe zone.
        BufferedImage icon = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas(icon);
        glyph(g, -96, -96, 704);
        g.dispose();
        ImageIO.write(icon, "png", new File("icon-512.png"));

        // Feature graphic: e-ink plain. Glyph left, name and promise right.
        BufferedImage f = new BufferedImage(1024, 500, BufferedImage.TYPE_INT_RGB);
        g = canvas(f);
        glyph(g, -40, -55, 610);
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 92));
        g.drawString("AssistKey", 500, 232);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 34));
        g.setColor(new Color(0x44, 0x44, 0x44));
        g.drawString("Remap every hardware key", 504, 296);
        g.drawString("on your Viwoods AiPaper", 504, 342);
        g.setColor(INK);
        g.setStroke(new BasicStroke(3f));
        g.drawLine(504, 256, 584, 256);
        g.dispose();
        ImageIO.write(f, "png", new File("feature-1024x500.png"));

        System.out.println("wrote icon-512.png and feature-1024x500.png");
    }
}
