package lphystudio.app.graphicalmodelpanel;

import lphy.core.*;
import lphy.graphicalModel.*;
import lphy.layeredgraph.Layering;
import lphy.util.LoggerUtils;
import lphystudio.app.graphicalmodelcomponent.GraphicalModelComponent;
import lphystudio.app.graphicalmodelcomponent.interactive.InteractiveGraphicalModelComponent;
import lphystudio.core.codecolorizer.LineCodeColorizer;
import lphystudio.core.swing.TidyComboBox;
import lphystudio.core.swing.TidyTextField;
import lphystudio.core.valueeditors.DoubleArray2DEditor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

public class GraphicalModelPanel extends JPanel {

    GraphicalModelComponent component;
    GraphicalModelInterpreter modelInterpreter;
    GraphicalModelInterpreter dataInterpreter;

    JTabbedPane leftPane;

    ViewerPane rightPane;

    GraphicalLPhyParser parser;

    JLabel repsLabel = new JLabel("reps:");
    JTextField repsField = new TidyTextField("1", 4);
    JButton sampleButton = new JButton("Sample");
    JCheckBox showConstantNodes = new JCheckBox("Show constants");
    JComboBox<Layering> layeringAlgorithm = new TidyComboBox<>(new Layering[]{new Layering.LongestPathFromSinks(), new Layering.LongestPathFromSources()});

    JSplitPane horizSplitPane;
    JSplitPane verticalSplitPane;

    Object displayedElement;

    public GraphicalModelPanel(GraphicalLPhyParser parser) {

        this.parser = parser;

        dataInterpreter = new GraphicalModelInterpreter(parser, LPhyParser.Context.data);
        modelInterpreter = new GraphicalModelInterpreter(parser, LPhyParser.Context.model);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));

        layeringAlgorithm.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                component.setLayering((Layering) layeringAlgorithm.getSelectedItem());
            }
        });
        layeringAlgorithm.setPreferredSize(new Dimension(200, 20));

        buttonPanel.add(repsLabel);
        buttonPanel.add(repsField);
        buttonPanel.add(sampleButton);
        buttonPanel.add(new JLabel(" Layering:"));
        buttonPanel.add(layeringAlgorithm);
        buttonPanel.add(showConstantNodes);

        sampleButton.addActionListener(e -> sample(getReps()));

        showConstantNodes.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                component.setShowConstantNodes(showConstantNodes.isSelected());
            }
        });

        component = new GraphicalModelComponent(parser);

        showConstantNodes.setSelected(component.getShowConstantNodes());

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
        panel.add(component);
        panel.add(buttonPanel);

        setLayout(new BorderLayout());


        GraphicalModelListener listener = new GraphicalModelListener() {
            @Override
            public void valueSelected(Value value) {
                showValue(value);
            }

            @Override
            public void generativeDistributionSelected(GenerativeDistribution g) {
                showParameterized(g);
            }

            @Override
            public void functionSelected(DeterministicFunction f) {

                showParameterized(f);
            }

            @Override
            public void layout() {

            }

        };

        component.addGraphicalModelListener(listener);
        parser.addGraphicalModelChangeListener(component);

        //TODO need a new way to deal with this model listener interaction

        //        parser.addGraphicalModelListener(new GraphicalModelListener() {
//            @Override
//            public void valueSelected(Value value) {
//
//                showValue(value);
//            }
//
//            @Override
//            public void generativeDistributionSelected(GenerativeDistribution g) {
//
//            }
//
//            @Override
//            public void functionSelected(DeterministicFunction f) {
//
//            }
//        });

        rightPane = new ViewerPane(parser, component);
        //rightPane.addTab("New Random Variable", new NewRandomVariablePanel(modelInterpreter, ParserUtils.getGenerativeDistributions()));

        leftPane = new JTabbedPane();
        leftPane.addTab("AutoLayout", panel);
        leftPane.addTab("Interactive", new InteractiveGraphicalModelComponent(parser, component));

        horizSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPane, rightPane);
        horizSplitPane.setResizeWeight(0.5);

        JTabbedPane interpreterPane = new JTabbedPane();
        interpreterPane.add("data", dataInterpreter);
        interpreterPane.add("model", modelInterpreter);

        verticalSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, horizSplitPane, interpreterPane);
        verticalSplitPane.setResizeWeight(0.75);
        add(verticalSplitPane, BorderLayout.CENTER);

        if (parser.getModelSinks().size() > 0) {
            showValue(parser.getModelSinks().iterator().next());
        }
    }

    /**
     * This is duplicated to {@link lphy.parser.REPL#source(BufferedReader)},
     * but has extra code using {@link LineCodeColorizer}
     * and panel function.
     *
     * @param reader
     * @throws IOException
     */
    public void source(BufferedReader reader) throws IOException {

        Script script = Script.loadLPhyScript(reader);

        dataInterpreter.interpretInput(script.dataLines, LPhyParser.Context.data);
        modelInterpreter.interpretInput(script.modelLines, LPhyParser.Context.model);

        repaint();
    }

    private String consumeForLoop(String firstLine, BufferedReader reader) throws IOException {
        StringBuilder builder = new StringBuilder(firstLine);
        String line = reader.readLine();
        while (!line.trim().startsWith("}")) {
            builder.append(line);
            line = reader.readLine();
        }
        builder.append(line);
        return builder.toString();
    }

    private int getReps() {
        int reps = 1;
        try {
            reps = Integer.parseInt(repsField.getText());
        } catch (NumberFormatException nfe) {
            repsField.setText("1");
            reps = 1;
        }
        return reps;
    }

    void sample(int reps) {
        sample(reps, new LinkedList<>());
    }

    void sample(int reps, List<RandomValueLogger> loggers) {

        long start = System.currentTimeMillis();

        String id = null;
        if (displayedElement instanceof Value && !((Value) displayedElement).isAnonymous()) {
            id = ((Value) displayedElement).getId();
        }

        loggers.add(rightPane.variableLog);
        loggers.add(rightPane.treeLog);
        loggers.add(rightPane.variableSummary);

        Sampler sampler = new Sampler(parser);
        sampler.sample(reps, loggers);

        if (id != null) {
            Value<?> selectedValue = parser.getValue(id, LPhyParser.Context.model);
            if (selectedValue != null) {
                showValue(selectedValue, false);
            }
        } else {
            List<Value<?>> sinks = parser.getModelSinks();
            if (sinks.size() > 0) showValue(sinks.get(0), false);
        }
        long end = System.currentTimeMillis();
        LoggerUtils.log.info("sample(" + reps + ") took " + (end - start) + " ms.");

        rightPane.variableSummary.repaint();
        rightPane.repaint();
    }

    void showValue(Value value) {
        showValue(value, true);
    }

    void showValue(Value value, boolean moveToTab) {
        if (value != null) {
            String type = value.value().getClass().getSimpleName();
            String label = value.getLabel();

            showObject(type + " " + label, value, moveToTab);
        }
    }

    private void showParameterized(Generator g) {
        showObject(g.codeString(), g, true);
    }

    private void showObject(String label, Object obj, boolean moveToTab) {
        displayedElement = obj;

        JComponent viewer = null;
        if (obj instanceof Value) {
            viewer = ViewerRegister.getJComponentForValue(obj);
        } else if (obj instanceof Generator) {
            viewer = new JLabel(((Generator) obj).getRichDescription(0));
        } else {
            LoggerUtils.log.severe("Trying to show an object that is neither Value nor Generator.");
        }

        if (viewer instanceof JTextField || viewer instanceof JLabel || viewer instanceof DoubleArray2DEditor) {
            JPanel viewerPanel = new JPanel();
            viewerPanel.setOpaque(false);
            viewerPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
            viewerPanel.add(viewer);
            viewer = viewerPanel;
        }
        rightPane.currentSelectionContainer.setViewportView(viewer);
        rightPane.currentSelectionContainer.setBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createMatteBorder(0, 0, 0, 0, viewer.getBackground()),
                        "<html><font color=\"#808080\" >" + label + "</font></html>"));

        if (moveToTab) rightPane.setSelectedComponent(rightPane.currentSelectionContainer);

        repaint();
    }

    // IO should be in one place
    // use LinguaPhyloStudio readFile(File exampleFile)
    @Deprecated
    public void readScript(File scriptFile) {
        Path path = Paths.get(scriptFile.getAbsolutePath());
        try {
            String mimeType = Files.probeContentType(path);
            // bug: NullPointerException: Cannot invoke "String.equals(Object)"
            if (mimeType.equals("text/plain")) {
                // TODO need to find another way to do this
                //parser.clear();
                dataInterpreter.clear();
                modelInterpreter.clear();

                FileReader reader = new FileReader(scriptFile);
                BufferedReader bufferedReader = new BufferedReader(reader);
                String line = bufferedReader.readLine();
                while (line != null) {
                    dataInterpreter.clear();
                    modelInterpreter.clear();
                    line = bufferedReader.readLine();
                }
                bufferedReader.close();
                reader.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public GraphicalLPhyParser getParser() {
        return parser;
    }

    public ViewerPane getRightPane() {
        return rightPane;
    }

    public JTextPane getCanonicalModelPane() {
        return rightPane.canonicalModelPanel.pane;
    }

    public GraphicalModelComponent getComponent() {
        return component;
    }

    /**
     * clear panel, parser, and interpreters
     */
    public void clear() {
        rightPane.clear();
        dataInterpreter.clear();
        modelInterpreter.clear();
        parser.clear();
    }
}