package lphy.graphicalModel;

import net.steppschuh.markdowngenerator.link.Link;
import net.steppschuh.markdowngenerator.list.UnorderedList;
import net.steppschuh.markdowngenerator.text.Text;
import net.steppschuh.markdowngenerator.text.emphasis.BoldText;
import net.steppschuh.markdowngenerator.text.heading.Heading;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * A generator generates values, either deterministically (DeterministicFunction) or stochastically (GenerativeDistribution).
 * A generator also takes named Parameters which are themselves Values, which may have been generated by a Generator.
 */
public interface Generator<T> extends GraphicalModelNode<T> {

    static Class<?> getReturnType(Class<?> genClass) {
        GeneratorInfo generatorInfo = getGeneratorInfo(genClass);
        if (generatorInfo != null) return generatorInfo.returnType();
        return Object.class;
    }

    String getName();

    /**
     * @return a value generated by this generator.
     */
    Value<T> generate();

    String codeString();


    @Override
    default List<GraphicalModelNode> getInputs() {
        return new ArrayList<>(getParams().values());
    }

    Map<String, Value> getParams();

    default void setParam(String paramName, Value<?> value) {

        String methodName = "set" + Character.toUpperCase(paramName.charAt(0)) + paramName.substring(1);

        try {
            Method method = getClass().getMethod(methodName, value.value().getClass());

            method.invoke(this, value.value());
        } catch (NoSuchMethodException e) {

            Method[] methods = getClass().getMethods();
            for (Method method : methods) {
                if (method.getName().equals(methodName)) {
                    try {
                        method.invoke(this, value.value());
                        break;
                    } catch (InvocationTargetException | IllegalAccessException ignored) {
                    }
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    default void setInput(String paramName, Value<?> value) {
        setParam(paramName, value);
        value.addOutput(this);
    }

    default void setInputs(Map<String, Value<?>> params) {
        params.forEach(this::setInput);
    }

    default String getParamName(Value value) {
        Map<String, Value> params = getParams();
        for (String key : params.keySet()) {
            if (params.get(key) == value) return key;
        }
        return null;
    }

    /**
     * @return true if any of the parameters are random variables,
     * or are themselves that result of a function with random parameters as arguments.
     */
    default boolean hasRandomParameters() {
        for (Map.Entry<String, Value> entry : getParams().entrySet()) {

            Value<?> v = entry.getValue();

            if (v == null) {
                throw new RuntimeException("Unexpected null value for param " + entry.getKey() + " in generator " + getName());
            }

            if (v.isRandom()) return true;
        }
        return false;
    }
    default String getParamName(int paramIndex, int constructorIndex) {
        return getParameterInfo(constructorIndex).get(paramIndex).name();
    }

    @Deprecated
    default String getParamName(int paramIndex) {
        return getParamName(paramIndex, 0);
    }

    default List<ParameterInfo> getParameterInfo(int constructorIndex) {
        return getParameterInfo(this.getClass(), constructorIndex);
    }

    default Citation getCitation() {
        return getCitation(getClass());
    }

    default Class<?> getType(String name) {
        return getParams().get(name).getType();
    }

    default GeneratorInfo getInfo() {

        Class<?> classElement = getClass();

        Method[] methods = classElement.getMethods();

        for (Method method : methods) {
            for (Annotation annotation : method.getAnnotations()) {
                if (annotation instanceof GeneratorInfo) {
                    return (GeneratorInfo) annotation;
                }
            }
        }
        return null;
    }

    default String getRichDescription(int index) {

        List<ParameterInfo> pInfo = getParameterInfo(index);

        Map<String, Value> paramValues = getParams();

        StringBuilder html = new StringBuilder("<html><h3>");
        html.append(getName());
        if (this instanceof GenerativeDistribution) {
            html.append(" distribution");
        }
        html.append("</h3>");
        GeneratorInfo info = getInfo();
        if (info != null) {
            html.append("<p>").append(getInfo().description()).append("</p>");
        }
        if (pInfo.size() > 0) {
            html.append("<p>parameters: <ul>");
            for (ParameterInfo pi : pInfo) {
                html.append("<li>").append(pi.name()).append(" (").append(paramValues.get(pi.name())).append("); <font color=\"#808080\">").append(pi.description()).append("</font></li>");
            }
            html.append("</ul>");
        }

        Citation citation = getCitation();
        if (citation != null) {
            html.append("<h3>Reference</h3>");
            html.append(citation.value());
            if (citation.DOI().length() > 0) {
                String url = citation.DOI();
                if (!url.startsWith("http")) {
                    url = "http://doi.org/" + url;
                }
                html.append("<br><a href=\"" + url + "\">" + url + "</a><br>");
            }
        }

        html.append("</p></html>");
        return html.toString();
    }

    static List<ParameterInfo> getParameterInfo(Class<?> c, int constructorIndex) {
        return getParameterInfo(c.getConstructors()[constructorIndex]);
    }

    static Citation getCitation(Class<?> c) {
        Annotation[] annotations = c.getAnnotations();
        for (Annotation annotation : annotations) {
            if (annotation instanceof Citation) {
                return (Citation) annotation;
            }
        }
        return null;
    }


    static List<ParameterInfo> getParameterInfo(Constructor constructor) {
        ArrayList<ParameterInfo> pInfo = new ArrayList<>();

        Annotation[][] annotations = constructor.getParameterAnnotations();
        for (int i = 0; i < annotations.length; i++) {
            Annotation[] annotations1 = annotations[i];
            for (Annotation annotation : annotations1) {
                if (annotation instanceof ParameterInfo) {
                    pInfo.add((ParameterInfo) annotation);
                }
            }
        }

        return pInfo;
    }

    static String getGeneratorMarkdown(Class<? extends Generator> generatorClass) {

        GeneratorInfo generatorInfo = getGeneratorInfo(generatorClass);

        List<ParameterInfo> pInfo = getParameterInfo(generatorClass, 0);

        StringBuilder md = new StringBuilder();

        StringBuilder signature = new StringBuilder();

        signature.append(Generator.getGeneratorName(generatorClass)).append("(");

        int count = 0;
        for (ParameterInfo pi : pInfo) {
            if (count > 0) signature.append(", ");
            signature.append(new Text(pi.type().getSimpleName())).append(" ").append(new BoldText(pi.name()));
            count += 1;
        }
        signature.append(")");

        md.append(new Heading(signature.toString(), 2)).append("\n\n");

        if (generatorInfo != null) md.append(generatorInfo.description()).append("\n\n");

        if (pInfo.size() > 0) {
            md.append(new Heading("Parameters", 3)).append("\n\n");
            List<Object> paramText = new ArrayList<>();

            for (ParameterInfo pi : pInfo) {
                paramText.add(new Text(pi.type().getSimpleName() + " " + new BoldText(pi.name()) + " - " + pi.description()));
            }
            md.append(new UnorderedList<>(paramText));
        }
        md.append("\n\n");

        md.append(new Heading("Return type", 3)).append("\n\n");

        List<String> returnType = Collections.singletonList(generatorInfo != null ? generatorInfo.returnType().getSimpleName() : "Object");
        md.append(new UnorderedList<>(returnType)).append("\n\n");

        Citation citation = getCitation(generatorClass);
        if (citation != null) {
            md.append(new Heading("Reference", 3)).append("\n\n");
            md.append(citation.value());
            if (citation.DOI().length() > 0) {
                String url = citation.DOI();
                if (!url.startsWith("http")) {
                    url = "http://doi.org/" + url;
                }
                md.append(new Link(url, url));
            }
        }
        return md.toString();
    }

    static List<ParameterInfo> getAllParameterInfo(Class c) {
        ArrayList<ParameterInfo> pInfo = new ArrayList<>();
        for (Constructor constructor : c.getConstructors()) {
            pInfo.addAll(getParameterInfo(constructor));
        }
        return pInfo;
    }

    static String getSignature(Class<?> aClass) {

        List<ParameterInfo> pInfo = Generator.getParameterInfo(aClass, 0);

        StringBuilder builder = new StringBuilder();
        builder.append(getGeneratorName(aClass));
        builder.append("(");
        if (pInfo.size() > 0) {
            builder.append(pInfo.get(0).name());
            for (int i = 1; i < pInfo.size(); i++) {
                builder.append(", ");
                builder.append(pInfo.get(i).name());
            }
        }
        builder.append(")");
        return builder.toString();
    }

    static String getGeneratorName(Class<?> c) {
        GeneratorInfo ginfo = getGeneratorInfo(c);
        if (ginfo != null) return ginfo.name();
        return c.getSimpleName();
    }

    static String getGeneratorDescription(Class<?> c) {
        GeneratorInfo ginfo = getGeneratorInfo(c);
        if (ginfo != null) return ginfo.description();
        return "";
    }

    static GeneratorInfo getGeneratorInfo(Class<?> c) {

        Method[] methods = c.getMethods();
        for (Method method : methods) {
            for (Annotation annotation : method.getAnnotations()) {
                if (annotation instanceof GeneratorInfo) {
                    return (GeneratorInfo) annotation;
                }
            }
        }
        return null;
    }

    static String getArgumentCodeString(Map.Entry<String, Value> entry) {
        return getArgumentCodeString(entry.getKey(), entry.getValue());
    }

    static String getArgumentCodeString(String name, Value value) {
        String prefix = "";
        if (!Utils.isInteger(name)) {
            prefix = name + "=";
        }

        if (value == null) {
            throw new RuntimeException("Value of " + name + " is null!");
        }

        if (value.isAnonymous()) return prefix + value.codeString();
        return prefix + value.getId();
    }

    static boolean matchingParameterTypes(List<ParameterInfo> parameterInfos, Object[] initArgs, boolean lightweight) {
        for (int i = 0; i < parameterInfos.size(); i++) {
            ParameterInfo parameterInfo = parameterInfos.get(i);
            Object argument = initArgs[i];

            if (argument != null) {
                Class parameterType = parameterInfo.type();
                Class valueType = lightweight ? argument.getClass() : ((Value) argument).value().getClass();

                if (!parameterType.isAssignableFrom(valueType)) return false;
            } else {
                if (!parameterInfo.optional()) return false;
            }
        }
        return true;
    }

    static Map<String, Value> convertArgumentsToParameterMap(List<ParameterInfo> parameterInfos, Object[] initArgs) {
        Map<String, Value> params = new TreeMap<>();
        for (int i = 0; i < parameterInfos.size(); i++) {
            ParameterInfo parameterInfo = parameterInfos.get(i);
            Value value = (Value) initArgs[i];

            if (value != null) params.put(parameterInfo.name(), value);
        }
        return params;
    }
}
