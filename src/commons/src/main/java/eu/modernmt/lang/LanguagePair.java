package eu.modernmt.lang;

import java.io.Serializable;
import java.util.Locale;

/**
 * Created by davide on 27/07/17.
 */
public final class LanguagePair implements Serializable {

    public final Locale source;
    public final Locale target;
    private transient LanguagePair reversed = null;

    public LanguagePair(Locale source, Locale target) {
        this.source = source;
        this.target = target;
    }

    public LanguagePair reversed() {
        if (reversed == null) {
            LanguagePair reversed = new LanguagePair(target, source);
            reversed.reversed = this;

            this.reversed = reversed;
        }

        return reversed;
    }

    public boolean equalsIgnoreDirection(LanguagePair o) {
        if (o == null) return false;
        return (source.equals(o.source) && target.equals(o.target)) || (source.equals(o.target) && target.equals(o.source));
    }

    public boolean equalsIgnoreCountry(LanguagePair o) {
        if (o == null) return false;
        return source.getLanguage().equals(o.source.getLanguage()) && target.getLanguage().equals(o.target.getLanguage());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LanguagePair that = (LanguagePair) o;

        if (!source.equals(that.source)) return false;
        return target.equals(that.target);
    }

    @Override
    public int hashCode() {
        int result = source.hashCode();
        result = 31 * result + target.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return source.toLanguageTag() + " \u2192 " + target.toLanguageTag();
    }

}
