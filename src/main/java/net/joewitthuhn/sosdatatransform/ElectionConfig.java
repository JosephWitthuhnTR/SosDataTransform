package net.joewitthuhn.sosdatatransform;

import java.util.Collections;
import java.util.Map;

/**
 * @author Joseph Witthuhn (jwitthuhn@uwalumni.com)
 */
public class ElectionConfig {
    public final Map<String, Integer> electionDates;
    public final Map<Integer, String> electionNames;

    public ElectionConfig(Map<String, Integer> electionDates, Map<Integer, String> electionNames) {
        this.electionDates = Collections.unmodifiableMap(electionDates);
        this.electionNames = Collections.unmodifiableMap(electionNames);
    }
}
