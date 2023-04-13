import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws IOException {
        listLine();
    }

    private static final String VERSION = "1.1";
    private static final String RELEASE_DATE = "13/04/2023";
    private static final String IGNORE_KEYWORD_SIGN = "#";
    private static final String CONFIG_SECTION_INPUT_DIRECTORY = "<<InputDirectory>>";
    private static final String CONFIG_SECTION_OUTPUT_DIRECTORY = "<<OutputDirectory>>";
    private static final String CONFIG_SECTION_IGNORED_INPUT_FILE_CONTAIN_WITH = "<<IgnoredInputFileContainWith>>";
    private static final String CONFIG_SECTION_KEYWORDS = "<<Keywords>>";
    private static final String CONFIG_SECTION_IGNORED_KEYWORDS = "<<IgnoredKeywords>>";
    private static final String CONFIG_SECTION_GROUPED_BY_THREAD_KEYWORDS = "<<GroupedByThreadKeywords>>";

    public static void listLine() throws IOException {

        System.out.println("=========================================================");
        System.out.println("ListLine v" + VERSION + ", ReleaseDate: " + RELEASE_DATE);
        System.out.println("=========================================================");

        String propertiesPathConfig = System.getProperty("properties.ini");
        File properties = new File(propertiesPathConfig == null ? "properties.ini" : propertiesPathConfig);
        if (!properties.exists()) {
            System.out.printf("properties.ini or defined config (%s) is required but not found %n", propertiesPathConfig);
            System.exit(-1);
        }



        String inputDirectory = null;
        String outputDirectory = null;
        ArrayList<String> keywords = new ArrayList<>();
        ArrayList<String> ignoredInputFileContainWith = new ArrayList<>();
        ArrayList<String> ignoredKeywords = new ArrayList<>();
        ArrayList<String> groupedByThreadKeywords = new ArrayList<>();

        String currentConfigState = "";
        List<String> propertyLines = Files.readAllLines(properties.toPath());
        for (String line : propertyLines) {
            if (line.isBlank()) {
                continue;
            }
            if (List.of(CONFIG_SECTION_INPUT_DIRECTORY, CONFIG_SECTION_OUTPUT_DIRECTORY, CONFIG_SECTION_IGNORED_INPUT_FILE_CONTAIN_WITH, CONFIG_SECTION_KEYWORDS, CONFIG_SECTION_IGNORED_KEYWORDS, CONFIG_SECTION_GROUPED_BY_THREAD_KEYWORDS).contains(line.trim())) {
                currentConfigState = line.trim();
            } else {
                switch (currentConfigState) {
                    case CONFIG_SECTION_INPUT_DIRECTORY:
                        inputDirectory = line;
                        System.out.println("Input Directory: " + inputDirectory);
                        break;
                    case CONFIG_SECTION_OUTPUT_DIRECTORY:
                        outputDirectory = line;
                        System.out.println("Output Directory: " + outputDirectory);
                        break;
                    case CONFIG_SECTION_IGNORED_INPUT_FILE_CONTAIN_WITH:
                        ignoredInputFileContainWith.add(line);
                        System.out.println("Ignored Input File Contain With Keyword: " + line);
                        break;
                    case CONFIG_SECTION_KEYWORDS:
                        keywords.add(line);
                        System.out.println("Interested Keyword: " + line);
                        break;
                    case CONFIG_SECTION_IGNORED_KEYWORDS:
                        ignoredKeywords.add(line);
                        System.out.println("Ignored Keyword: " + line);
                        break;
                    case CONFIG_SECTION_GROUPED_BY_THREAD_KEYWORDS:
                        groupedByThreadKeywords.add(line);
                        System.out.println("Grouped Interested Keyword: " + line);
                        break;
                }
            }
        }

        Function<String, String> nameExtractor = (in) -> {
            return (in.replace("/", "_").replace("[", "").replace("]", "").replace(":", "").replace(".", "_").replace("=", "_").trim() + ".log");
        };

        for (String keyword : keywords) {
            File dir = new File(inputDirectory);
            String fileName = nameExtractor.apply(keyword);
            File out = new File(outputDirectory + fileName);
            if (!out.exists() && out.createNewFile()) {
                System.out.println("File out created!");
            }
            try (FileWriter fileWriter = new FileWriter(out)) {
                System.out.println("Extracting Lines... [" + keyword + "]");
                AtomicInteger containCount = new AtomicInteger(0);
                for (File file : Arrays.asList(dir.listFiles()).stream().sorted(
//                        Comparator.comparingInt(f -> { String[] splitted = ((File) f).getName().split("\\."); return splitted.length == 3 ? Integer.parseInt(splitted[1]) : Integer.MAX_VALUE; })
                        Comparator.comparingLong(File::lastModified)
                ).collect(Collectors.toList())) {
                    if (!file.isDirectory()) {

                        if (ignoredInputFileContainWith.stream().anyMatch(it -> file.getName().contains(it))) {
                            /*System.out.println("Ignore and skip file [" + file.getName() + "]");*/
                            continue;
                        }

                            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                        fileWriter.write(file.getName() + "\n");
                        lines.forEach(it -> {
                            for (String ignore : ignoredKeywords) {
                                if (it.contains(ignore)) {
                                    return;
                                }
                            }
                            if (keyword.startsWith(IGNORE_KEYWORD_SIGN)) {
                                System.out.println("Ignore [" + keyword + "] by sign");
                            }
                            if (it.contains(keyword)) {
                                try {
                                    containCount.incrementAndGet();
                                    fileWriter.write(it + "\n");
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
                    }
                }
                System.out.println("Extracting Lines Finished! [" + keyword + "][" + containCount.get() + " Matched]");
            }
        }

        if (!groupedByThreadKeywords.isEmpty()) {
            String groupedDir = "grouped";
            File outputDir = new File(outputDirectory, groupedDir);
            if (!outputDir.exists() && outputDir.mkdir()) {
                System.out.println(outputDir.getAbsolutePath() + " is created!");
            }
            for (String groupedByThreadKeyword : groupedByThreadKeywords) {
                System.out.println("Grouping Keyword... " + groupedByThreadKeyword);
                String groupedFileName = nameExtractor.apply(groupedByThreadKeyword);
                File readKeywwordFile = new File(outputDirectory, groupedFileName);
                if (!readKeywwordFile.exists()) {
                    System.out.println("Skip read " + readKeywwordFile.getAbsolutePath() + " due to not exists, Please check grouped keyword is an input keyword");
                }
                List<String> lines = Files.readAllLines(readKeywwordFile.toPath());
                Map<String, List<String>> groupedKeywordMap = lines.stream()
                        .filter(it -> it.contains("[") && it.contains("]"))
                        .collect(Collectors.groupingBy(it -> it.substring(it.indexOf("[") + 1, it.indexOf("]"))));
                File groupedFileToWrite = new File(outputDir, groupedFileName);
                try (FileWriter writer = new FileWriter(groupedFileToWrite)) {
                    for (Map.Entry<String, List<String>> entry : groupedKeywordMap.entrySet()) {
                        String threadName = entry.getKey();
                        List<String> lineList = entry.getValue();
                        writer.write("----------------------------------------\n");
                        writer.write(threadName + "\n");
                        for (String line : lineList) {
                            writer.write(line + "\n");
                        }
                        writer.write("----------------------------------------\n");
                    }
                }
                System.out.println("Grouping Keyword Finished! " + groupedByThreadKeyword);
            }
        }

    }

}