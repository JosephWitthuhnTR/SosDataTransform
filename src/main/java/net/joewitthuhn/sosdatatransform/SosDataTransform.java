package net.joewitthuhn.sosdatatransform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * @author Joseph Witthuhn (jwitthuhn@uwalumni.com)
 */
public class SosDataTransform {
    static {
        System.out.println("Questions? For help with this application, contact:\nJoseph Witthuhn (jwitthuhn@uwalumni.com)\n");
    }

    // These values are hard coded configuration values
    private static final Pattern INPUT_FILE_PATTERN = Pattern.compile("(?:Election|Voter)0(\\d).txt");
    private static final Pattern ELECTION_CONFIG_PATTERN = Pattern.compile("(\\d{2}/\\d{2}/\\d{4}),(.*)");
    private static final Pattern DISTRICT_CONFIG_PATTERN = Pattern.compile("[1-9][0-9]*");
    private static final Map<String, String> COUNTY_CODES;

    // These are column indexes in the state's ELECTION And VOTER files. This will need to be changed if they
    // add/remove/reorder columns.
    private static final int IDX_ELECTION_VOTER_ID = 0;
    private static final int IDX_ELECTION_DATE = 1;
    private static final int IDX_ELECTION_METHOD = 3;

    private static final int IDX_VOTER_VOTER_ID = 0;
    private static final int IDX_VOTER_COUNTY= 1;
    private static final int IDX_VOTER_SCHOOL_DISTRICT = 26;
    private static final int IDX_VOTER_SENATE_DISTRICT = 30;

    // These are loaded from configuration
    private static final Map<String, Integer> ELECTION_DATES;
    private static final Map<Integer, String> ELECTION_NAMES;
    private static final List<Character> CONGRESSIONAL_DISTRICTS;
    private static final Set<String> SENATE_DISTRICTS;
    private static final Set<String> SCHOOL_DISTRICTS;

    // These data structures must be accessible for data processing within closures
    private static final Map<Integer, String[]> ELECTIONS = new HashMap<>();
    private static int currentRow = 0;
    private static int numVoterColumns = 0;

    public static void main(String[] args) throws IOException {
        System.out.println();
        // Delete and recreate output folder
        FileUtils.deleteDirectory(new File("output"));
        if (!new File("output").mkdir()) {
            throw new RuntimeException("Unable to create output directory.");
        }

        for (Character i : CONGRESSIONAL_DISTRICTS) {
            System.out.println("Reading election records for CD" + i);

            Files.lines(Paths.get("input" + File.separatorChar + "Election0" + i + ".txt")).forEachOrdered(line -> {
                String[] parts = readCsvLine(line);

                // Don't process the header line
                if (!parts[IDX_ELECTION_DATE].equals("ElectionDate")) {
                    Integer electionIndex = ELECTION_DATES.get(parts[IDX_ELECTION_DATE]);

                    // Is this an election we care about?
                    if (electionIndex != null) {
                        Integer voterId = Integer.valueOf(parts[IDX_ELECTION_VOTER_ID]);
                        String[] electionRecord = ELECTIONS.get(voterId);
                        if (electionRecord == null) {
                            electionRecord = new String[ELECTION_DATES.size()];
                            ELECTIONS.put(voterId,  electionRecord);
                        }
                        electionRecord[electionIndex] = parts[IDX_ELECTION_METHOD];
                    }
                }
            });
        }
        System.out.println(ELECTIONS.size() + " election records processed.\n");

        for (Character i : CONGRESSIONAL_DISTRICTS) {
            System.out.println("Processing CD" + i);
            currentRow = 0;
            try (SXSSFWorkbook workbook = new SXSSFWorkbook()) {
                try {
                    Sheet worksheet = workbook.createSheet("CD" + i);
                    Files.lines(Paths.get("input" + File.separatorChar + "Voter0" + i + ".txt"), StandardCharsets.ISO_8859_1).forEachOrdered(line -> {
                        String[] parts = readCsvLine(line);

                        boolean includeBasedOnSenateDistrict = SENATE_DISTRICTS.isEmpty() || SENATE_DISTRICTS.contains(parts[IDX_VOTER_SENATE_DISTRICT]);
                        boolean includeBasedOnSchoolDistrict = SCHOOL_DISTRICTS.isEmpty() || SCHOOL_DISTRICTS.contains(parts[IDX_VOTER_SCHOOL_DISTRICT]);
                        if (parts[IDX_VOTER_VOTER_ID].equals("VoterId") || (includeBasedOnSenateDistrict && includeBasedOnSchoolDistrict)) {
                            Row row = worksheet.createRow(currentRow);
                            for (int j = 0; j < parts.length; j ++) {
                                Cell cell = row.createCell(j);

                                if (j == IDX_VOTER_COUNTY) {
                                    // Translate County Code to County Name
                                    String countyName = COUNTY_CODES.get(parts[j]);
                                    if (countyName != null) {
                                        cell.setCellValue(countyName);
                                    } else {
                                        cell.setCellValue(parts[j]);
                                    }
                                } else {
                                    cell.setCellValue(parts[j]);
                                }
                            }

                            if (parts[IDX_VOTER_VOTER_ID].equals("VoterId")) {
                                numVoterColumns = parts.length;

                                // If we are on the header row, insert election names
                                for (int j = 0; j < ELECTION_NAMES.size(); j++) {
                                    Cell cell = row.createCell(numVoterColumns + j);
                                    cell.setCellValue(ELECTION_NAMES.get(j));
                                }
                            } else {
                                for (int j = 0; j < ELECTION_DATES.size(); j++) {
                                    Integer voterId = Integer.valueOf(parts[IDX_VOTER_VOTER_ID]);
                                    String[] methods = ELECTIONS.get(voterId);

                                    if (methods != null && methods[j] != null) {
                                        Cell cell = row.createCell(numVoterColumns + j);
                                        cell.setCellValue(methods[j]);
                                    }
                                }
                            }

                            currentRow++;
                        }
                    });

                    System.out.println("Writing " + (currentRow - 1) + " records to CD" + i + ".xlsx");
                    try (FileOutputStream outputFile = new FileOutputStream("output" + File.separatorChar + "CD" + i + ".xlsx")) {
                        workbook.write(outputFile);
                    }
                } catch (UncheckedIOException e) {
                    System.err.println("Error reading voter file for CD" + i);
                    e.printStackTrace();
                } finally {
                    workbook.dispose();
                }
            }
        }

        System.out.println("\nJob completed.");
    }

    private static String[] readCsvLine(String line) {
        return line.substring(1, line.length() - 1).split("\",\"");
    }

    static {
        CONGRESSIONAL_DISTRICTS = readCongressionalDistricts();
        ElectionConfig electionConfig = readElectionConfig();
        ELECTION_DATES = electionConfig.electionDates;
        ELECTION_NAMES = electionConfig.electionNames;
        SENATE_DISTRICTS = readDistrictFilters("senateDistricts.txt");
        SCHOOL_DISTRICTS = readDistrictFilters("schoolDistricts.txt");

        Map<String, String> countyCodes = new HashMap<>();
        countyCodes.put("01","Aitkin");
        countyCodes.put("02","Anoka");
        countyCodes.put("03","Becker");
        countyCodes.put("04","Beltrami");
        countyCodes.put("05","Benton");
        countyCodes.put("06","Big-Stone");
        countyCodes.put("07","Blue-Earth");
        countyCodes.put("08","Brown");
        countyCodes.put("09","Carlton");
        countyCodes.put("10","Carver");
        countyCodes.put("11","Cass");
        countyCodes.put("12","Chippewa");
        countyCodes.put("13","Chisago");
        countyCodes.put("14","Clay");
        countyCodes.put("15","Clearwater");
        countyCodes.put("16","Cook");
        countyCodes.put("17","Cottonwood");
        countyCodes.put("18","Crow Wing");
        countyCodes.put("19","Dakota");
        countyCodes.put("20","Dodge");
        countyCodes.put("21","Douglas");
        countyCodes.put("22","Faribault");
        countyCodes.put("23","Fillmore");
        countyCodes.put("24","Freeborn");
        countyCodes.put("25","Goodhue");
        countyCodes.put("26","Grant");
        countyCodes.put("27","Hennepin");
        countyCodes.put("28","Houston");
        countyCodes.put("29","Hubbard");
        countyCodes.put("30","Isanti");
        countyCodes.put("31","Itasca");
        countyCodes.put("32","Jackson");
        countyCodes.put("33","Kanabec");
        countyCodes.put("34","Kandiyohi");
        countyCodes.put("35","Kittson");
        countyCodes.put("36","Koochiching");
        countyCodes.put("37","Lac qui Parle");
        countyCodes.put("38","Lake");
        countyCodes.put("39","Lake of the Woods");
        countyCodes.put("40","Le Sueur");
        countyCodes.put("41","Lincoln");
        countyCodes.put("42","Lyon");
        countyCodes.put("43","McLeod");
        countyCodes.put("44","Mahnomen");
        countyCodes.put("45","Marshall");
        countyCodes.put("46","Martin");
        countyCodes.put("47","Meeker");
        countyCodes.put("48","Mille Lacs");
        countyCodes.put("49","Morrison");
        countyCodes.put("50","Mower");
        countyCodes.put("51","Murray");
        countyCodes.put("52","Nicollet");
        countyCodes.put("53","Nobles");
        countyCodes.put("54","Norman");
        countyCodes.put("55","Olmsted");
        countyCodes.put("56","Otter Tail");
        countyCodes.put("57","Pennington");
        countyCodes.put("58","Pine");
        countyCodes.put("59","Pipestone");
        countyCodes.put("60","Polk");
        countyCodes.put("61","Pope");
        countyCodes.put("62","Ramsey");
        countyCodes.put("63","Red Lake");
        countyCodes.put("64","Redwood");
        countyCodes.put("65","Renville");
        countyCodes.put("66","Rice");
        countyCodes.put("67","Rock");
        countyCodes.put("68","Roseau");
        countyCodes.put("69","St. Louis");
        countyCodes.put("70","Scott");
        countyCodes.put("71","Sherburne");
        countyCodes.put("72","Sibley");
        countyCodes.put("73","Stearns");
        countyCodes.put("74","Steele");
        countyCodes.put("75","Stevens");
        countyCodes.put("76","Swift");
        countyCodes.put("77","Todd");
        countyCodes.put("78","Traverse");
        countyCodes.put("79","Wabasha");
        countyCodes.put("80","Wadena");
        countyCodes.put("81","Waseca");
        countyCodes.put("82","Washington");
        countyCodes.put("83","Watonwan");
        countyCodes.put("84","Wilkin");
        countyCodes.put("85","Winona");
        countyCodes.put("86","Wright");
        countyCodes.put("87","Yellow Medicine");
        COUNTY_CODES = Collections.unmodifiableMap(countyCodes);
    }

    private static List<Character> readCongressionalDistricts() {
        System.out.println("Looking for Secretary of State data files");

        if (!new File("input").isDirectory()) {
            System.err.println("Input Directory does not exist - creating it.");
            if (!new File("input").mkdir()) {
                throw new RuntimeException("Input Directory does not exist - unable to create it.");
            }
            throw new RuntimeException("Input Directory does not exist - created it.");
        }

        List<Character> districts = new ArrayList<>(8);
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(Paths.get("input"))){
            for (Path path : dirStream) {
                String filename = path.toString();
                filename = filename.substring(filename.lastIndexOf(File.separatorChar) + 1);
                Matcher m = INPUT_FILE_PATTERN.matcher(filename);
                if (!m.matches()) {
                    throw new RuntimeException("Unrecognized input file name: " + filename);
                }
                Character district = m.group(1).charAt(0);
                if (!districts.contains(district)) {
                    districts.add(district);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error looking for input files.", e);
        }

        if (districts.isEmpty()) {
            throw new RuntimeException("No input files found in input folder.");
        }

        Collections.sort(districts);
        return Collections.unmodifiableList(districts);
    }

    private static ElectionConfig readElectionConfig() {
        String electionsConfigFile = "config" + File.separatorChar + "elections.txt";
        System.out.println("Reading configuration from " + electionsConfigFile);

        Map<String, Integer> electionDates = new HashMap<>();
        Map<Integer, String> electionNames = new HashMap<>();

        try {
            Files.lines(Paths.get(electionsConfigFile)).forEachOrdered(line -> {
                line = line.trim();
                if (line.trim().length() == 0) {
                    return;
                }

                Matcher m = ELECTION_CONFIG_PATTERN.matcher(line);
                if (!m.matches()) {
                    throw new RuntimeException("Invalid election configuration line: " + line);
                }

                electionDates.put(m.group(1), currentRow);
                electionNames.put(currentRow, m.group(2));
                currentRow++;
            });
        } catch (IOException e) {
            throw new RuntimeException("Error reading election configuration.", e);
        }

        if (currentRow == 0) {
            throw new RuntimeException("No election configuration found.");
        }

        System.out.println(currentRow + " elections found to watch.");
        return new ElectionConfig(electionDates, electionNames);
    }

    private static Set<String> readDistrictFilters(String filename) {
        String districtsConfigFile = "config" + File.separatorChar + filename;
        System.out.println("Reading district filter from " + districtsConfigFile);

        Set<String> districts = new HashSet<>();

        try {
            Files.lines(Paths.get(districtsConfigFile)).forEachOrdered(line -> {
                line = line.trim();
                if (line.trim().length() == 0) {
                    return;
                }

                Matcher m = DISTRICT_CONFIG_PATTERN.matcher(line);
                if (!m.matches()) {
                    throw new RuntimeException("Invalid district configuration line in " + filename + ": " + line);
                }

                districts.add(line);
            });

            if (districts.isEmpty()) {
                System.out.println("File empty - no filter will be applied.");
            } else {
                System.out.println(districts.size() + " districts loaded to include.");
            }
        } catch (NoSuchFileException e) {
            // If file doesn't exist, assume no filter.
            System.out.println("File not found - no filter will be applied.");
        } catch (IOException e) {
            throw new RuntimeException("Error reading configuration from " + filename, e);
        }

        return Collections.unmodifiableSet(districts);
    }
}
