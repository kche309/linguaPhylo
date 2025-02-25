package lphystudio.app.alignmentcomponent;

import jebl.evolution.sequences.SequenceType;
import lphy.evolution.alignment.Alignment;
import lphy.evolution.alignment.ErrorAlignment;
import lphy.evolution.likelihood.PhyloCTMC;
import lphy.evolution.tree.TimeTree;
import lphy.graphicalModel.GenerativeDistribution;
import lphy.graphicalModel.RandomVariable;
import lphy.graphicalModel.Value;
import lphystudio.app.Utils;
import lphystudio.app.treecomponent.TimeTreeComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.prefs.Preferences;

public class AlignmentComponent extends JComponent {

    static Preferences preferences = Preferences.userNodeForPackage(AlignmentComponent.class);

    static Font taxaMinFont = new Font(Font.MONOSPACED, Font.PLAIN, Utils.MIN_FONT_SIZE);

    Color[] colors;
    Value<? extends Alignment> alignmentValue;
    Alignment alignment;

    Value<TimeTree> timeTree = null;

    int spacer = 5;

    int maxTaxaWidth = 0;

    public static boolean showErrorsIfAvailable = true;

    public AlignmentComponent(Value<? extends Alignment> av) {
        this.alignmentValue = av;
        this.alignment = av.value();
        SequenceType sequenceType = alignment.getSequenceType();
        this.colors = ColourPalette.getCanonicalStateColours(sequenceType);

        if (av instanceof RandomVariable) {
            GenerativeDistribution gen = ((RandomVariable)av).getGenerativeDistribution();
            if (gen instanceof PhyloCTMC) {
                timeTree = ((PhyloCTMC) gen).getParams().get("tree");
            }
        }

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {

                if (e.getButton() == MouseEvent.BUTTON3) {
                    setShowTreeInAlignmentViewerIfAvailable(!getShowTreeInAlignmentViewerIfAvailable());
                } else {
                    showErrorsIfAvailable = !showErrorsIfAvailable;
                }
                repaint();
            }
        });

        computeMinMaxSize();

        preferences.addPreferenceChangeListener(evt -> computeMinMaxSize());
    }

    private void computeMinMaxSize() {
        int desktopWidth = Toolkit.getDefaultToolkit().getScreenSize().width;
        int desktopHeight = Toolkit.getDefaultToolkit().getScreenSize().height;

        int maximumWidth = Math.min(desktopWidth, Utils.MAX_FONT_SIZE*alignment.nchar());
        int maximumHeight = Math.min(desktopHeight, Utils.MAX_FONT_SIZE*alignment.ntaxa());
        int minimumHeight = Utils.MIN_FONT_SIZE*alignment.ntaxa();

        maxTaxaWidth = getMaxStringWidth(alignment.getTaxa().getTaxaNames(), getFontMetrics(taxaMinFont));
        if (maxTaxaWidth < 50) maxTaxaWidth = 50;

        int minimumWidth = maxTaxaWidth + Math.max(alignment.nchar(), Utils.MIN_FONT_SIZE);

        if (isShowingTree()) minimumWidth += maxTaxaWidth;

        setMaximumSize(new Dimension(maximumWidth, maximumHeight));
        setMinimumSize(new Dimension(minimumWidth, minimumHeight));
        repaint();
    }

    private int getMaxStringWidth(String[] strings, FontMetrics fontMetrics) {
        int maxStringWidth = 0;
        for (String str : strings) {
            int stringWidth = fontMetrics.stringWidth(str);
            if (stringWidth > maxStringWidth) maxStringWidth = stringWidth;
        }
        return maxStringWidth;
    }

    // for CharSetAlignment
    public AlignmentComponent() { }

    static boolean getShowTreeInAlignmentViewerIfAvailable() {
        return preferences.getBoolean("showTreeInAlignmentViewerIfAvailable", true);
    }

    public static void setShowTreeInAlignmentViewerIfAvailable(boolean showTreeInAlignmentViewerIfAvailable) {
        preferences.putBoolean("showTreeInAlignmentViewerIfAvailable", showTreeInAlignmentViewerIfAvailable);
    }

    public void paintComponent(Graphics g) {

        Insets insets = getInsets();
        g.translate(insets.left, insets.top);
        int width = getWidth() - insets.left - insets.right;
        int height = getHeight() - insets.top - insets.bottom;

        Graphics2D g2d = (Graphics2D) g;

        int xdelta = 0;

        double h = height / (double) alignment.ntaxa();

        Font f = taxaMinFont;
        if (h<9) {
            g.setFont(f.deriveFont(8.0f));
        } else if (h < 10) {
            g.setFont(f.deriveFont(9.0f));
        } else if (h < 11) {
            g.setFont(f.deriveFont(10.0f));
        } else if (h < 12) {
            g.setFont(f.deriveFont(11.0f));
        } else {
            g.setFont(f.deriveFont(12.0f));
        }

        if (isShowingTree()) {

            int ytrans = (int)Math.round(h/2);

            int treeHeight = (int)Math.round(height - h);
            g.translate(0, ytrans);

            TimeTreeComponent treeComponent = new TimeTreeComponent(timeTree.value());
            treeComponent.setBorder(BorderFactory.createEmptyBorder(1,1,1,0));
            treeComponent.setSize(maxTaxaWidth*2, treeHeight);
            treeComponent.paintComponent(g);
            width -= 2.0*maxTaxaWidth;
            xdelta = 2*maxTaxaWidth;
            g.translate(0, -ytrans);
        }

        int maxWidth = 0;
        int[] sWidth = new int[alignment.ntaxa()];

        for (int i = 0; i < alignment.ntaxa(); i++) {

            sWidth[i] = g.getFontMetrics().stringWidth(alignment.getTaxonName(i));
            if (sWidth[i] > maxWidth) maxWidth = sWidth[i];
        }

        double w = (width - maxWidth - spacer) / (double) alignment.nchar();

        int ascent = g.getFontMetrics().getAscent();
        double ydelta = (h - ascent) / 2.0 + ascent;

        for (int i = 0; i < alignment.ntaxa(); i++) {
            double y = i * h;

            if (!isShowingTree()) {
                g.setColor(Color.black);
                g.drawString(alignment.getTaxonName(i),maxWidth-sWidth[i]+xdelta,(int)Math.round(y+ydelta));
            }

            for (int j = 0; j < alignment.nchar(); j++) {

                int state = alignment.getState(i, j);
//                int col = SequenceTypeFactory.getColourIndex(state, alignment.getSequenceType());
                Color c = ColourPalette.getColour(state, colors);

                if (alignment instanceof ErrorAlignment && showErrorsIfAvailable && ((ErrorAlignment)alignment).isError(i,j)) {
                    c = new Color(255-c.getRed(), 255-c.getGreen(), 255-c.getBlue());
                }

                g.setColor(c);

                Rectangle2D rect2D = new Rectangle2D.Double(j * w + xdelta + maxWidth + spacer, y, w, h * 0.95);

                g2d.fill(rect2D);
            }
        }
        g.translate(-insets.left, -insets.top);

    }

    boolean isShowingTree() {
        return getShowTreeInAlignmentViewerIfAvailable() && timeTree != null;
    }
}
