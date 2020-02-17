package james.graphicalModel;

import james.Coalescent;
import james.Yule;
import james.core.ErrorModel;
import james.core.JCPhyloCTMC;
import james.core.PhyloBrownian;
import james.core.PhyloCTMC;
import james.core.distributions.*;
import james.core.distributions.Exp;
import james.core.functions.*;
import james.graphicalModel.types.*;
import james.app.GraphicalModelChangeListener;
import james.app.GraphicalModelListener;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class GraphicalModelParser {

    // CURRENT MODEL STATE
    private SortedMap<String, Value> dictionary = new TreeMap<>();

    // convenience book-keeping
    Set<String> globalArguments = new TreeSet<>();

    // HISTORY OF LINES PARSED
    List<String> lines = new ArrayList<>();

    // PARSER STATE
    Map<String, Class> genDistDictionary = new TreeMap<>();
    Map<String, Class> functionDictionary = new TreeMap<>();

    List<GraphicalModelChangeListener> listeners = new ArrayList<>();
    List<GraphicalModelListener> gmListeners = new ArrayList<>();

    enum Keyword {
        remove
    }

    public GraphicalModelParser() {

        Class[] genClasses = {Normal.class, LogNormal.class, Exp.class, Coalescent.class, JCPhyloCTMC.class,
                PhyloCTMC.class, PhyloBrownian.class, Dirichlet.class, Gamma.class, DiscretizedGamma.class,
                ErrorModel.class, Yule.class};

        for (Class genClass : genClasses) {
            genDistDictionary.put(genClass.getSimpleName(), genClass);
        }

        Class[] functionClasses = {james.core.functions.Exp.class, JukesCantor.class, K80.class, HKY.class, GTR.class,
                BinaryCTMC.class, Newick.class};

        for (Class functionClass : functionClasses) {
            functionDictionary.put(Func.getFunctionName(functionClass), functionClass);
        }
        System.out.println(functionDictionary);
    }

    public void clear() {
        // clear current model state
        dictionary.clear();
        globalArguments.clear();

        // clear history of lines
        lines.clear();
        notifyListeners();
    }

    public void addGraphicalModelChangeListener(GraphicalModelChangeListener listener) {
        listeners.add(listener);
    }

    public void addGraphicalModelListener(GraphicalModelListener listener) {
        gmListeners.add(listener);
    }

    public SortedMap<String, Value> getDictionary() {
        return dictionary;
    }

    public Set<Value> getRoots() {
        Set<String> nonArguments = new HashSet<>();
        dictionary.values().forEach((val) -> nonArguments.add(val.getId()));
        nonArguments.removeAll(globalArguments);

        SortedSet<Value> nonArgValues = new TreeSet<>(Comparator.comparing(Value::getId));
        nonArguments.forEach((id) -> nonArgValues.add(dictionary.get(id)));

        return nonArgValues;
    }

    public void parseLines(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            parseLine(lines[i]);
        }
    }

    public void parseLineProperly(String line) {

    }

    public void parseLine(String line) {
        int lineNumber = nextLineNumber();
        if (isRandomVariableLine(line)) {
            parseRandomVariable(line, lineNumber);
        } else if (isFunctionLine(line)) {
            parseFunctionLine(line, lineNumber);
        } else if (isFixedParameterLine(line)) {
            parseFixedParameterLine(line, lineNumber);
        } else if (isKeywordLine(line)) {
            parseKeywordLine(line, lineNumber);
        } else if (isValueId(line)) {
            selectValue(dictionary.get(line.substring(0, line.length() - 1)));
        } else {
            throw new RuntimeException("Parse error on line " + lineNumber + ": " + line);
        }
        lines.add(line);
        notifyListeners();
    }

    private void selectValue(Value value) {
        for (GraphicalModelListener listener : gmListeners) {
            listener.valueSelected(value);
        }
    }

    private boolean isValueId(String line) {
        return dictionary.keySet().contains(line.substring(0, line.length() - 1));
    }

    private void parseKeywordLine(String line, int lineNumber) {
        line = line.trim();
        Keyword keyword = null;
        for (Keyword kw : Keyword.values()) {
            if (line.startsWith(kw.name())) {
                keyword = kw;
                break;
            }
        }
        String remainder = line.substring(keyword.name().length());
        switch (keyword) {
            case remove:
                parseRemove(remainder, lineNumber);
        }
    }

    private void parseRemove(String remainder, int lineNumber) {
        if (remainder.startsWith("(")) {
            remainder = remainder.trim();
            if (remainder.endsWith(");")) {
                String argument = remainder.substring(1, remainder.length() - 2);
                if (dictionary.keySet().contains(argument)) {
                    dictionary.remove(argument);
                    System.out.println("Removed " + argument + ".");
                } else {
                    System.out.println("Value named " + argument + " not found.");
                }
            } else
                throw new RuntimeException("Parsing error: expected ')' after argument to keyword " + Keyword.remove);

        } else throw new RuntimeException("Parsing error: expected '(' after keyword " + Keyword.remove);
    }

    private boolean isKeywordLine(String line) {
        line = line.trim();
        for (Keyword keyword : Keyword.values()) {
            if (line.startsWith(keyword.name())) {
                return true;
            }
        }
        return false;
    }

    private void notifyListeners() {
        for (GraphicalModelChangeListener listener : listeners) {
            listener.modelChanged();
        }
    }

    private int nextLineNumber() {
        return lines.size();
    }

    private void parseFixedParameterLine(String line, int lineNumber) {
        int firstEquals = line.indexOf('=');
        if (firstEquals > 0) {
            String id = line.substring(0, firstEquals).trim();
            String remainder = line.substring(firstEquals + 1).trim();
            if (remainder.endsWith(";")) {
                remainder = remainder.substring(0, remainder.length()-1);
            }

            Value literalValue = parseLiteralValue(id, remainder, lineNumber);
            dictionary.put(literalValue.getId(), literalValue);
        }
    }

    private Value parseLiteralValue(String id, String valueString, int lineNumber) {

        if (valueString.startsWith("\"") && valueString.endsWith("\"")) {
            // parse string
            return new Value<>(id, valueString.substring(1, valueString.length()-1));
        }


        if (valueString.startsWith("[") && valueString.endsWith("]")) {
            return parseList(id, valueString, lineNumber);
        }
        try {
            Integer intVal = Integer.parseInt(valueString);
            return new IntegerValue(id, intVal);
        } catch (NumberFormatException e) {
        }

        try {
            Double val = Double.parseDouble(valueString);
            return new DoubleValue(id, val);
        } catch (NumberFormatException e) {
        }

        throw new RuntimeException("Parsing fixed parameter " + id + " with value " + valueString + " failed on line " + lineNumber);
    }

    private Value parseList(String id, String valueString, int lineNumber) {

        // remove whitespace
        valueString = valueString.replace(" ", "");

        // remove outer brackets
        valueString = valueString.substring(1, valueString.length() - 1);

        if (valueString.startsWith("[")) {
            // nested list -> parse as [][]

            String[] parts = valueString.split("\\]\\,");

            // remove opening bracket from each element and closing bracket from last element
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i].replace("[", "");
                parts[i] = parts[i].replace("]", "");
            }

            Number[][] array;

            Number[] num = parseNumberArray(parts[0], lineNumber);
            if (num instanceof Double[]) {
                array = new Double[parts.length][];
            } else {
                array = new Integer[parts.length][];
            }

            for (int i = 0; i < parts.length; i++) {
                array[i] = parseNumberArray(parts[i], lineNumber);
            }

            if (array instanceof Double[][]) {
                return new DoubleArray2DValue(id, (Double[][]) array);
            } else {
                return new IntegerArray2DValue(id, (Integer[][]) array);
            }

        } else {

            Number[] val = parseNumberArray(valueString, lineNumber);

            if (val instanceof Integer[]) {

                return new IntegerArrayValue(id, (Integer[]) val);
            } else {
                return new DoubleArrayValue(id, (Double[]) val);
            }
        }
    }

    private Number[] parseNumberArray(String arrayString, int lineNumber) {

        String[] elements = arrayString.split(",");
        for (int i = 0; i < elements.length; i++) {
            elements[i] = elements[i].trim();
        }

        if (isInteger(elements[0])) {
            Integer[] val = new Integer[elements.length];
            for (int i = 0; i < elements.length; i++) {
                try {
                    val[i] = Integer.parseInt(elements[i]);
                } catch (NumberFormatException e) {
                    throw new RuntimeException("Parser error: parsing integer list at line number " + lineNumber + " but found non-integer:" + elements[i]);
                }
            }
            return val;

        } else if (isDouble(elements[0])) {
            Double[] val = new Double[elements.length];

            for (int i = 0; i < elements.length; i++) {
                try {
                    val[i] = Double.parseDouble(elements[i]);
                } catch (NumberFormatException e) {
                    throw new RuntimeException("Parser error: parsing real list at line number " + lineNumber + " but found non-real:" + elements[i]);
                }
            }
            return val;
        }
        throw new RuntimeException("Parser error: parsing number array at line number " + lineNumber + " but found non-number:" + elements[0]);
    }

    private boolean isInteger(String s) {
        try {
            Integer intVal = Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isDouble(String s) {
        try {
            Double doubleVal = Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void parseFunctionLine(String line, int lineNumber) {
        int firstEquals = line.indexOf('=');
        String id = line.substring(0, firstEquals).trim();
        String remainder = line.substring(firstEquals + 1);
        String functionString = remainder.substring(0, remainder.indexOf(';'));
        Value val = parseDeterministicFunction(id, functionString, lineNumber);
        dictionary.put(val.getId(), val);
    }

    private Value parseDeterministicFunction(String id, String functionString, int lineNumber) {
        String[] parts = functionString.split("\\(");
        if (parts.length != 2)
            throw new RuntimeException("Parsing deterministic function " + parts[0] + " failed on line " + lineNumber);
        String name = parts[0].trim();
        String argumentString = parts[1].substring(0, parts[1].indexOf(')'));
        Map<String, String> arguments = parseArguments(argumentString, lineNumber);

        Class functionClass = functionDictionary.get(name);
        if (functionClass == null)
            throw new RuntimeException("Parsing error: Unrecognised deterministic function: " + name);

        try {
            List<Object> initargs = new ArrayList<>();
            Constructor constructor = getConstructorByArguments(arguments, functionClass, initargs);
            if (constructor == null) {
                System.err.println("DeterministicFunction class: " + functionClass);
                System.err.println("     Arguments: " + arguments);
                throw new RuntimeException("Parser error: no constructor found for deterministic function " + name + " with arguments " + arguments);
            }

            DeterministicFunction func = (DeterministicFunction) constructor.newInstance(initargs.toArray());
            for (String parameterName : arguments.keySet()) {
                Value value = dictionary.get(arguments.get(parameterName));
                func.setParam(parameterName, value);
                globalArguments.add(value.id);
            }
            Value val = func.apply();
            val.setId(id);
            return val;
        } catch (InstantiationException e) {
            e.printStackTrace();
            throw new RuntimeException("Parsing generative distribution " + name + " failed on line " + lineNumber);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException("Parsing generative distribution " + name + " failed on line " + lineNumber);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            throw new RuntimeException("Parsing generative distribution " + name + " failed on line " + lineNumber);
        }
    }

    private void parseRandomVariable(String line, int lineNumber) {
        String[] parts = line.split("~");
        if (parts.length != 2)
            throw new RuntimeException("Parsing random variable " + parts[0] + "failed on line " + lineNumber);
        String id = parts[0].trim();
        String genString = parts[1].substring(0, parts[1].indexOf(';'));
        GenerativeDistribution genDist = parseGenDist(genString, lineNumber);
        RandomVariable var = genDist.sample(id);
        dictionary.put(var.getId(), var);
    }

    private GenerativeDistribution parseGenDist(String genString, int lineNumber) {
        String[] parts = genString.split("\\(");
        if (parts.length != 2)
            throw new RuntimeException("Parsing generative distribution " + parts[0] + "failed on line " + lineNumber);
        String name = parts[0].trim();
        String argumentString = parts[1].substring(0, parts[1].indexOf(')'));
        Map<String, String> arguments = parseArguments(argumentString, lineNumber);

        Class genDistClass = genDistDictionary.get(name);
        if (genDistClass == null)
            throw new RuntimeException("Parsing error: Unrecognised generative distribution: " + name);

        try {
            List<Object> initargs = new ArrayList<>();
            Constructor constructor = getConstructorByArguments(arguments, genDistClass, initargs);
            if (constructor == null)
                throw new RuntimeException("Parser error: no constructor found for generative distribution " + name + " with arguments " + arguments);

            GenerativeDistribution dist = (GenerativeDistribution) constructor.newInstance(initargs.toArray());
            for (String parameterName : arguments.keySet()) {
                Value value = dictionary.get(arguments.get(parameterName));
                dist.setParam(parameterName, value);
                globalArguments.add(value.id);
            }
            return dist;
        } catch (InstantiationException e) {
            e.printStackTrace();
            throw new RuntimeException("Parsing generative distribution " + name + " failed on line " + lineNumber);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException("Parsing generative distribution " + name + " failed on line " + lineNumber);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            throw new RuntimeException("Parsing generative distribution " + name + " failed on line " + lineNumber);
        }
    }

    private Constructor getConstructorByArguments(Map<String, String> arguments, Class genDistClass, List<Object> initargs) {
        System.out.println(genDistClass.getSimpleName() + " " + arguments );
        for (Constructor constructor : genDistClass.getConstructors()) {
            List<ParameterInfo> pInfo = Parameterized.getParameterInfo(constructor);
            if (match(arguments, pInfo)) {
                for (int i = 0; i < pInfo.size(); i++) {
                    String id = arguments.get(pInfo.get(i).name());
                    if (id != null) {
                        Value arg = dictionary.get(id);
                        initargs.add(arg);
                        if (arg != null) {
                            globalArguments.add(arg.id);
                        } else if (!pInfo.get(i).optional()) {
                            throw new RuntimeException("Value id=" + arguments.get(pInfo.get(i).name()) + " not found for required input!");
                        }
                    } else if (!pInfo.get(i).optional()) {
                        throw new RuntimeException("No argument provided for required input" + pInfo.get(i).name() + " of " + genDistClass.getSimpleName());
                    } else {
                        initargs.add(null);
                    }
                }
                return constructor;
            }
        }
        return null;
    }

    /**
     * A match occurs if the required parameters are in the argument map and the remaining arguments in the map match names of optional arguments.
     * @param arguments
     * @param pInfo
     * @return
     */
    private boolean match(Map<String, String> arguments, List<ParameterInfo> pInfo) {

        Set<String> requiredArguments = new TreeSet<>();
        Set<String> optionalArguments = new TreeSet<>();
        for (ParameterInfo pinfo : pInfo) {
            if (pinfo.optional()) {
                optionalArguments.add(pinfo.name());
            } else {
                requiredArguments.add(pinfo.name());
            }
        }

        if (!arguments.keySet().containsAll(requiredArguments)) {
            return false;
        }
        Set<String> allArguments = optionalArguments;
        allArguments.addAll(requiredArguments);
        return allArguments.containsAll(arguments.keySet());
    }

    private Map<String, String> parseArguments(String argumentString, int lineNumber) {
        String[] argumentStrings = argumentString.split(",");
        TreeMap<String, String> arguments = new TreeMap<>();
        for (String argumentPair : argumentStrings) {
            if (argumentPair.indexOf('=') < 0) {
                argumentPair = "x=" + argumentPair;
            }
            String[] keyValue = argumentPair.split("=");
            if (keyValue.length != 2)
                throw new RuntimeException("Parsing argument " + keyValue[0].trim() + " failed on line " + lineNumber);
            String key = keyValue[0].trim();
            String value = keyValue[1].trim();
            arguments.put(key, value);
        }
        return arguments;
    }

    public static boolean isFunctionLine(String line) {
        int firstEquals = line.indexOf('=');
        if (firstEquals > 0) {
            String id = line.substring(0, firstEquals).trim();
            String remainder = line.substring(firstEquals + 1).trim();
            return (remainder.indexOf('(') > 0 && !remainder.startsWith("\""));
        } else return false;
    }

    public static boolean isRandomVariableLine(String line) {
        return (line.indexOf('~') > 0);
    }

    public static boolean isFixedParameterLine(String line) {
        int firstEquals = line.indexOf('=');
        if (firstEquals > 0) {
            String id = line.substring(0, firstEquals).trim();

            String remainder = line.substring(firstEquals + 1).trim();
            String valueString = remainder.substring(0, remainder.indexOf(';'));
            valueString = valueString.trim();

            if (valueString.startsWith("\"")) {
                // is string
                return true;
            }

            if (valueString.startsWith("[") && valueString.endsWith("]")) {
                // is list
                return true;
            }

            try {
                Double val = Double.parseDouble(valueString);
                return true;
            } catch (NumberFormatException nfe) {
                return false;
            }
        } else return false;
    }

    public static void main(String[] args) {
        String[] lines = {
                "kappa = 10.0;",
                "L = 50;",
                "mu = 0.01;",
                "n = 20;",
                "mean = 3.0;",
                "sd = 1.0;",
                "logTheta ~ Normal(mean=mean, sd=sd);",
                "Θ = exp(logTheta);",
                "Q = k80(kappa=kappa);",
                "ψ ~ Coalescent(n=n, theta=Θ);",
                "D ~ PhyloCTMC(L=L, mu=mu, Q=Q, tree=ψ);"};

        GraphicalModelParser parser = new GraphicalModelParser();
        parser.parseLines(lines);
        System.out.println(parser.dictionary);

    }

    public List<String> getLines() {
        return lines;
    }

    public void sample() {

        Set<String> sampled = new HashSet<>();

        for (Value value : getRoots()) {

            if (value instanceof RandomVariable) {
                RandomVariable variable = sampleAll(((RandomVariable) value).getGenerativeDistribution(), sampled);
                variable.setId(value.id);
                dictionary.put(variable.getId(), variable);
            }
        }
        notifyListeners();
    }

    private RandomVariable sampleAll(GenerativeDistribution generativeDistribution, Set<String> sampled) {

        for (Map.Entry<String, Value> e : getNewlySampledParams(generativeDistribution, sampled).entrySet()) {
            generativeDistribution.setParam(e.getKey(), e.getValue());
            sampled.add(e.getValue().getId());
        }

        return generativeDistribution.sample();
    }

    private Value sampleAll(DeterministicFunction function, Set<String> sampled) {

        for (Map.Entry<String, Value> e : getNewlySampledParams(function, sampled).entrySet()) {
            function.setParam(e.getKey(), e.getValue());
            sampled.add(e.getValue().getId());
        }

        return function.apply();
    }

    private Map<String, Value> getNewlySampledParams(Parameterized parameterized, Set<String> sampled) {
        Map<String, Value> params = parameterized.getParams();

        Map<String, Value> newlySampledParams = new HashMap<>();
        for (Map.Entry<String, Value> e : params.entrySet()) {

            if (!sampled.contains(e.getValue().getId())) {
                // needs to be sampled

                if (e.getValue() instanceof RandomVariable) {
                    RandomVariable v = (RandomVariable) e.getValue();

                    RandomVariable nv = sampleAll(v.getGenerativeDistribution(), sampled);
                    nv.setId(v.getId());
                    newlySampledParams.put(e.getKey(), nv);
                    dictionary.put(nv.getId(), nv);
                } else if (e.getValue().getFunction() != null) {
                    Value v = e.getValue();
                    DeterministicFunction f = e.getValue().getFunction();

                    Value nv = sampleAll(f, sampled);
                    nv.setId(v.getId());
                    newlySampledParams.put(e.getKey(), nv);
                    dictionary.put(nv.getId(), nv);
                }
            } else {
                String id = e.getValue().getId();
                newlySampledParams.put(e.getKey(), dictionary.get(id));
            }
        }
        return newlySampledParams;
    }
}