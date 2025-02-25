package lphystudio.app.graphicalmodelpanel.viewer;

import lphy.graphicalModel.Value;

import static lphy.graphicalModel.ValueUtils.quotedString;

public class StringArrayLabel extends ArrayLabel<String> {

    public StringArrayLabel(Value<String[]> values) {
        super(values);
    }

    public StringArrayLabel(String[] values) {
        super(values);
    }

    @Override
    public String valueToString(String rawValue) {
        return quotedString(rawValue);
    }
}
